package com.swift.browser.webstudio.manager

import java.io.File

object ProjectTemplates {

    fun applyTemplate(projectDir: File, template: String) {
        when (template) {
            "HTML5" -> createHtml5Boilerplate(projectDir)
            "JavaScript" -> createJsAppBoilerplate(projectDir)
            "CSS Playground" -> createCssPlaygroundBoilerplate(projectDir)
            "Markdown Note" -> createMarkdownBoilerplate(projectDir)
            else -> createHtml5Boilerplate(projectDir)
        }
    }

    private fun createHtml5Boilerplate(dir: File) {
        val indexHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Web Studio App</title>
                <link rel="stylesheet" href="style.css">
            </head>
            <body>
                <div class="container">
                    <h1>🚀 Welcome to Web Studio</h1>
                    <p>Edit index.html, style.css, and app.js to build your web app!</p>
                    <button id="btn">Click Me</button>
                </div>
                <script src="app.js"></script>
            </body>
            </html>
        """.trimIndent()

        val styleCss = """
            body {
                font-family: system-ui, -apple-system, sans-serif;
                background-color: #0f172a;
                color: #f8fafc;
                display: flex;
                justify-content: center;
                align-items: center;
                height: 100vh;
                margin: 0;
            }
            .container {
                text-align: center;
                padding: 2rem;
                background: #1e293b;
                border-radius: 12px;
                box-shadow: 0 10px 25px rgba(0,0,0,0.5);
            }
            button {
                background: #38bdf8;
                color: #0f172a;
                border: none;
                padding: 10px 20px;
                font-weight: bold;
                border-radius: 6px;
                cursor: pointer;
            }
        """.trimIndent()

        val appJs = """
            document.getElementById('btn').addEventListener('click', () => {
                alert('Hello from Web Studio App!');
            });
        """.trimIndent()

        File(dir, "index.html").writeText(indexHtml)
        File(dir, "style.css").writeText(styleCss)
        File(dir, "app.js").writeText(appJs)
    }

    private fun createJsAppBoilerplate(dir: File) {
        val indexHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>JS App</title>
            </head>
            <body>
                <div id="app"></div>
                <script src="main.js"></script>
            </body>
            </html>
        """.trimIndent()

        val mainJs = """
            const app = document.getElementById('app');
            app.innerHTML = '<h2>Interactive JS Engine Running</h2>';
        """.trimIndent()

        File(dir, "index.html").writeText(indexHtml)
        File(dir, "main.js").writeText(mainJs)
    }

    private fun createCssPlaygroundBoilerplate(dir: File) {
        val indexHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <link rel="stylesheet" href="styles.css">
            </head>
            <body>
                <div class="card">
                    <h2>CSS Playground</h2>
                    <p>Experiment with modern CSS styles!</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val stylesCss = """
            body {
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                font-family: sans-serif;
                height: 100vh;
                display: flex;
                align-items: center;
                justify-content: center;
            }
            .card {
                background: rgba(255, 255, 255, 0.9);
                padding: 30px;
                border-radius: 16px;
                box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
            }
        """.trimIndent()

        File(dir, "index.html").writeText(indexHtml)
        File(dir, "styles.css").writeText(stylesCss)
    }

    private fun createMarkdownBoilerplate(dir: File) {
        val readmeMd = """
            # Web Studio Project
            
            Welcome to your project!
            
            - High performance browser engine
            - Integrated Web Studio IDE
            - Full engine ownership
        """.trimIndent()

        File(dir, "README.md").writeText(readmeMd)
    }
}
