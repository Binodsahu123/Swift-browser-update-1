package com.swift.browser.extensionengine

import android.content.Context

class ExtensionRuntime(
    val parsedExtension: ParsedExtension,
    private val context: Context,
    private val backgroundScriptManager: BackgroundScriptManager,
    private val contentScriptManager: ContentScriptManager,
    private val popupManager: PopupManager,
    private val bootstrapScript: String
) {
    var isActive: Boolean = false
        private set

    fun start() {
        if (isActive) return
        isActive = true
        backgroundScriptManager.startBackgroundWorker(parsedExtension, bootstrapScript)
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        backgroundScriptManager.stopBackgroundWorker(parsedExtension.id)
    }

    fun matchAndInjectContentScripts(evaluator: ScriptEvaluator, url: String, runAt: String = "document_idle") {
        if (!isActive) return
        contentScriptManager.matchAndInject(
            evaluator = evaluator,
            url = url,
            parsedExtensions = listOf(parsedExtension),
            runAtFilter = runAt,
            bootstrapScriptProvider = { bootstrapScript }
        )
    }

    fun getActionPopupUrl(): String? {
        return popupManager.getPopupUrl(context, parsedExtension.id, parsedExtension.actionPopup)
    }
}
