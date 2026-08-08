package dev.freshleaf.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.freshleaf.reader.data.ArticleEntity
import dev.freshleaf.reader.data.FreshLeafRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FreshLeafViewModel(private val repository: FreshLeafRepository) : ViewModel() {
    private val filter = MutableStateFlow("all")
    private val feedId = MutableStateFlow<String?>(null)
    private val categoryId = MutableStateFlow<String?>(null)
    private val tagId = MutableStateFlow<String?>(null)
    private val _configured = MutableStateFlow(repository.settings() != null)
    private val _selectedArticle = MutableStateFlow<ArticleEntity?>(null)
    private val _operationError = MutableStateFlow<String?>(null)

    val configured: StateFlow<Boolean> = _configured
    val selectedArticle: StateFlow<ArticleEntity?> = _selectedArticle
    val operationError: StateFlow<String?> = _operationError
    val syncState = repository.syncState
    val feeds = repository.feeds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = repository.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders = repository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selectedFilter: StateFlow<String> = filter
    val selectedFeedId: StateFlow<String?> = feedId
    val articles = combine(filter, feedId, categoryId, tagId) { currentFilter, currentFeed, currentCategory, currentTag ->
        listOf(currentFilter, currentFeed, currentCategory, currentTag)
    }
        .flatMapLatest { values -> repository.articles(values[0] ?: "all", values[1], values[2], values[3]) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (_configured.value) sync()
    }

    fun configure(endpoint: String, username: String, password: String) = launchOperation {
        repository.configure(endpoint, username, password)
        _configured.value = true
        repository.sync()
    }

    fun sync() = viewModelScope.launch { repository.sync() }

    fun chooseFilter(value: String) {
        feedId.value = null
        categoryId.value = null
        tagId.value = null
        filter.value = value
        _selectedArticle.value = null
    }

    fun chooseFeed(value: String) {
        feedId.value = value
        categoryId.value = null
        tagId.value = null
        filter.value = "all"
        _selectedArticle.value = null
    }

    fun chooseCategory(value: String) {
        feedId.value = null
        categoryId.value = value
        tagId.value = null
        filter.value = "all"
        _selectedArticle.value = null
    }

    fun chooseTag(value: String) {
        feedId.value = null
        categoryId.value = null
        tagId.value = value
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
    fun createFolder(name: String, parentId: Long? = null) = launchOperation { repository.createFolder(name, parentId) }
    fun addFeed(url: String, title: String, categoryId: String?) = launchOperation { repository.subscribe(url, title, categoryId) }
    fun createTag(label: String) = launchOperation { repository.createRemoteTag(label) }

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

class FreshLeafViewModelFactory(private val repository: FreshLeafRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = FreshLeafViewModel(repository) as T
}
