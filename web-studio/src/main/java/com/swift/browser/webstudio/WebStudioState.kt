package com.swift.browser.webstudio

import android.net.Uri
import com.swift.browser.webstudio.model.*
import java.io.File

data class EditorTab(
    val id: String,
    val name: String,
    var content: String,
    val uri: Uri? = null,
    val file: File? = null
)

data class WebStudioState(
    val currentDir: File? = null,
    val fileList: List<File> = emptyList(),
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabId: String? = null,
    val showPreview: Boolean = false,
    val showDevTools: Boolean = false,
    val theme: String = "Dark",
    val workspace: WorkspaceModel = WorkspaceModel(),
    val editorState: EditorState = EditorState(),
    val previewState: PreviewState = PreviewState(),
    val runtimeState: RuntimeState = RuntimeState(),
    val consoleState: ConsoleState = ConsoleState(),
    val isStudioOpen: Boolean = true
)
