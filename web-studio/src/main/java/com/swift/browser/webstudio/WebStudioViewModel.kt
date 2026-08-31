package com.swift.browser.webstudio

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swift.browser.webstudio.api.WebStudioEngineApi
import com.swift.browser.webstudio.engine.WebStudioEngine
import com.swift.browser.webstudio.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class WebStudioViewModel(application: Application) : AndroidViewModel(application) {

    val engineApi: WebStudioEngineApi = WebStudioEngine(application.applicationContext)

    val uiState: StateFlow<WebStudioState> = engineApi.studioState
    val workspaceFlow: StateFlow<WorkspaceModel> = engineApi.workspaceFlow
    val openFilesFlow: StateFlow<List<EditorTab>> = engineApi.openFilesFlow
    val editorStateFlow: StateFlow<EditorState> = engineApi.editorStateFlow
    val previewStateFlow: StateFlow<PreviewState> = engineApi.previewStateFlow
    val runtimeStateFlow: StateFlow<RuntimeState> = engineApi.runtimeStateFlow
    val consoleStateFlow: StateFlow<ConsoleState> = engineApi.consoleStateFlow
    val errorFlow: StateFlow<String?> = engineApi.errorFlow

    val isLoading = (engineApi as WebStudioEngine).loadingStateManager.isLoading
    val loadingMessage = (engineApi as WebStudioEngine).loadingStateManager.loadingMessage
    val errorMessage = (engineApi as WebStudioEngine).errorStateManager.error

    fun setCurrentDirectory(dir: File?) {
        viewModelScope.launch(Dispatchers.IO) {
            (engineApi as WebStudioEngine).loadDirectory(dir)
        }
    }

    fun createProject(name: String, template: String = "HTML5") {
        viewModelScope.launch(Dispatchers.IO) {
            engineApi.createProject(name, template)
        }
    }

    fun openProject(dir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            engineApi.openProject(dir)
        }
    }

    fun createFile(parentDir: File, name: String, content: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            engineApi.createFile(parentDir, name, content)
        }
    }

    fun createFolder(parentDir: File, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            engineApi.createFolder(parentDir, name)
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            engineApi.deleteFile(file)
        }
    }

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            engineApi.renameFile(file, newName)
        }
    }

    fun openFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            engineApi.openFile(file)
        }
    }

    fun closeTab(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            engineApi.closeFile(id)
        }
    }

    fun updateActiveTabContent(newContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val activeTabId = uiState.value.activeTabId
            if (activeTabId != null) {
                engineApi.updateFileContent(activeTabId, newContent)
            }
        }
    }

    fun saveActiveTab() {
        viewModelScope.launch(Dispatchers.IO) {
            val activeTabId = uiState.value.activeTabId
            if (activeTabId != null) {
                engineApi.saveFile(activeTabId)
            }
        }
    }

    fun togglePreview() {
        viewModelScope.launch(Dispatchers.IO) {
            (engineApi as WebStudioEngine).togglePreview()
        }
    }

    fun toggleDevTools() {
        viewModelScope.launch(Dispatchers.IO) {
            (engineApi as WebStudioEngine).toggleDevTools()
        }
    }

    fun importZipProject(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            engineApi.loadProject(uri)
        }
    }

    fun clearError() {
        engineApi.clearError()
    }

    fun getPreviewHtml(activeTab: EditorTab?): String? {
        return engineApi.getPreviewHtml(activeTab)
    }

    fun exportProject(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            engineApi.exportProject(context)
        }
    }
}
