package dev.freshleaf.reader.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncState(val running: Boolean = false, val message: String = "", val error: String? = null)

class FreshLeafRepository(
    private val database: AppDatabase,
    private val credentials: SecureCredentials,
) {
    private val api = FreshRssApi()
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    val feeds = database.feeds().observeAll()
    val categories = database.categories().observeAll()
    val tags = database.tags().observeAll()
    val folders = database.folders().observeAll()

    fun articles(filter: String, feedId: String? = null, categoryId: String? = null, tagId: String? = null): Flow<List<ArticleEntity>> = database.articles().observe(filter, feedId, categoryId, tagId)
    fun article(id: String): Flow<ArticleEntity?> = database.articles().observeOne(id)

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

    suspend fun clearAccount() = credentials.clear()

    private suspend fun FreshRssApi.loginFromStored(store: SecureCredentials) {
        val account = store.load() ?: throw FreshRssException("Configure a FreshRSS account first")
        login(account.endpoint, account.username, account.password)
    }
}
