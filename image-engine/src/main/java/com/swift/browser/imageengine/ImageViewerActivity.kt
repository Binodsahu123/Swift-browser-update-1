package com.swift.browser.imageengine

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var filePath = intent.getStringExtra("file_path") ?: ""
        val originalUrl = intent.getStringExtra("original_url") ?: ""

        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                filePath = uri.toString()
            }
        } else if (intent.action == Intent.ACTION_SEND) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                filePath = uri.toString()
            }
        }

        if (filePath.isEmpty()) {
            Toast.makeText(this, "No image specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ImageViewerComponent(
                filePath = filePath,
                originalUrl = originalUrl,
                onDismiss = { finish() }
            )
        }
    }
}
