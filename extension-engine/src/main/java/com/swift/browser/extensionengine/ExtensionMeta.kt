package com.swift.browser.extensionengine

data class ExtensionMeta(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val size: String,
    val provider: String,
    val lastUpdated: String,
    val permissionDescription: String,
    val defaultInstalled: Boolean = false,
    val iconPath: String = ""
)
