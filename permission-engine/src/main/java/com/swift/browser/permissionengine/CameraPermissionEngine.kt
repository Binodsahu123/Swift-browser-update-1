package com.swift.browser.permissionengine

import android.content.Context

object CameraPermissionEngine {
    fun handleRequest(
        context: Context,
        request: PermissionRequestModel,
        repository: SitePermissionRepository,
        onComplete: (String) -> Unit
    ) {
        val engine = PermissionEngineProvider.get(context)
        engine.handleRequest(
            requestModel = request,
            androidContext = context,
            onComplete = onComplete
        )
    }
}

