package com.swift.browser.webstudio.api

import android.content.Context
import android.net.Uri
import com.swift.browser.webstudio.EditorTab
import com.swift.browser.webstudio.WebStudioState
import com.swift.browser.webstudio.model.*
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface WebStudioEngineApi {
    val studioState: StateFlow<WebStudioState>
    val projectFlow: StateFlow<File?>
    val workspaceFlow: StateFlow<WorkspaceModel>
    val openFilesFlow: StateFlow<List<EditorTab>>
    val editorStateFlow: StateFlow<EditorState>
    val previewStateFlow: StateFlow<PreviewState>
    val runtimeStateFlow: StateFlow<RuntimeState>
    val consoleStateFlow: StateFlow<ConsoleState>
    val errorFlow: StateFlow<String?>

    fun openStudio()
    fun closeStudio()
    fun createProject(name: String, template: String = "HTML5"): File?
    fun openProject(dir: File)
    fun closeProject()
    fun deleteProject(dir: File): Boolean
    fun renameProject(dir: File, newName: String): Boolean
    fun createFile(parentDir: File, name: String, content: String = ""): File?
    fun createFolder(parentDir: File, name: String): File?
    fun deleteFile(file: File): Boolean
    fun renameFile(file: File, newName: String): Boolean
    fun openFile(file: File)
    fun closeFile(tabId: String)
    fun updateFileContent(tabId: String, content: String)
    fun saveFile(tabId: String)
    fun saveProject()
    fun loadProject(uri: Uri)
    fun startPreview()
    fun stopPreview()
    fun refreshPreview()
    fun runProject()
    fun stopProject()
    fun getPreviewHtml(activeTab: EditorTab?): String?
    fun exportProject(context: Context)
    fun clearError()
}
