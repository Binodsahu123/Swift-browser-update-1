package com.swift.browser.extensionengine

class CssInjector {

    /**
     * Appends a style block with raw CSS definitions into the document head container.
     */
    fun injectCss(evaluator: ScriptEvaluator, cssContent: String) {
        if (cssContent.isBlank()) return
        evaluator.post {
            try {
                val escapedCss = cssContent
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", " ")
                    .replace("\r", " ")
                
                val stylePayloadScript = """
                    (function() {
                        const style = document.createElement('style');
                        style.type = 'text/css';
                        style.innerHTML = '$escapedCss';
                        document.head.appendChild(style);
                    })();
                """.trimIndent()
                
                evaluator.evaluateJavascript(stylePayloadScript, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Appends a style block identified by a unique key for dynamic script injections.
     */
    fun injectCssWithKey(
        evaluator: ScriptEvaluator,
        styleKey: String,
        cssContent: String,
        onComplete: (() -> Unit)? = null
    ) {
        if (cssContent.isBlank()) return
        evaluator.post {
            try {
                val stylePayloadScript = """
                    (function() {
                        if (document.getElementById('$styleKey')) return;
                        const style = document.createElement('style');
                        style.id = '$styleKey';
                        style.type = 'text/css';
                        style.innerHTML = ${org.json.JSONObject.quote(cssContent)};
                        (document.head || document.documentElement).appendChild(style);
                    })();
                """.trimIndent()
                
                evaluator.evaluateJavascript(stylePayloadScript) {
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete?.invoke()
            }
        }
    }

    /**
     * Removes a style element matching the given key from the document.
     */
    fun removeCssByKey(
        evaluator: ScriptEvaluator,
        styleKey: String,
        onComplete: (() -> Unit)? = null
    ) {
        evaluator.post {
            try {
                val jsPayload = """
                    (function() {
                        const style = document.getElementById('$styleKey');
                        if (style) style.remove();
                    })();
                """.trimIndent()
                
                evaluator.evaluateJavascript(jsPayload) {
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete?.invoke()
            }
        }
    }
}
