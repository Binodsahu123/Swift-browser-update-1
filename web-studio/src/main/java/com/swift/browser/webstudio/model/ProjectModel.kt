package com.swift.browser.webstudio.model

import java.io.File

data class ProjectModel(
    val name: String,
    val rootDir: File,
    val template: String = "HTML5",
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
)
