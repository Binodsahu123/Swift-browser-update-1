package com.swift.browser.videoengine.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swift.browser.videoengine.core.VideoPlayerEngine
import com.swift.browser.videoengine.model.VideoItem

class VideoPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentData: Uri? = intent.data
        val videoUrlExtra = intent.getStringExtra(EXTRA_VIDEO_URL) ?: intentData?.toString()
        val videoTitleExtra = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: intentData?.lastPathSegment ?: "Video Player"

        setContent {
            VideoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!videoUrlExtra.isNullOrEmpty()) {
                        StandalonePlayerScreen(
                            videoUrl = videoUrlExtra,
                            videoTitle = videoTitleExtra,
                            onBack = { finish() }
                        )
                    } else {
                        VideoCenterHomeScreen(
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val engine = VideoPlayerEngine.getInstance(this)
        if (engine.isPlaying.value) {
            engine.enterPictureInPicture(this)
        }
    }

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
    }
}
