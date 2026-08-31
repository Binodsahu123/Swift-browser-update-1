package com.swift.browser.videoengine.pip

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PictureInPictureManager(private val context: Context) {
    private val _isInPip = MutableStateFlow(false)
    val isInPip: StateFlow<Boolean> = _isInPip.asStateFlow()

    fun setPipState(inPip: Boolean) {
        _isInPip.value = inPip
    }

    fun enterPictureInPicture(activity: Activity?, isPlaying: Boolean): Boolean {
        if (activity == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = buildPipParams(isPlaying)
                activity.enterPictureInPictureMode(params)
                _isInPip.value = true
                return true
            } catch (e: Exception) {
                Log.e("PipManager", "Failed to enter Picture-In-Picture mode", e)
                return false
            }
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildPipParams(isPlaying: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))

        val actions = mutableListOf<RemoteAction>()

        // Play/Pause Action
        val playPauseIntent = Intent("ACTION_MEDIA_PLAY_PAUSE").setPackage(context.packageName)
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIcon = Icon.createWithResource(
            context,
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        actions.add(
            RemoteAction(
                playPauseIcon,
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) "Pause video" else "Play video",
                playPausePendingIntent
            )
        )

        // Next Action
        val nextIntent = Intent("ACTION_MEDIA_NEXT").setPackage(context.packageName)
        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIcon = Icon.createWithResource(context, android.R.drawable.ic_media_next)
        actions.add(
            RemoteAction(
                nextIcon,
                "Next",
                "Next video",
                nextPendingIntent
            )
        )

        builder.setActions(actions)
        return builder.build()
    }

    fun updatePipParams(activity: Activity?, isPlaying: Boolean) {
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && _isInPip.value) {
            try {
                activity.setPictureInPictureParams(buildPipParams(isPlaying))
            } catch (e: Exception) {
                Log.e("PipManager", "Error updating PiP params", e)
            }
        }
    }
}
