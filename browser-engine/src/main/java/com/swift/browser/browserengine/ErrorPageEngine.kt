package com.swift.browser.browserengine

import android.webkit.WebView

object ErrorPageEngine {

    fun loadErrorPage(webView: WebView?, errorType: String, failingUrl: String) {
        if (webView == null) return

        val titleText = when (errorType) {
            "offline" -> "You're offline"
            "404" -> "Page not found"
            "ssl" -> "Connection not secure"
            "security" -> "Security Threat Blocked"
            "timeout" -> "Page took too long"
            else -> "Something went wrong"
        }

        val descText = when (errorType) {
            "offline" -> "Check your internet connection and try reloading the page."
            "404" -> "The page you're looking for doesn't exist or has been moved."
            "ssl" -> "The site's security certificate is invalid. Your connection to this site is not private."
            "security" -> "Security Shield blocked access to this URL because it matches a known malware or phishing pattern."
            "timeout" -> "The server at ${failingUrl.take(40)} is taking too long to respond."
            else -> "We encountered an error while trying to process your request."
        }

        val iconSvg = when (errorType) {
            "offline" -> """<svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="#BB86FC" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.5 19A3.5 3.5 0 0 0 21 15.5c0-2.79-2.54-4.5-5-4.5-.42-1.95-2-3.5-4-3.5a5.52 5.52 0 0 0-5.18 3.79c-1.3-.23-2.82.21-3.82 1.21A4.5 4.5 0 0 0 3 15.5c0 1.93 1.57 3.5 3.5 3.5h11z"/><path d="M15 11l-6 6"/><path d="M9 11l6 6"/></svg>"""
            "ssl" -> """<svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="#CF6679" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>"""
            "timeout" -> """<svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="#BB86FC" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>"""
            "security" -> """<svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="#CF6679" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>"""
            else -> """<svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="#CF6679" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>"""
        }

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    body {
                        background-color: #121212;
                        color: #E0E0E0;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        height: 100vh;
                        margin: 0;
                        padding: 24px;
                        box-sizing: border-box;
                        text-align: center;
                    }
                    .icon-container {
                        margin-bottom: 24px;
                    }
                    h1 {
                        font-size: 22px;
                        font-weight: 600;
                        margin: 0 0 12px 0;
                        color: #FFFFFF;
                    }
                    p {
                        font-size: 14px;
                        line-height: 1.5;
                        color: #A0A0A0;
                        margin: 0 0 24px 0;
                        max-width: 320px;
                    }
                    .btn {
                        background-color: #BB86FC;
                        color: #000000;
                        border: none;
                        padding: 12px 28px;
                        border-radius: 20px;
                        font-size: 14px;
                        font-weight: 600;
                        cursor: pointer;
                        text-decoration: none;
                        display: inline-block;
                        transition: opacity 0.2s;
                    }
                    .btn:active {
                        opacity: 0.8;
                    }
                    .url-badge {
                        font-size: 11px;
                        color: #666666;
                        word-break: break-all;
                        margin-top: 24px;
                        max-width: 280px;
                    }
                </style>
            </head>
            <body>
                <div class="icon-container">
                    $iconSvg
                </div>
                <h1>$titleText</h1>
                <p>$descText</p>
                <button class="btn" onclick="location.reload()">Try Again</button>
                <div class="url-badge">$failingUrl</div>
            </body>
            </html>
        """.trimIndent()

        webView.post {
            try {
                webView.loadDataWithBaseURL(failingUrl, htmlContent, "text/html", "UTF-8", null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
