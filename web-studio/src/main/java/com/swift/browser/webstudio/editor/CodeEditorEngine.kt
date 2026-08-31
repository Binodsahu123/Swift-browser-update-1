package com.swift.browser.webstudio.editor

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.swift.browser.webstudio.MobileCodingKeyboard

@Composable
fun CodeEditorEngine(content: String, language: String, theme: String, onContentChanged: (String) -> Unit) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun onContentChanged(newContent: String) {
                            onContentChanged(newContent)
                        }
                    }, "Android")
                    
                    val isDark = theme == "Dark" || theme == "High-Contrast"
                    val vsTheme = if (theme == "High-Contrast") "hc-black" else if (isDark) "vs-dark" else "vs"
                    val escapedContent = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
                    
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="utf-8">
                            <style>
                                html, body, #container { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; }
                            </style>
                        </head>
                        <body>
                            <div id="container"></div>
                            <script src="https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.39.0/min/vs/loader.min.js"></script>
                            <script>
                                require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.39.0/min/vs' }});
                                require(['vs/editor/editor.main'], function() {
                                    var editor = monaco.editor.create(document.getElementById('container'), {
                                        value: "$escapedContent",
                                        language: '$language',
                                        theme: '$vsTheme',
                                        automaticLayout: true,
                                        minimap: { enabled: false },
                                        lineNumbers: 'on',
                                        autoIndent: 'full',
                                        autoClosingBrackets: 'always',
                                        autoClosingQuotes: 'always',
                                        folding: true,
                                        find: {
                                            addExtraSpaceOnTop: false,
                                            autoFindInSelection: 'always',
                                            seedSearchStringFromSelection: 'always'
                                        }
                                    });
                                    
                                    editor.onDidChangeModelContent(function() {
                                        var val = editor.getValue();
                                        Android.onContentChanged(val);
                                    });
                                    
                                    window.insertText = function(text) {
                                        var position = editor.getPosition();
                                        editor.executeEdits("keyboard", [{
                                            range: new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column),
                                            text: text === 'Tab' ? '\t' : text,
                                            forceMoveMarkers: true
                                        }]);
                                        editor.focus();
                                    };
                                });
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    
                    loadDataWithBaseURL("https://localhost", html, "text/html", "utf-8", null)
                }.also { webViewRef = it }
            },
            modifier = Modifier.weight(1f)
        )
        
        // Mobile Coding Keyboard
        MobileCodingKeyboard(onKeyPress = { key ->
            webViewRef?.evaluateJavascript("window.insertText && window.insertText('$key');", null)
        })
    }
}
