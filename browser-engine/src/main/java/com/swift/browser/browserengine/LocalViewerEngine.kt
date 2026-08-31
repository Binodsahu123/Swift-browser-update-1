package com.swift.browser.browserengine

import android.content.Context
import android.content.Intent
import android.net.Uri

object LocalViewerEngine {

    fun openLocalFile(
        context: Context,
        filePath: String,
        fileName: String,
        mimeType: String,
        onCustomViewer: (filePath: String, fileName: String, mimeType: String) -> Unit
    ) {
        val pkg = context.packageName
        if (mimeType.startsWith("video/")) {
            try {
                val intent = Intent().setClassName(pkg, "com.swift.browser.videoengine.ui.VideoPlayerActivity").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("VIDEO_URL", filePath)
                    putExtra("VIDEO_TITLE", fileName)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (mimeType.startsWith("audio/")) {
            try {
                val intent = Intent().setClassName(pkg, "com.swift.browser.audioengine.AudioPlayerActivity").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(filePath)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (mimeType.startsWith("image/")) {
            try {
                val intent = Intent().setClassName(pkg, "com.swift.browser.imageengine.ImageViewerActivity").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("file_path", filePath)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onCustomViewer(filePath, fileName, mimeType)
    }
}

