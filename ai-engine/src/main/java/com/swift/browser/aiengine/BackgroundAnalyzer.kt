package com.swift.browser.aiengine
import com.swift.browser.aiengine.AISessionManager
import com.swift.browser.aiengine.AISummaryCache
import com.swift.browser.aiengine.AISummaryEngine
import com.swift.browser.aiengine.AISettingsManager
import com.swift.browser.aiengine.FastAIExtractor


import android.content.Context
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BackgroundAnalyzer {
    private const val TAG = "BackgroundAnalyzer"
    private val mainScope = CoroutineScope(Dispatchers.Main)

    fun analyzeActivePageInBackground(
        context: Context,
        tabId: String,
        webView: WebView,
        url: String,
        settingsManager: AISettingsManager,
        targetLangCode: String? = null,
        targetLangName: String? = null
    ) {
        AICacheEngine.initialize(context)
        if (url.isBlank() || url.startsWith("swift://") || url.startsWith("about:") || url.startsWith("file://")) {
            return
        }

        Log.i(TAG, "Initiating ultra-fast background page extraction and analysis for: $url")
        
        mainScope.launch {
            try {
                // Check cache first in AICacheEngine
                val cachedArticle = withContext(Dispatchers.IO) {
                    AICacheEngine.getCachedArticle(url)
                }
                if (cachedArticle != null) {
                    Log.i(TAG, "Page $url already cached in AICacheEngine, skipping JS load.")
                    val cacheKey = "${url}_${settingsManager.defaultProvider}_${settingsManager.responseLength}"
                    val cachedResp = withContext(Dispatchers.IO) {
                        AICacheEngine.getCachedResponse(cacheKey)
                    }
                    if (cachedResp != null) {
                        val parsed = parseAnalysis(cachedResp.responseText)
                        val finalAnalysis = parsed.copy(readingTime = 1, rawResponse = cachedResp.responseText)
                        AISessionManager.updateSessionPage(tabId, url, cachedArticle.rawText, finalAnalysis)
                        
                        val dummySummaryPrompt = "Cached Summary"
                        val cachedItem = SummaryCacheItem(
                            url = url,
                            title = cachedArticle.title,
                            pageType = PageType.GENERAL,
                            summaryPrompt = dummySummaryPrompt,
                            keyPointsPrompt = "",
                            factCheckPrompt = "",
                            translationStateCode = targetLangCode,
                            languageStateName = targetLangName ?: "English",
                            analysisResult = finalAnalysis
                        )
                        AISummaryCache.put(url, cachedItem)
                    }
                    return@launch
                }

                // Run Fast AIExtractor in WebView
                webView.evaluateJavascript(FastAIExtractor.JS_EXTRACT_SCRIPT) { resultJson ->
                    if (resultJson.isNullOrBlank() || resultJson == "null" || resultJson == "undefined") {
                        Log.w(TAG, "WebView content extraction returned empty result.")
                        return@evaluateJavascript
                    }
                    
                    mainScope.launch(Dispatchers.Default) {
                        try {
                            val fastContext = FastAIExtractor.parseJsonToContext(resultJson)
                            
                            // Fast language detection
                            val sampleText = if (fastContext.paragraphs.isNotEmpty()) {
                                fastContext.paragraphs.joinToString(" ").take(400)
                            } else {
                                fastContext.title.take(400)
                            }
                            
                            val detectedCode = try {
                                com.swift.browser.translateengine.LanguageDetector().detectLanguage(sampleText)
                            } catch (e: Exception) {
                                "en"
                            }
                            
                            val detectedLanguageName = when (detectedCode.lowercase().substringBefore("-")) {
                                "en" -> "English"
                                "hi" -> "Hindi"
                                "ta" -> "Tamil"
                                "te" -> "Telugu"
                                "bn" -> "Bengali"
                                "es" -> "Spanish"
                                "fr" -> "French"
                                "de" -> "German"
                                "ja" -> "Japanese"
                                "zh" -> "Chinese"
                                "ar" -> "Arabic"
                                else -> "English"
                            }
                            
                            // Follow visible/target language
                            val chosenOutputLanguage = targetLangName ?: if (settingsManager.preferredLanguage == "Same as Page") {
                                detectedLanguageName
                            } else {
                                settingsManager.preferredLanguage
                            }
                            
                            // Speed-focused Prompt
                            val summaryPrompt = """
                                Summarize this page.
                                
                                Output Language: $chosenOutputLanguage.
                                Ensure everything is written strictly in $chosenOutputLanguage.
                                
                                Format the response exactly as follows. Use the unique bracketed section headers in CAPITAL LETTERS as exact markers. Do not add bold symbols ** inside the section header line.
                                
                                [MAIN TOPIC]
                                A short, concise one-line title.
                                
                                [SHORT SUMMARY]
                                A brief, highly focused 2-3 sentence overview.
                                
                                [KEY POINTS]
                                - Bullet point 1
                                - Bullet point 2
                                - Bullet point 3
                                
                                [IMPORTANT FACTS]
                                - Fact 1
                                - Fact 2

                                Webpage Context:
                                ${fastContext.toFormattedPromptString()}
                            """.trimIndent()
                            
                            val keyPointsPrompt = """
                                Identify key takeaways from this text.
                                Output language: $chosenOutputLanguage.
                                ${fastContext.toFormattedPromptString()}
                            """.trimIndent()
                            
                            val factCheckPrompt = """
                                List critical facts and statements.
                                Output language: $chosenOutputLanguage.
                                ${fastContext.toFormattedPromptString()}
                            """.trimIndent()
                            
                            Log.d(TAG, "Prompts prepared. Inserting temporary cache item.")
                            
                            val temporaryCacheItem = SummaryCacheItem(
                                url = url,
                                title = fastContext.title,
                                pageType = PageType.GENERAL,
                                summaryPrompt = summaryPrompt,
                                keyPointsPrompt = keyPointsPrompt,
                                factCheckPrompt = factCheckPrompt,
                                translationStateCode = targetLangCode,
                                languageStateName = chosenOutputLanguage,
                                analysisResult = null
                            )
                            AISummaryCache.put(url, temporaryCacheItem)
                            
                            // Save raw webpage text to database cache
                            AICacheEngine.cacheArticle(url, fastContext.title, resultJson)

                            // Instant execution of AI analysis in safe IO background thread
                            withContext(Dispatchers.IO) {
                                try {
                                    Log.i(TAG, "Executing pre-load AI content analysis via AISummaryEngine")
                                    val rawResult = AISummaryEngine.analyzePage(resultJson, settingsManager, context)
                                    val parsedResult = parseAnalysis(rawResult)
                                    val finalAnalysis = parsedResult.copy(readingTime = 1, rawResponse = rawResult)
                                    
                                    val completedCacheItem = temporaryCacheItem.copy(analysisResult = finalAnalysis)
                                    AISummaryCache.put(url, completedCacheItem)
                                    
                                    // Cache full response in SQLite too
                                    val cacheKey = "${url}_${settingsManager.defaultProvider}_${settingsManager.responseLength}"
                                    AICacheEngine.cacheResponse(cacheKey, rawResult, settingsManager.defaultProvider)

                                    AISessionManager.updateSessionPage(tabId, url, resultJson, finalAnalysis)
                                    Log.i(TAG, "Background pre-loading completed successfully. Cache and Session updated for: $url")
                                    
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed pre-load AI API call: ${e.localizedMessage}", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in default dispatcher background job", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating WebView Javascript evaluation on main scope", e)
            }
        }
    }
}
