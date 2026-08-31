package com.swift.browser.extensionengine

/**
 * Interface that abstracts dynamic script/code compilation targets.
 * Decouples extension content injections from platform-specific WebViews.
 */
interface ScriptEvaluator {
    fun evaluateJavascript(code: String, callback: ((String?) -> Unit)? = null)
    fun post(action: () -> Unit)
}

class DelegateScriptEvaluator(
    private val delegate: BrowserDelegate,
    private val tabId: String
) : ScriptEvaluator {
    override fun evaluateJavascript(code: String, callback: ((String?) -> Unit)?) {
        delegate.executeScriptOnTab(tabId, code) { result ->
            callback?.invoke(result)
        }
    }

    override fun post(action: () -> Unit) {
        action()
    }
}

class ScriptInjector {

    /**
     * Runs a block of JavaScript code inside the target evaluator scope.
     */
    fun injectScript(evaluator: ScriptEvaluator, code: String, onResult: ((String?) -> Unit)? = null) {
        evaluator.post {
            try {
                evaluator.evaluateJavascript(code) { value ->
                    onResult?.invoke(value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult?.invoke(null)
            }
        }
    }

    /**
     * Executes a block of JavaScript on a tab via the canonical BrowserDelegate interface.
     */
    fun executeScriptOnTab(
        delegate: BrowserDelegate,
        tabId: String,
        code: String,
        onResult: ((String?) -> Unit)? = null
    ) {
        val evaluator = DelegateScriptEvaluator(delegate, tabId)
        injectScript(evaluator, code, onResult)
    }
}
