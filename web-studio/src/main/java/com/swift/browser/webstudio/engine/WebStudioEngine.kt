package com.swift.browser.webstudio.engine

import android.content.Context
import android.net.Uri
import com.swift.browser.webstudio.EditorTab
import com.swift.browser.webstudio.WebStudioState
import com.swift.browser.webstudio.api.WebStudioEngineApi
import com.swift.browser.webstudio.manager.ProjectTemplates
import com.swift.browser.webstudio.model.*
import com.swift.browser.webstudio.repository.WebStudioRepository
import kotlinx.coroutines.flow.*
import java.io.File

class WebStudioEngine(private val context: Context) : WebStudioEngineApi {

    val diagnosticsManager = DiagnosticsManager()
    val crashRecoveryManager = CrashRecoveryManager(diagnosticsManager)
    val projectLoader = ProjectLoader(context, diagnosticsManager)
    val markdownRenderer = MarkdownRenderer()
    val tabStateManager = TabStateManager(diagnosticsManager)
    val fileExplorerEngine = FileExplorerEngine(diagnosticsManager)
    val loadingStateManager = LoadingStateManager()
    val errorStateManager = ErrorStateManager()
    private val repository = WebStudioRepository(context)

    private val _engineState = MutableStateFlow(WebStudioState())
    override val studioState: StateFlow<WebStudioState> = _engineState.asStateFlow()

    override val projectFlow: StateFlow<File?> = _engineState
        .map { it.currentDir }
        .stateIn(kotlinx.coroutines.GlobalScope, SharingStarted.Eagerly, null)

    override val workspaceFlow: StateFlow<WorkspaceModel> = _engineState
        .map { it.workspace }
        .stateIn(kotlinx.coroutines.GlobalScope, SharingStarted.Eagerly, WorkspaceModel())

    override val openFilesFlow: StateFlow<List<EditorTab>> = _engineState
        .map { it.openTabs }
        .stateIn(kotlinx.coroutines.GlobalScope, SharingStarted.Eagerly, emptyList())

    override val editorStateFlow: StateFlow<EditorState> = _engineState
        .map { it.editorState }
        .stateIn(kotlinx.coroutines.GlobalScope, SharingStarted.Eagerly, EditorState())

    override val previewStateFlow: StateFlow<PreviewState> = _engineState
        .map { it.previewState }
        .stateIn(kotlinx.coroutines.GlobalScope, SharingStarted.Eagerly, PreviewState())

    override val runtimeStateFlow: StateFlow<RuntimeState> = _engineState
        .map { it.runtimeState }
        .stateIn(kotlinx.coroutines.GlobalScope, SharingStarted.Eagerly, RuntimeState())

    override val consoleStateFlow: StateFlow<ConsoleState> = _engineState
        .map { it.consoleState }
        .stateIn(kotlinx.coroutines.GlobalScope, SharingStarted.Eagerly, ConsoleState())

    override val errorFlow: StateFlow<String?> = errorStateManager.error

    init {
        diagnosticsManager.logEvent("WebStudioEngine initialized")
    }

    override fun openStudio() {
        _engineState.update { it.copy(isStudioOpen = true) }
    }

    override fun closeStudio() {
        _engineState.update { it.copy(isStudioOpen = false) }
    }

    override fun createProject(name: String, template: String): File? {
        return try {
            val projectDir = repository.createProjectDirectory(name)
            ProjectTemplates.applyTemplate(projectDir, template)
            val projectModel = ProjectModel(name = name, rootDir = projectDir, template = template)
            
            _engineState.update {
                it.copy(
                    currentDir = projectDir,
                    workspace = WorkspaceModel(currentProject = projectModel, currentDir = projectDir)
                )
            }
            loadDirectory(projectDir)
            openDefaultFile(projectDir)
            diagnosticsManager.logEvent("Created project: $name at ${projectDir.absolutePath}")
            projectDir
        } catch (e: Exception) {
            diagnosticsManager.logError("Failed to create project $name", e)
            null
        }
    }

    private fun openDefaultFile(projectDir: File) {
        val indexHtml = File(projectDir, "index.html")
        if (indexHtml.exists()) {
            openFile(indexHtml)
        } else {
            val firstFile = projectDir.listFiles()?.firstOrNull { it.isFile }
            if (firstFile != null) {
                openFile(firstFile)
            }
        }
    }

    override fun openProject(dir: File) {
        loadDirectory(dir)
        openDefaultFile(dir)
    }

    override fun closeProject() {
        _engineState.update {
            it.copy(
                currentDir = null,
                fileList = emptyList(),
                openTabs = emptyList(),
                activeTabId = null,
                workspace = WorkspaceModel()
            )
        }
    }

    override fun deleteProject(dir: File): Boolean {
        val success = repository.deleteProject(dir)
        if (success && _engineState.value.currentDir == dir) {
            closeProject()
        }
        return success
    }

    override fun renameProject(dir: File, newName: String): Boolean {
        return repository.renameFile(dir, newName)
    }

    override fun createFile(parentDir: File, name: String, content: String): File? {
        val newFile = repository.createFile(parentDir, name, content)
        if (newFile != null) {
            loadDirectory(_engineState.value.currentDir ?: parentDir)
            openFile(newFile)
        } else {
            errorStateManager.setError("Failed to create file $name")
        }
        return newFile
    }

    override fun createFolder(parentDir: File, name: String): File? {
        val newFolder = repository.createFolder(parentDir, name)
        if (newFolder != null) {
            loadDirectory(_engineState.value.currentDir ?: parentDir)
        } else {
            errorStateManager.setError("Failed to create folder $name")
        }
        return newFolder
    }

    override fun deleteFile(file: File): Boolean {
        val tabId = file.absolutePath
        closeFile(tabId)
        val success = repository.deleteFile(file)
        if (success) {
            loadDirectory(_engineState.value.currentDir ?: file.parentFile)
        }
        return success
    }

    override fun renameFile(file: File, newName: String): Boolean {
        val oldId = file.absolutePath
        val success = repository.renameFile(file, newName)
        if (success) {
            val newFile = File(file.parentFile, newName)
            loadDirectory(_engineState.value.currentDir ?: file.parentFile)
            closeFile(oldId)
            if (newFile.isFile) openFile(newFile)
        }
        return success
    }

    override fun loadProject(uri: Uri) {
        crashRecoveryManager.executeSafe("Load Project") {
            loadingStateManager.setLoading(true, "Extracting project...")
            val projectDir = projectLoader.importZip(uri)
            if (projectDir != null) {
                val projectName = projectDir.name
                val projectModel = ProjectModel(name = projectName, rootDir = projectDir)
                _engineState.update {
                    it.copy(
                        currentDir = projectDir,
                        workspace = WorkspaceModel(currentProject = projectModel, currentDir = projectDir)
                    )
                }
                loadDirectory(projectDir)
                openDefaultFile(projectDir)
                diagnosticsManager.logEvent("Project loaded: ${projectDir.absolutePath}")
            } else {
                errorStateManager.setError("Failed to extract ZIP project")
            }
            loadingStateManager.setLoading(false)
        }
    }

    fun loadDirectory(dir: File?) {
        crashRecoveryManager.executeSafe("Load Directory") {
            _engineState.update { it.copy(currentDir = dir) }
            fileExplorerEngine.loadDirectory(dir) { files ->
                _engineState.update { state ->
                    val fileModels = files.map { FileModel(it) }
                    state.copy(
                        fileList = files,
                        workspace = state.workspace.copy(currentDir = dir, fileTree = fileModels)
                    )
                }
            }
        }
    }

    override fun openFile(file: File) {
        crashRecoveryManager.executeSafe("Open File") {
            if (file.isDirectory) {
                loadDirectory(file)
                return@executeSafe
            }
            loadingStateManager.setLoading(true, "Opening file...")

            val currentState = _engineState.value
            val id = file.absolutePath

            if (currentState.openTabs.none { it.id == id }) {
                try {
                    val content = file.readText()
                    val tab = tabStateManager.createTab(id, file.name, content, file)
                    val newTabs = currentState.openTabs + tab
                    _engineState.update {
                        it.copy(
                            openTabs = newTabs,
                            activeTabId = id,
                            editorState = it.editorState.copy(openTabs = newTabs, activeTabId = id)
                        )
                    }
                    diagnosticsManager.logEvent("File opened successfully: ${file.name}")
                } catch (e: Exception) {
                    errorStateManager.setError("Failed to read file: ${e.message}")
                    diagnosticsManager.logError("File read failure: ${file.name}", e)
                }
            } else {
                _engineState.update {
                    it.copy(
                        activeTabId = id,
                        editorState = it.editorState.copy(activeTabId = id)
                    )
                }
                diagnosticsManager.logEvent("Switched to existing tab: ${file.name}")
            }
            loadingStateManager.setLoading(false)
        }
    }

    override fun closeFile(tabId: String) {
        closeTab(tabId)
    }

    fun closeTab(id: String) {
        crashRecoveryManager.executeSafe("Close Tab") {
            val state = _engineState.value
            val result = tabStateManager.closeTab(id, state.openTabs, state.activeTabId)
            _engineState.update {
                it.copy(
                    openTabs = result.first,
                    activeTabId = result.second,
                    editorState = it.editorState.copy(openTabs = result.first, activeTabId = result.second)
                )
            }
            diagnosticsManager.logEvent("Tab closed: $id")
        }
    }

    override fun updateFileContent(tabId: String, content: String) {
        val state = _engineState.value
        val newTabs = tabStateManager.updateTabContent(tabId, content, state.openTabs)
        _engineState.update {
            it.copy(
                openTabs = newTabs,
                editorState = it.editorState.copy(openTabs = newTabs)
            )
        }
    }

    fun updateActiveTabContent(newContent: String) {
        val state = _engineState.value
        state.activeTabId?.let { updateFileContent(it, newContent) }
    }

    override fun saveFile(tabId: String) {
        crashRecoveryManager.executeSafe("Save File") {
            val state = _engineState.value
            val tab = state.openTabs.find { it.id == tabId }
            if (tab?.file != null) {
                tab.file.writeText(tab.content)
                diagnosticsManager.logEvent("Saved file: ${tab.name}")
            }
        }
    }

    fun saveActiveTab() {
        val activeId = _engineState.value.activeTabId
        if (activeId != null) saveFile(activeId)
    }

    override fun saveProject() {
        val state = _engineState.value
        state.openTabs.forEach { tab ->
            if (tab.file != null) {
                tab.file.writeText(tab.content)
            }
        }
        diagnosticsManager.logEvent("All open files saved")
    }

    override fun startPreview() {
        _engineState.update {
            it.copy(
                showPreview = true,
                showDevTools = false,
                previewState = it.previewState.copy(isPreviewing = true)
            )
        }
    }

    override fun stopPreview() {
        _engineState.update {
            it.copy(
                showPreview = false,
                previewState = it.previewState.copy(isPreviewing = false)
            )
        }
    }

    override fun refreshPreview() {
        _engineState.update {
            it.copy(previewState = it.previewState.copy(isLoading = true, hasError = false))
        }
    }

    override fun runProject() {
        saveProject()
        startPreview()
        _engineState.update {
            it.copy(runtimeState = RuntimeState(isRunning = true, statusMessage = "Running project"))
        }
    }

    override fun stopProject() {
        stopPreview()
        _engineState.update {
            it.copy(runtimeState = RuntimeState(isRunning = false, statusMessage = "Stopped"))
        }
    }

    fun togglePreview() {
        crashRecoveryManager.executeSafe("Toggle Preview") {
            val newShow = !_engineState.value.showPreview
            _engineState.update {
                it.copy(
                    showPreview = newShow,
                    showDevTools = false,
                    previewState = it.previewState.copy(isPreviewing = newShow)
                )
            }
        }
    }

    fun toggleDevTools() {
        crashRecoveryManager.executeSafe("Toggle DevTools") {
            _engineState.update {
                it.copy(showDevTools = !it.showDevTools, showPreview = false)
            }
        }
    }

    override fun getPreviewHtml(activeTab: EditorTab?): String? {
        if (activeTab == null) return null
        return if (activeTab.name.endsWith(".md", ignoreCase = true) || activeTab.name.endsWith(".markdown", ignoreCase = true)) {
            diagnosticsManager.logEvent("Rendering markdown preview for ${activeTab.name}")
            markdownRenderer.renderToHtml(activeTab.content)
        } else {
            activeTab.content
        }
    }

    override fun exportProject(context: Context) {
        val currentDir = _engineState.value.currentDir ?: return
        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()
        val zipFile = File(exportDir, "${currentDir.name}.zip")
        val success = repository.zipProject(currentDir, zipFile)
        if (success) {
            diagnosticsManager.logEvent("Exported project to ${zipFile.absolutePath}")
        } else {
            errorStateManager.setError("Failed to export project")
        }
    }

    override fun clearError() {
        errorStateManager.clearError()
    }
}
