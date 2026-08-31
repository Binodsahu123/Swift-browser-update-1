package com.swift.browser.browserengine

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast

object PrintEngine {

    fun printCurrentPage(context: Context, webView: WebView?) {
        if (webView == null) return
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = webView.createPrintDocumentAdapter("SwiftBrowser Document")
            printManager.print("SwiftBrowser Print Job", printAdapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Print failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun printSavedPdf(context: Context, webView: WebView?, fileName: String) {
        if (webView == null) return
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = webView.createPrintDocumentAdapter(fileName)
            printManager.print(
                fileName,
                printAdapter,
                PrintAttributes.Builder().build()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

object PdfUtility {
    fun printTextToPdf(context: Context, fileName: String, content: String) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            Toast.makeText(context, "Printing $fileName...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

