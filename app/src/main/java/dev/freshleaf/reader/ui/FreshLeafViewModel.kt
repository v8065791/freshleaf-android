package dev.freshleaf.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.freshleaf.reader.data.ArticleEntity
import dev.freshleaf.reader.data.FreshLeafRepository
import dev.freshleaf.reader.data.FolderSources
import dev.freshleaf.reader.data.FeedEntity
import dev.freshleaf.reader.data.LocalFolderEntity
import dev.freshleaf.reader.data.ReaderPreferences
import dev.freshleaf.reader.data.RowSwipeAction
import dev.freshleaf.reader.data.ThemeMode
import dev.freshleaf.reader.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FreshLeafViewModel(
    private val repository: FreshLeafRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {
    private val filter = MutableStateFlow("all")
    private val feedId = MutableStateFlow<String?>(null)
    private val categoryId = MutableStateFlow<String?>(null)
    private val tagId = MutableStateFlow<String?>(null)
    private val localFeedTagId = MutableStateFlow<Long?>(null)
    private val folderId = MutableStateFlow<Long?>(null)
    private val _configured = MutableStateFlow(repository.settings() != null)
    private val _selectedArticle = MutableStateFlow<ArticleEntity?>(null)
    private val _operationError = MutableStateFlow<String?>(null)

    val configured: StateFlow<Boolean> = _configured
    val selectedArticle: StateFlow<ArticleEntity?> = _selectedArticle
    val operationError: StateFlow<String?> = _operationError
    val syncState = repository.syncState
    private val _account = MutableStateFlow(repository.settings())
    val account: StateFlow<dev.freshleaf.reader.data.AccountSettings?> = _account
    val feeds = repository.feeds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = repository.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val localFeedTags = repository.localFeedTags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders = repository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folderFeedAssignments = repository.folderFeedAssignments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selectedFilter: StateFlow<String> = filter
    val selectedFeedId: StateFlow<String?> = feedId
    val selectedCategoryId: StateFlow<String?> = categoryId
    val selectedTagId: StateFlow<String?> = tagId
    val preferences: StateFlow<ReaderPreferences> = userPreferences.state
    val articles = combine(filter, feedId, categoryId, tagId, localFeedTagId) { currentFilter, currentFeed, currentCategory, currentTag, currentLocalFeedTag ->
        BasicArticleSelection(currentFilter, currentFeed, currentCategory, currentTag, currentLocalFeedTag)
    }.combine(folderId) { selection, currentFolder ->
        selection.copy(folderId = currentFolder)
    }.combine(folders) { selection, allFolders ->
        ArticleSelection(selection.filter, selection.feedId, selection.categoryId, selection.tagId, selection.localFeedTagId, selection.folderId, allFolders)
    }.flatMapLatest { selection ->
        selection.folderId?.let { repository.folderArticles(selection.filter, descendantFolderIds(it, selection.folders)) }
            ?: repository.articles(selection.filter, selection.feedId, selection.categoryId, selection.tagId, selection.localFeedTagId)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (_configured.value) sync()
    }

    fun configure(endpoint: String, username: String, password: String) = launchOperation {
        repository.configure(endpoint, username, password)
        _account.value = repository.settings()
        _configured.value = true
        repository.sync()
    }

    fun sync() = viewModelScope.launch { repository.sync() }

    fun chooseFilter(value: String) {
        feedId.value = null
        categoryId.value = null
        tagId.value = null
        localFeedTagId.value = null
        folderId.value = null
        filter.value = value
        _selectedArticle.value = null
    }

    fun chooseFeed(value: String) {
        feedId.value = value
        categoryId.value = null
        tagId.value = null
        localFeedTagId.value = null
        folderId.value = null
        filter.value = "all"
        _selectedArticle.value = null
    }

    fun chooseCategory(value: String) {
        feedId.value = null
        categoryId.value = value
        tagId.value = null
        localFeedTagId.value = null
        folderId.value = null
        filter.value = "all"
        _selectedArticle.value = null
    }

    fun chooseTag(value: String) {
        feedId.value = null
        categoryId.value = null
        tagId.value = value
        localFeedTagId.value = null
        folderId.value = null
        filter.value = "all"
        _selectedArticle.value = null
    }

    fun chooseLocalFeedTag(value: Long) {
        feedId.value = null
        categoryId.value = null
        tagId.value = null
        localFeedTagId.value = value
        folderId.value = null
        filter.value = "all"
        _selectedArticle.value = null
    }

    fun chooseFolder(value: Long) {
        feedId.value = null
        categoryId.value = null
        tagId.value = null
        localFeedTagId.value = null
        folderId.value = value
        filter.value = "all"
        _selectedArticle.value = null
    }

    fun openArticle(article: ArticleEntity) {
        _selectedArticle.value = article
        if (!article.isRead) {
            viewModelScope.launch {
                try { repository.markRead(article, true) } catch (_: Exception) { /* offline state is retained */ }
            }
        }
    }

    fun closeArticle() { _selectedArticle.value = null }

    fun toggleRead(article: ArticleEntity) = launchOperation { repository.setRead(article) }
    fun toggleStar(article: ArticleEntity) = launchOperation { repository.setStarred(article) }
    fun applySwipe(article: ArticleEntity, action: RowSwipeAction) = when (action) {
        RowSwipeAction.MARK_READ -> if (!article.isRead) launchOperation { repository.markRead(article, true) } else Unit
        RowSwipeAction.TOGGLE_STAR -> toggleStar(article)
        RowSwipeAction.DISABLED -> Unit
    }
    fun createFolder(name: String, parentId: Long? = null) = launchOperation { repository.createFolder(name, parentId) }
    fun folderSources(folderId: Long) = repository.folderSources(folderId)
    fun replaceFolderSources(folderId: Long, sources: FolderSources) = launchOperation { repository.replaceFolderSources(folderId, sources.feedIds, sources.categoryIds) }
    fun feedOrganization(feedId: String) = repository.feedOrganization(feedId)
    fun saveFeedOrganization(feed: FeedEntity, title: String, categoryIds: List<String>, localTagIds: List<Long>, folderIds: List<Long>) = launchOperation {
        repository.saveFeedOrganization(feed, title, categoryIds, localTagIds, folderIds)
    }
    fun createCategoryForFeed(feed: FeedEntity, name: String) = launchOperation { repository.createCategoryForFeed(feed, name) }
    fun createLocalFeedTag(name: String) = launchOperation { repository.createLocalFeedTag(name) }
    fun renameLocalFeedTag(id: Long, name: String) = launchOperation { repository.renameLocalFeedTag(id, name) }
    fun deleteLocalFeedTag(id: Long) = launchOperation { repository.deleteLocalFeedTag(id) }
    fun resetAccount() = launchOperation {
        repository.clearAccount()
        _account.value = null
        _configured.value = false
        chooseFilter("all")
    }
    fun addFeed(url: String, title: String, categoryId: String?) = launchOperation { repository.subscribe(url, title, categoryId) }
    fun createTag(label: String) = launchOperation { repository.createRemoteTag(label) }
    fun unsubscribe(feed: FeedEntity) = launchOperation { repository.unsubscribe(feed) }
    fun setThemeMode(value: ThemeMode) = userPreferences.setThemeMode(value)
    fun setSwipeStart(value: RowSwipeAction) = userPreferences.setSwipeStart(value)
    fun setSwipeEnd(value: RowSwipeAction) = userPreferences.setSwipeEnd(value)

    fun clearError() { _operationError.value = null }

    private fun launchOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (error: Exception) {
                _operationError.value = error.message ?: "Operation failed"
            }
        }
    }
}

private data class ArticleSelection(
    val filter: String,
    val feedId: String?,
    val categoryId: String?,
    val tagId: String?,
    val localFeedTagId: Long?,
    val folderId: Long?,
    val folders: List<LocalFolderEntity>,
)

private data class BasicArticleSelection(
    val filter: String,
    val feedId: String?,
    val categoryId: String?,
    val tagId: String?,
    val localFeedTagId: Long?,
    val folderId: Long? = null,
)

internal fun descendantFolderIds(rootId: Long, folders: List<LocalFolderEntity>): List<Long> {
    val children = folders.groupBy { it.parentId }
    val result = mutableListOf(rootId)
    fun visit(parentId: Long) { children[parentId].orEmpty().forEach { result += it.id; visit(it.id) } }
    visit(rootId)
    return result
}

class FreshLeafViewModelFactory(
    private val repository: FreshLeafRepository,
    private val userPreferences: UserPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = FreshLeafViewModel(repository, userPreferences) as T
}
