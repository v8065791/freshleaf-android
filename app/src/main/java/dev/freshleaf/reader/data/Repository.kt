package dev.freshleaf.reader.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class SyncState(val running: Boolean = false, val message: String = "", val error: String? = null)
data class FolderSources(val feedIds: List<String> = emptyList(), val categoryIds: List<String> = emptyList())
data class FeedOrganization(val localTagIds: List<Long> = emptyList(), val folderIds: List<Long> = emptyList())

class FreshLeafRepository(
    private val database: AppDatabase,
    private val credentials: SecureCredentials,
    private val api: FreshRssApi = FreshRssApi(),
) {
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    val feeds = database.feeds().observeAll()
    val categories = database.categories().observeAll()
    val tags = database.tags().observeAll()
    val localFeedTags = database.localFeedTags().observeAll()
    val folders = database.folders().observeAll()

    fun articles(filter: String, feedId: String? = null, categoryId: String? = null, tagId: String? = null, localFeedTagId: Long? = null): Flow<List<ArticleEntity>> =
        database.articles().observe(filter, feedId, categoryId, tagId, localFeedTagId)
    fun article(id: String): Flow<ArticleEntity?> = database.articles().observeOne(id)

    fun folderSources(folderId: Long): Flow<FolderSources> = combine(
        database.folders().observeFeedIds(folderId),
        database.folders().observeCategoryIds(folderId),
    ) { feedIds, categoryIds -> FolderSources(feedIds, categoryIds) }

    fun feedOrganization(feedId: String): Flow<FeedOrganization> = combine(
        database.localFeedTags().observeTagIdsForFeed(feedId),
        database.folders().observeFolderIdsForFeed(feedId),
    ) { localTagIds, folderIds -> FeedOrganization(localTagIds, folderIds) }

    fun folderArticles(filter: String, folderIds: List<Long>): Flow<List<ArticleEntity>> = combine(
        database.articles().observeAllCached(),
        database.feeds().observeAll(),
        database.folders().observeFeedIds(folderIds),
        database.folders().observeCategoryIds(folderIds),
    ) { articles, feeds, folderFeedIds, folderCategoryIds ->
        val feedCategories = feeds.associate { it.id to it.categoryIds }
        articles.filter { article ->
            (filter == "all" || filter == "unread" && !article.isRead || filter == "starred" && article.isStarred) &&
                (article.feedId in folderFeedIds || folderCategoryIds.any { category -> containsRemoteLabel(feedCategories[article.feedId].orEmpty(), category) })
        }
    }

    fun settings(): AccountSettings? = credentials.load()

    suspend fun configure(endpoint: String, username: String, password: String) {
        api.login(endpoint, username, password)
        credentials.save(endpoint, username, password)
    }

    suspend fun sync() {
        _syncState.value = SyncState(running = true, message = "Syncing FreshRSS…")
        try {
            val account = credentials.load() ?: throw FreshRssException("Configure a FreshRSS account first")
            api.login(account.endpoint, account.username, account.password)
            val snapshot = api.sync()
            database.feeds().upsertAll(snapshot.feeds)
            database.categories().upsertAll(snapshot.categories)
            database.tags().upsertAll(snapshot.tags)
            database.articles().upsertAll(snapshot.articles)
            _syncState.value = SyncState(message = "Synced ${snapshot.articles.size} articles")
        } catch (e: Exception) {
            _syncState.value = SyncState(error = e.message ?: "Sync failed")
        }
    }

    suspend fun setRead(article: ArticleEntity) {
        markRead(article, !article.isRead)
    }

    suspend fun markRead(article: ArticleEntity, read: Boolean) {
        api.loginFromStored(credentials)
        api.markRead(article.id, read)
        database.articles().setRead(article.id, read)
    }

    suspend fun setStarred(article: ArticleEntity) {
        api.markStarred(article.id, !article.isStarred)
        database.articles().setStarred(article.id, !article.isStarred)
    }

    suspend fun createFolder(name: String, parentId: Long? = null) {
        database.folders().insert(LocalFolderEntity(name = name.trim(), parentId = parentId))
    }

    suspend fun addFeedToFolder(folderId: Long, feedId: String) {
        database.folders().addFeed(FolderFeedCrossRef(folderId, feedId))
    }

    suspend fun addCategoryToFolder(folderId: Long, categoryId: String) {
        database.folders().addCategory(FolderCategoryCrossRef(folderId, categoryId))
    }

    suspend fun replaceFolderSources(folderId: Long, feedIds: List<String>, categoryIds: List<String>) {
        database.folders().replaceSources(folderId, feedIds, categoryIds)
    }

    suspend fun saveFeedOrganization(
        feed: FeedEntity,
        title: String,
        categoryIds: List<String>,
        localTagIds: List<Long>,
        folderIds: List<Long>,
    ) {
        val currentCategories = remoteIds(feed.categoryIds)
        val selectedCategories = categoryIds.toSet()
        val titleChanged = title.trim() != feed.title
        if (currentCategories != selectedCategories || titleChanged) {
            api.loginFromStored(credentials)
            if (titleChanged) api.updateSubscriptionTitle(feed.id, title)
            api.updateSubscriptionCategories(feed.id, currentCategories, selectedCategories)
            // Keep the local selection accurate even if a subsequent full sync is unavailable.
            database.feeds().upsertAll(listOf(feed.copy(title = title.trim(), categoryIds = selectedCategories.sorted().joinToString(REMOTE_ID_SEPARATOR))))
        }
        database.localFeedTags().replaceFeedTags(feed.id, localTagIds)
        database.folders().replaceFoldersForFeed(feed.id, folderIds)
        if (currentCategories != selectedCategories || titleChanged) sync()
    }

    suspend fun createCategoryForFeed(feed: FeedEntity, label: String) {
        api.loginFromStored(credentials)
        val categoryId = api.createCategoryAndAssign(feed.id, label)
        val updatedIds = remoteIds(feed.categoryIds) + categoryId
        database.feeds().upsertAll(listOf(feed.copy(categoryIds = updatedIds.sorted().joinToString(REMOTE_ID_SEPARATOR))))
        sync()
    }

    suspend fun createLocalFeedTag(name: String) {
        val cleaned = name.trim()
        require(cleaned.isNotBlank()) { "Label name cannot be blank" }
        database.localFeedTags().insert(LocalFeedTagEntity(name = cleaned))
    }

    suspend fun renameLocalFeedTag(id: Long, name: String) {
        val cleaned = name.trim()
        require(cleaned.isNotBlank()) { "Label name cannot be blank" }
        database.localFeedTags().rename(id, cleaned)
    }

    suspend fun deleteLocalFeedTag(id: Long) {
        database.localFeedTags().delete(id)
    }

    suspend fun subscribe(url: String, title: String, categoryId: String?) {
        api.loginFromStored(credentials)
        api.subscribe(url, title, categoryId)
        sync()
    }

    suspend fun createRemoteTag(label: String) {
        api.loginFromStored(credentials)
        api.createTag(label)
        sync()
    }

    suspend fun unsubscribe(feed: FeedEntity) {
        api.loginFromStored(credentials)
        api.unsubscribe(feed.id)
        database.feeds().delete(feed.id)
        database.articles().deleteForFeed(feed.id)
    }

    suspend fun clearAccount() = withContext(Dispatchers.IO) {
        credentials.clear()
        database.clearAllTables()
    }

    private suspend fun FreshRssApi.loginFromStored(store: SecureCredentials) {
        val account = store.load() ?: throw FreshRssException("Configure a FreshRSS account first")
        login(account.endpoint, account.username, account.password)
    }

    private fun remoteIds(value: String): Set<String> = value.split(REMOTE_ID_SEPARATOR).filter { it.isNotBlank() }.toSet()
}

const val REMOTE_ID_SEPARATOR = "\u001f"
