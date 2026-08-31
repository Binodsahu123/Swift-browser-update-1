package com.swift.browser.webstudio

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import java.io.File

class WebStudioActivity : ComponentActivity() {

    private val viewModel: WebStudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataUri: Uri? = intent?.data
        val filePath: String? = intent?.getStringExtra("file_path")

        if (dataUri != null) {
            val uriStr = dataUri.toString()
            if (uriStr.endsWith(".zip", ignoreCase = true) || intent?.type == "application/zip") {
                viewModel.importZipProject(this, dataUri)
            } else if (dataUri.scheme == "file") {
                dataUri.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        viewModel.openFile(file)
                    }
                }
            } else {
                viewModel.importZipProject(this, dataUri)
            }
        } else if (!filePath.isNullOrEmpty()) {
            val file = File(filePath)
            if (file.exists()) {
                viewModel.openFile(file)
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebStudioScreen(
                        viewModel = viewModel,
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}
