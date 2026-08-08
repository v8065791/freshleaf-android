package dev.freshleaf.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.freshleaf.reader.data.ArticleEntity
import dev.freshleaf.reader.data.CategoryEntity
import dev.freshleaf.reader.data.FeedEntity
import dev.freshleaf.reader.data.LocalFolderEntity
import dev.freshleaf.reader.data.TagEntity
import dev.freshleaf.reader.data.TAILSCALE_PACKAGE
import dev.freshleaf.reader.data.isTailscaleEndpoint
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun FreshLeafScreen(viewModel: FreshLeafViewModel) {
    val configured by viewModel.configured.collectAsState()
    if (!configured) SetupScreen(viewModel) else ReaderScreen(viewModel)
}

@Composable
private fun SetupScreen(viewModel: FreshLeafViewModel) {
    val context = LocalContext.current
    var endpoint by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val sync by viewModel.syncState.collectAsState()
    val error by viewModel.operationError.collectAsState()
    val tailscaleIntent = remember(context) {
        context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("FreshLeaf", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Offline reading for your FreshRSS account", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("FreshRSS URL") }, placeholder = { Text("https://rss.example.net") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(password, { password = it }, label = { Text("API password") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (isTailscaleEndpoint(endpoint)) {
            TailscaleSetupNotice(tailscaleIntent != null) {
                tailscaleIntent?.let(context::startActivity)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.configure(endpoint, username, password) },
            enabled = endpoint.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect and sync") }
        if (sync.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Text("Use the dedicated API password configured in FreshRSS, not your normal login password.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun TailscaleSetupNotice(tailscaleInstalled: Boolean, openTailscale: () -> Unit) {
    Text(
        "This .ts.net server uses Tailscale. Connect Tailscale first, and make sure FreshLeaf is not excluded by app-based split tunneling.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 12.dp),
    )
    if (tailscaleInstalled) {
        TextButton(onClick = openTailscale) { Text("Open Tailscale") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(viewModel: FreshLeafViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val articles by viewModel.articles.collectAsState()
    val feeds by viewModel.feeds.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val filter by viewModel.selectedFilter.collectAsState()
    val selectedArticle by viewModel.selectedArticle.collectAsState()
    val sync by viewModel.syncState.collectAsState()
    var showFolderDialog by remember { mutableStateOf(false) }
    var showFeedDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    if (showSettings) {
        SettingsScreen(viewModel, onBack = { showSettings = false })
        return
    }

    selectedArticle?.let { article ->
        ArticleDetail(article, viewModel)
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("FreshLeaf", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
                DrawerAction("All articles", Icons.Default.Inbox, filter == "all") { viewModel.chooseFilter("all"); scope.launch { drawerState.close() } }
                DrawerAction("Unread", Icons.Default.Check, filter == "unread") { viewModel.chooseFilter("unread"); scope.launch { drawerState.close() } }
                DrawerAction("Starred", Icons.Default.Star, filter == "starred") { viewModel.chooseFilter("starred"); scope.launch { drawerState.close() } }
                Divider(Modifier.padding(vertical = 8.dp))
                Text("Categories", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                categories.forEach { category -> DrawerAction(category.title, Icons.Default.Folder, false) { viewModel.chooseCategory(category.id); scope.launch { drawerState.close() } } }
                Text("Tags", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                tags.forEach { tag -> DrawerAction(tag.title, Icons.Default.Label, false) { viewModel.chooseTag(tag.id); scope.launch { drawerState.close() } } }
                Text("Local folders", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FolderDrawerTree(folders) { folder -> viewModel.chooseFolder(folder.id); scope.launch { drawerState.close() } }
                Divider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { showFolderDialog = true }) { Icon(Icons.Default.CreateNewFolder, null); Text("New local folder", Modifier.padding(start = 8.dp)) }
                TextButton(onClick = { showFeedDialog = true }) { Icon(Icons.Default.Add, null); Text("Subscribe to feed", Modifier.padding(start = 8.dp)) }
                TextButton(onClick = { showTagDialog = true }) { Icon(Icons.Default.Label, null); Text("New FreshRSS tag", Modifier.padding(start = 8.dp)) }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (filter == "all") "Articles" else filter.replaceFirstChar { it.uppercase() }) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu") } },
                    actions = {
                        IconButton(onClick = viewModel::sync) { Icon(Icons.Default.Refresh, "Sync") }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHost) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (sync.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                sync.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                if (articles.isEmpty()) EmptyState(sync.message.ifBlank { "No articles cached yet." })
                else LazyColumn(Modifier.fillMaxSize()) { items(articles, key = { it.id }) { ArticleCard(it, feeds, viewModel) { message -> scope.launch { snackbarHost.showSnackbar(message) } } } }
            }
        }
    }

    if (showFolderDialog) NameDialog("New local folder", "Folder name", { showFolderDialog = false }, { viewModel.createFolder(it); showFolderDialog = false })
    if (showTagDialog) NameDialog("New FreshRSS tag", "Tag name", { showTagDialog = false }, { viewModel.createTag(it); showTagDialog = false })
    if (showFeedDialog) FeedDialog({ showFeedDialog = false }, { url, title -> viewModel.addFeed(url, title, null); showFeedDialog = false })
}

@Composable
private fun DrawerAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.NavigationDrawerItem(
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null) },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun FolderDrawerTree(folders: List<LocalFolderEntity>, onOpen: (LocalFolderEntity) -> Unit) {
    @Composable
    fun draw(parentId: Long?, depth: Int) {
        folders.filter { it.parentId == parentId }.forEach { folder ->
            DrawerAction("${"  ".repeat(depth)}${folder.name}", if (folder.parentId == null) Icons.Default.Folder else Icons.Default.FolderOpen, false) { onOpen(folder) }
            draw(folder.id, depth + 1)
        }
    }
    draw(null, 0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(viewModel: FreshLeafViewModel, onBack: () -> Unit) {
    val account by viewModel.account.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val feeds by viewModel.feeds.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var accountEditor by remember { mutableStateOf(false) }
    var resetConfirmation by remember { mutableStateOf(false) }
    var parentForNewFolder by remember { mutableStateOf<LocalFolderEntity?>(null) }
    var sourceFolder by remember { mutableStateOf<LocalFolderEntity?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item {
                Text("Account", style = MaterialTheme.typography.titleMedium)
                account?.let {
                    Text(it.endpoint, style = MaterialTheme.typography.bodyMedium)
                    Text(it.username, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { accountEditor = true }) { Text("Edit account") }
                    TextButton(onClick = { resetConfirmation = true }) { Text("Reset account") }
                }
                Divider(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Local folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { parentForNewFolder = LocalFolderEntity(name = "") }) { Text("New folder") }
                }
            }
            fun draw(parentId: Long?, depth: Int) {
                folders.filter { it.parentId == parentId }.forEach { folder ->
                    item(key = folder.id) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = (depth * 20).dp)) {
                            Icon(if (folder.parentId == null) Icons.Default.Folder else Icons.Default.FolderOpen, null)
                            TextButton(onClick = { sourceFolder = folder }, modifier = Modifier.weight(1f)) { Text(folder.name) }
                            TextButton(onClick = { parentForNewFolder = folder }) { Text("Add subfolder") }
                        }
                    }
                    draw(folder.id, depth + 1)
                }
            }
            draw(null, 0)
        }
    }

    if (accountEditor && account != null) AccountDialog(account!!, { accountEditor = false }) { endpoint, username, password ->
        viewModel.configure(endpoint, username, password)
        accountEditor = false
    }
    if (parentForNewFolder != null) NameDialog(
        if (parentForNewFolder!!.id == 0L) "New local folder" else "New subfolder in ${parentForNewFolder!!.name}",
        "Folder name",
        { parentForNewFolder = null },
    ) { name ->
        viewModel.createFolder(name, parentForNewFolder!!.id.takeIf { it != 0L })
        parentForNewFolder = null
    }
    sourceFolder?.let { folder -> FolderSourcesDialog(folder, feeds, categories, viewModel, { sourceFolder = null }) }
    if (resetConfirmation) AlertDialog(
        onDismissRequest = { resetConfirmation = false },
        title = { Text("Reset account?") },
        text = { Text("This removes your credentials, cached FreshRSS data, and local folders from this device.") },
        confirmButton = { TextButton(onClick = { viewModel.resetAccount(); resetConfirmation = false; onBack() }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { resetConfirmation = false }) { Text("Cancel") } },
    )
}

@Composable
private fun AccountDialog(account: dev.freshleaf.reader.data.AccountSettings, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var endpoint by remember { mutableStateOf(account.endpoint) }
    var username by remember { mutableStateOf(account.username) }
    var password by remember { mutableStateOf(account.password) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit account") }, text = {
        Column {
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("FreshRSS URL") }, singleLine = true)
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("API password") }, singleLine = true)
        }
    }, confirmButton = { TextButton(enabled = endpoint.isNotBlank() && username.isNotBlank() && password.isNotBlank(), onClick = { onSave(endpoint, username, password) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun FolderSourcesDialog(folder: LocalFolderEntity, feeds: List<FeedEntity>, categories: List<CategoryEntity>, viewModel: FreshLeafViewModel, onDismiss: () -> Unit) {
    val sources by viewModel.folderSources(folder.id).collectAsState(initial = dev.freshleaf.reader.data.FolderSources())
    var feedIds by remember { mutableStateOf(emptySet<String>()) }
    var categoryIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(sources) { feedIds = sources.feedIds.toSet(); categoryIds = sources.categoryIds.toSet() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(folder.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Feeds", style = MaterialTheme.typography.labelLarge)
            feeds.forEach { feed -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(feed.id in feedIds, { checked -> feedIds = if (checked) feedIds + feed.id else feedIds - feed.id }); Text(feed.title) } }
            Text("Categories", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            categories.forEach { category -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(category.id in categoryIds, { checked -> categoryIds = if (checked) categoryIds + category.id else categoryIds - category.id }); Text(category.title) } }
        }
    }, confirmButton = { TextButton(onClick = { viewModel.replaceFolderSources(folder.id, dev.freshleaf.reader.data.FolderSources(feedIds.toList(), categoryIds.toList())); onDismiss() }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleCard(article: ArticleEntity, feeds: List<FeedEntity>, viewModel: FreshLeafViewModel, onMessage: (String) -> Unit) {
    val feedName = feeds.firstOrNull { it.id == article.feedId }?.title ?: "FreshRSS"
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> { viewModel.toggleRead(article); onMessage(if (article.isRead) "Marked unread" else "Marked read") }
            SwipeToDismissBoxValue.EndToStart -> { viewModel.toggleStar(article); onMessage(if (article.isStarred) "Removed star" else "Starred") }
            SwipeToDismissBoxValue.Settled -> Unit
        }
        false
    })
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.CenterStart) { Text("Mark read / unread") } },
        content = {
    Card(onClick = { viewModel.openArticle(article) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(feedName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.toggleStar(article) }, modifier = Modifier.size(36.dp)) { Icon(if (article.isStarred) Icons.Default.Star else Icons.Default.StarBorder, "Star") }
                IconButton(onClick = { viewModel.toggleRead(article) }, modifier = Modifier.size(36.dp)) { Icon(if (article.isRead) Icons.Default.Check else Icons.Default.Inbox, "Read") }
            }
            Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stripHtml(article.html), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(formatDate(article.publishedAt), style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        }
    }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleDetail(article: ArticleEntity, viewModel: FreshLeafViewModel) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(article.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = viewModel::closeArticle) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { viewModel.toggleStar(article) }) { Icon(if (article.isStarred) Icons.Default.Star else Icons.Default.StarBorder, "Star") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${article.author.ifBlank { "Unknown author" }} · ${formatDate(article.publishedAt)}", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            Divider(Modifier.padding(vertical = 16.dp))
            Text(stripHtml(article.html), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = { viewModel.toggleRead(article) }) { Text(if (article.isRead) "Mark unread" else "Mark read") }
        }
    }
}

@Composable
private fun EmptyState(message: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message, modifier = Modifier.padding(24.dp)) } }

@Composable
private fun NameDialog(title: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) }, confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onSave(value) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun FeedDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Subscribe to feed") }, text = { Column { OutlinedTextField(url, { url = it }, label = { Text("Feed URL") }, singleLine = true); OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true) } }, confirmButton = { TextButton(enabled = url.isNotBlank(), onClick = { onSave(url, title.ifBlank { url }) }) { Text("Subscribe") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun stripHtml(value: String): String = value.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()
private fun formatDate(timestamp: Long): String = if (timestamp == 0L) "" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
