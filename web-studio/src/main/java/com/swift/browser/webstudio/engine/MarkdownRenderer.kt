package com.swift.browser.webstudio.engine

import android.util.Base64

class MarkdownRenderer {
    fun renderToHtml(markdown: String): String {
        val base64Md = Base64.encodeToString(markdown.toByteArray(), Base64.NO_WRAP)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        padding: 16px;
                        max-width: 800px;
                        margin: 0 auto;
                        background: #ffffff;
                    }
                    img { max-width: 100%; height: auto; }
                    pre {
                        background: #f6f8fa;
                        padding: 16px;
                        border-radius: 8px;
                        overflow-x: auto;
                    }
                    code {
                        font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", Menlo, monospace;
                        background: #f6f8fa;
                        padding: 0.2em 0.4em;
                        border-radius: 4px;
                        font-size: 85%;
                    }
                    pre code {
                        background: transparent;
                        padding: 0;
                    }
                    blockquote {
                        border-left: 4px solid #dfe2e5;
                        color: #6a737d;
                        padding: 0 1em;
                        margin-left: 0;
                    }
                    table {
                        border-collapse: collapse;
                        width: 100%;
                        margin-bottom: 16px;
                    }
                    th, td {
                        border: 1px solid #dfe2e5;
                        padding: 6px 13px;
                    }
                    th { background-color: #f6f8fa; }
                    a { color: #0366d6; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                </style>
            </head>
            <body>
                <div id="content">Loading preview...</div>
                <script>
                    try {
                        const base64Md = "$base64Md";
                        const decodedMd = decodeURIComponent(escape(window.atob(base64Md)));
                        document.getElementById('content').innerHTML = marked.parse(decodedMd);
                    } catch (e) {
                        document.getElementById('content').innerHTML = "<p style='color:red;'>Failed to render markdown: " + e.message + "</p>";
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
