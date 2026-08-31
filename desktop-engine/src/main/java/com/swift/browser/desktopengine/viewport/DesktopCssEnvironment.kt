package com.swift.browser.desktopengine.viewport

import android.webkit.WebView
import com.swift.browser.desktopengine.rules.DesktopSiteRule

object DesktopCssEnvironment {
    private const val STYLE_ID = "swift-desktop-css-overrides"

    fun applyCompatibilityCss(
        webView: WebView,
        host: String,
        isDesktop: Boolean,
        rule: DesktopSiteRule? = null
    ) {
        if (!isDesktop) {
            removeCompatibilityCss(webView)
            return
        }

        val customCss = rule?.customCssOverrides.orEmpty()
        val script = getDesktopCssOverrideScript(customCss)
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    fun removeCompatibilityCss(webView: WebView) {
        val script = getMobileCssRestoreScript()
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    fun getDesktopCssOverrideScript(customCss: String = ""): String {
        val escapedCss = customCss.replace("`", "\\`").replace("\n", " ")
        return """
            (function() {
                var styleId = '$STYLE_ID';
                var existing = document.getElementById(styleId);
                var cssContent = `$escapedCss`;
                if (!cssContent || cssContent.trim() === '') {
                    // Default safe layout override without hiding arbitrary elements
                    cssContent = '@media (max-width: 767px) { body, html { min-width: 1024px !important; } }';
                }
                
                if (existing) {
                    existing.innerHTML = cssContent;
                    return;
                }

                var styleNode = document.createElement('style');
                styleNode.id = styleId;
                styleNode.type = 'text/css';
                styleNode.innerHTML = cssContent;
                document.head.appendChild(styleNode);
            })();
        """.trimIndent()
    }

    fun getMobileCssRestoreScript(): String {
        return """
            (function() {
                var styleNode = document.getElementById('$STYLE_ID');
                if (styleNode) {
                    styleNode.parentNode.removeChild(styleNode);
                }
            })();
        """.trimIndent()
    }
}
