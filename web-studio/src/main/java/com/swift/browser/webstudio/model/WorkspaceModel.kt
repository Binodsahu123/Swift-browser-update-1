package com.swift.browser.webstudio.model

import java.io.File

data class WorkspaceModel(
    val currentProject: ProjectModel? = null,
    val currentDir: File? = null,
    val fileTree: List<FileModel> = emptyList(),
    val activeFilePath: String? = null
)
