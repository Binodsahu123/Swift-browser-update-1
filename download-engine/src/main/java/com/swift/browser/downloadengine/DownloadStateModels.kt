package com.swift.browser.downloadengine
 
data class DownloadBrowsingContext(
    val isPrivate: Boolean = false,
    val privateSessionId: String? = null
) {
    companion object {
        val NORMAL = DownloadBrowsingContext(isPrivate = false)
        val PRIVATE = DownloadBrowsingContext(isPrivate = true)
    }
}

data class DownloadConfirmState(
    val show: Boolean = false,
    val url: String = "",
    val fileName: String = "",
    val contentLength: Long = 0L,
    val mimeType: String = "",
    val userAgent: String = "",
    val isPrivate: Boolean = false,
    val privateSessionId: String? = null
)
 
data class DownloadProgressState(
    val showProgress: Boolean = false,
    val fileName: String = "",
    val progress: Int = 0,
    val downloadId: Long = 0L,
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
    val mimeType: String = "",
    val isPrivate: Boolean = false,
    val privateSessionId: String? = null
)
