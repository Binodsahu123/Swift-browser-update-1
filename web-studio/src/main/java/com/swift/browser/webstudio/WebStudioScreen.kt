package com.swift.browser.webstudio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swift.browser.webstudio.editor.CodeEditorEngine
import com.swift.browser.webstudio.preview.PreviewEngine
import com.swift.browser.webstudio.console.DeveloperConsole
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebStudioScreen(
    viewModel: WebStudioViewModel = viewModel(),
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var selectedFileForAction by remember { mutableStateOf<File?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.importZipProject(context, uri)
            scope.launch { drawerState.open() }
        }
    }

    // Handle back button for closing drawer
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0F172A),
                modifier = Modifier.width(280.dp)
            ) {
                FileExplorerDrawer(
                    currentDir = uiState.currentDir,
                    fileList = uiState.fileList,
                    onFileClick = { file ->
                        viewModel.openFile(file)
                        if (!file.isDirectory) {
                            scope.launch { drawerState.close() }
                        }
                    },
                    onUpClick = { uiState.currentDir?.parentFile?.let { viewModel.setCurrentDirectory(it) } },
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    onImportZip = { zipLauncher.launch("application/zip") },
                    onNewFile = { showNewFileDialog = true },
                    onNewFolder = { showNewFolderDialog = true },
                    onNewProject = { showNewProjectDialog = true },
                    onFileAction = { file ->
                        selectedFileForAction = file
                    }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = remember { SnackbarHostState() }.apply {
                    LaunchedEffect(errorMessage) {
                        errorMessage?.let {
                            showSnackbar(it)
                            viewModel.clearError()
                        }
                    }
                })
            },
            topBar = {
                TopAppBar(
                    title = {
                        val activeTab = uiState.openTabs.find { it.id == uiState.activeTabId }
                        Text(
                            text = activeTab?.name ?: uiState.currentDir?.name ?: "Web Studio",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A),
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.exportProject(context) }) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Export Project")
                        }
                        IconButton(onClick = { viewModel.saveActiveTab() }) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                        IconButton(onClick = { viewModel.togglePreview() }) {
                            Icon(if (uiState.showPreview) Icons.Default.Code else Icons.Default.PlayArrow, contentDescription = "Preview")
                        }
                        IconButton(onClick = { viewModel.toggleDevTools() }) {
                            Icon(Icons.Default.DeveloperMode, contentDescription = "Developer Console")
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Web Studio")
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFF1E293B))) {
                // Tabs row
                if (uiState.openTabs.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = uiState.openTabs.indexOfFirst { it.id == uiState.activeTabId }.coerceAtLeast(0),
                        edgePadding = 0.dp,
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color.White,
                        indicator = { tabPositions ->
                            val index = uiState.openTabs.indexOfFirst { it.id == uiState.activeTabId }.coerceAtLeast(0)
                            if (index in tabPositions.indices) {
                                TabRowDefaults.Indicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    ) {
                        uiState.openTabs.forEach { tab ->
                            Tab(
                                selected = tab.id == uiState.activeTabId,
                                onClick = { tab.file?.let { viewModel.openFile(it) } },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(tab.name, fontSize = 14.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close",
                                            modifier = Modifier.size(16.dp).clickable { viewModel.closeTab(tab.id) }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                val activeTab = uiState.openTabs.find { it.id == uiState.activeTabId }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (activeTab != null) {
                        key(activeTab.id) {
                            CodeEditorEngine(
                                content = activeTab.content,
                                language = when {
                                    activeTab.name.endsWith(".js") -> "javascript"
                                    activeTab.name.endsWith(".html") -> "html"
                                    activeTab.name.endsWith(".css") -> "css"
                                    activeTab.name.endsWith(".json") -> "json"
                                    activeTab.name.endsWith(".xml") -> "xml"
                                    activeTab.name.endsWith(".md") -> "markdown"
                                    else -> "plaintext"
                                },
                                theme = uiState.theme,
                                onContentChanged = { viewModel.updateActiveTabContent(it) }
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                                Spacer(Modifier.height(16.dp))
                                Text("Open a file or project from the explorer to start editing", color = Color.Gray)
                                Spacer(Modifier.height(16.dp))
                                Row {
                                    Button(
                                        onClick = { showNewProjectDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                    ) {
                                        Text("New Project")
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    OutlinedButton(
                                        onClick = { scope.launch { drawerState.open() } }
                                    ) {
                                        Text("Open Explorer", color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.showPreview) {
                        val indexHtml = uiState.currentDir?.listFiles()?.find { it.name == "index.html" }
                        val url = if (indexHtml != null) "file://${indexHtml.absolutePath}" else ""
                        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                            val previewHtml = viewModel.getPreviewHtml(activeTab)
                            PreviewEngine(url = url, htmlContent = previewHtml)
                        }
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF10B981))
                                Spacer(Modifier.height(8.dp))
                                Text(loadingMessage, color = Color.White)
                            }
                        }
                    }
                    if (uiState.showDevTools) {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.BottomCenter) {
                            DeveloperConsole(htmlContent = activeTab?.content ?: "", onClose = { viewModel.toggleDevTools() })
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showNewFileDialog && uiState.currentDir != null) {
        var fileName by remember { mutableStateOf("index.html") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New File") },
            text = {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (fileName.isNotBlank()) {
                        viewModel.createFile(uiState.currentDir!!, fileName.trim())
                        showNewFileDialog = false
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNewFolderDialog && uiState.currentDir != null) {
        var folderName by remember { mutableStateOf("src") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        viewModel.createFolder(uiState.currentDir!!, folderName.trim())
                        showNewFolderDialog = false
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNewProjectDialog) {
        var projectName by remember { mutableStateOf("MyWebApp") }
        var selectedTemplate by remember { mutableStateOf("HTML5") }
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("Create New Project") },
            text = {
                Column {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Project Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Select Template:", fontWeight = FontWeight.Bold)
                    listOf("HTML5", "JavaScript", "CSS Playground", "Markdown Note").forEach { tmpl ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTemplate = tmpl }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedTemplate == tmpl,
                                onClick = { selectedTemplate = tmpl }
                            )
                            Text(tmpl, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (projectName.isNotBlank()) {
                        viewModel.createProject(projectName.trim(), selectedTemplate)
                        showNewProjectDialog = false
                    }
                }) {
                    Text("Create Project")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Action dialog for selected file/folder
    selectedFileForAction?.let { file ->
        var showMenu by remember { mutableStateOf(true) }
        if (showMenu) {
            AlertDialog(
                onDismissRequest = { selectedFileForAction = null },
                title = { Text(file.name) },
                text = {
                    Column {
                        ListItem(
                            headlineContent = { Text("Rename") },
                            leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                            modifier = Modifier.clickable {
                                showMenu = false
                                showRenameDialog = true
                            }
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Delete", color = Color.Red) },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            modifier = Modifier.clickable {
                                viewModel.deleteFile(file)
                                selectedFileForAction = null
                            }
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedFileForAction = null }) { Text("Close") }
                }
            )
        }

        if (showRenameDialog) {
            var newName by remember { mutableStateOf(file.name) }
            AlertDialog(
                onDismissRequest = {
                    showRenameDialog = false
                    selectedFileForAction = null
                },
                title = { Text("Rename ${file.name}") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newName.isNotBlank() && newName != file.name) {
                            viewModel.renameFile(file, newName.trim())
                        }
                        showRenameDialog = false
                        selectedFileForAction = null
                    }) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showRenameDialog = false
                        selectedFileForAction = null
                    }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun FileExplorerDrawer(
    currentDir: File?,
    fileList: List<File>,
    onFileClick: (File) -> Unit,
    onUpClick: () -> Unit,
    onCloseDrawer: () -> Unit,
    onImportZip: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onNewProject: () -> Unit,
    onFileAction: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("WEB STUDIO FILES", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onCloseDrawer, modifier = Modifier.size(24.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
            }
        }

        HorizontalDivider(color = Color(0xFF334155))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onNewProject) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddCircle, contentDescription = "New Project", tint = Color(0xFF10B981))
                    Text("Project", color = Color(0xFF10B981), fontSize = 10.sp)
                }
            }
            IconButton(onClick = onImportZip) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderZip, contentDescription = "Import ZIP", tint = Color(0xFF38BDF8))
                    Text("Zip", color = Color(0xFF38BDF8), fontSize = 10.sp)
                }
            }
            IconButton(onClick = onNewFile, enabled = currentDir != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddBox, contentDescription = "New File", tint = if (currentDir != null) Color.White else Color.Gray)
                    Text("File", color = if (currentDir != null) Color.White else Color.Gray, fontSize = 10.sp)
                }
            }
            IconButton(onClick = onNewFolder, enabled = currentDir != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = if (currentDir != null) Color.White else Color.Gray)
                    Text("Folder", color = if (currentDir != null) Color.White else Color.Gray, fontSize = 10.sp)
                }
            }
        }

        HorizontalDivider(color = Color(0xFF334155))

        if (currentDir == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No Project Open", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onNewProject,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Create Project")
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (currentDir.parentFile != null && currentDir.parentFile?.name?.contains("webstudio") == true) {
                    item {
                        ListItem(
                            headlineContent = { Text("..", color = Color.White) },
                            leadingContent = { Icon(Icons.Default.Folder, null, tint = Color(0xFF818CF8)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onUpClick() }
                        )
                    }
                }
                items(fileList) { file ->
                    ListItem(
                        headlineContent = { Text(file.name, maxLines = 1, color = Color.White) },
                        leadingContent = {
                            if (file.isDirectory) Icon(Icons.Default.Folder, null, tint = Color(0xFF818CF8))
                            else Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color(0xFF94A3B8))
                        },
                        trailingContent = {
                            IconButton(onClick = { onFileAction(file) }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Gray)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onFileClick(file) }
                    )
                }
            }
        }
    }
}
