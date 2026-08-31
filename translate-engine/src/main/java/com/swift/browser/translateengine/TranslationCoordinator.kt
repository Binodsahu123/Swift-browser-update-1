package com.swift.browser.translateengine

import android.content.Context
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

class TranslationCoordinator(
    private val context: Context,
    var currentProvider: TranslationProvider = GoogleTranslateBridge()
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val repo = TranslationRepository(context)
    private val memoryCache = TranslationCache()
    private val nodeCache = TranslationNodeCache()
    private val queue = TranslationQueue()
    private val tracker = DomTrackingEngine()
    private var observerManager: MutationObserverManager? = null
    var progressManager: TranslationProgressManager? = null

    /**
     * Translates the active page loaded in the WebView using in-place text swap.
     */
    fun translatePage(webView: WebView, targetLang: String, tabId: String, isDesktopMode: Boolean, onFinished: (Int) -> Unit = {}) {
        TranslationDebugger.reset()
        TranslationDebugger.targetLanguage = targetLang
        
        Log.d("TranslationCoordinator", "Starting translation of tab: $tabId to $targetLang, desktopMode: $isDesktopMode")
        val startTime = System.currentTimeMillis()
        progressManager?.reset()
        progressManager?.startTranslation(0)

        // Setup the dynamic observer for automatic translation of new elements (infinite scroll / dynamic SPA)
        setupMutationObserver(webView, targetLang, tabId, isDesktopMode)

        scope.launch {
            DomExtractionEngine.extractContent(webView, isDesktopMode) { rawJson ->
                if (rawJson.isNullOrBlank()) {
                    Log.d("TranslationCoordinator", "No translatable nodes extracted.")
                    onFinished(0)
                    progressManager?.completeTranslation()
                    return@extractContent
                }

                OriginalPageSnapshotManager.captureSnapshot(webView, tabId) { snapshotCount ->
                    Log.d("TranslationCoordinator", "Captured original snapshot: $snapshotCount nodes")
                }

                queue.enqueueJob {
                    try {
                        val count = processPayload(webView, rawJson, targetLang, tabId, isDesktopMode)
                        val elapsed = System.currentTimeMillis() - startTime
                        TranslationDebugger.addTranslationTime(elapsed)
                        
                        // Activate live observer in page
                        observerManager?.startObserving(isDesktopMode)
                        
                        withContext(Dispatchers.Main) {
                            if (TranslationDebugger.failedBatches.get() > 0 && count == 0) {
                                progressManager?.failTranslation()
                            } else {
                                progressManager?.completeTranslation()
                            }
                            onFinished(count)
                        }
                    } catch (e: Exception) {
                        Log.e("TranslationCoordinator", "Translation job error", e)
                        withContext(Dispatchers.Main) {
                            progressManager?.failTranslation()
                            onFinished(0)
                        }
                    }
                }
            }
        }
    }

    /**
     * Stop translation observers and release resources for a tab context.
     */
    fun stopSession(tabId: String) {
        tracker.clearTab(tabId)
        queue.cancelAll()
        nodeCache.clear()
        observerManager = null
    }

    private fun setupMutationObserver(webView: WebView, targetLang: String, tabId: String, isDesktopMode: Boolean) {
        observerManager = MutationObserverManager(webView) { dynamicPayloadJson ->
            if (dynamicPayloadJson.isBlank()) return@MutationObserverManager
            Log.d("TranslationCoordinator", "Mutation detected, translating dynamic additions...")
            
            queue.enqueueJob {
                observerManager?.pauseObserving()
                processPayload(webView, dynamicPayloadJson, targetLang, tabId, isDesktopMode)
                observerManager?.resumeObserving()
            }
        }
    }

    private suspend fun processPayload(
        webView: WebView,
        rawJson: String,
        targetLang: String,
        tabId: String,
        isDesktopMode: Boolean
    ): Int {
        try {
            val jsonArray = JSONArray(rawJson)
            val itemsCount = jsonArray.length()
            TranslationDebugger.textNodesFound.addAndGet(itemsCount)

            if (itemsCount == 0) return 0

            progressManager?.setTotal(itemsCount)

            val toTranslateList = mutableListOf<TranslationRequestItem>()
            val readyTranslations = JSONArray()

            // 1. Query local caches (First nodeCache, then memory lookup, then Room database check)
            for (i in 0 until itemsCount) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val type = obj.optString("type", "")
                val originalText = obj.optString("text", "")

                if (originalText.isBlank()) continue

                val nodeCached = nodeCache.get(id)
                if (nodeCached != null && nodeCached.language == targetLang) {
                    TranslationDebugger.recordCacheHit()
                    val replacement = JSONObject()
                        .put("id", id)
                        .put("type", type)
                        .put("translatedText", nodeCached.translatedText)
                    readyTranslations.put(replacement)
                    continue
                }

                val cached = memoryCache.get(originalText, targetLang) ?: repo.getTranslation(originalText, targetLang)
                if (cached != null) {
                    TranslationDebugger.recordCacheHit()
                    nodeCache.put(id, originalText, cached, targetLang)
                    // Cache hit - ready to replace directly
                    val replacement = JSONObject()
                        .put("id", id)
                        .put("type", type)
                        .put("translatedText", cached)
                    readyTranslations.put(replacement)
                } else {
                    TranslationDebugger.recordCacheMiss()
                    // Cache miss - schedule provider translation
                    toTranslateList.add(TranslationRequestItem(id, type, originalText))
                }
            }

            val cachedCount = readyTranslations.length()
            progressManager?.updateProgress(cachedCount)

            // 2. Chunk cache misses into batches
            if (toTranslateList.isNotEmpty()) {
                val batchConfig = TranslationBatchManager.createBatches(toTranslateList)
                TranslationDebugger.activeWorkersCount = batchConfig.recommendedWorkers
                TranslationDebugger.queueLength = batchConfig.batches.size

                val jobs = mutableListOf<Deferred<List<JSONObject>>>()
                val jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

                val completedNodes = java.util.concurrent.atomic.AtomicInteger(cachedCount)

                // Process batches with limited parallel workers
                batchConfig.batches.forEach { batch ->
                    val job = jobScope.async {
                        val batchResults = translateBatchWithRetry(batch, targetLang, 3)
                        val size = batch.size
                        val currentComp = completedNodes.addAndGet(size)
                        progressManager?.updateProgress(currentComp)
                        batchResults
                    }
                    jobs.add(job)
                }

                // Gather results from parallel tasks
                val results = jobs.awaitAll()
                results.forEach { batchResults ->
                    batchResults.forEach { replacementObj ->
                        val nodeId = replacementObj.optString("id")
                        val originalText = toTranslateList.find { it.id == nodeId }?.originalText ?: ""
                        val translatedText = replacementObj.optString("translatedText")
                        if (nodeId.isNotEmpty() && originalText.isNotEmpty()) {
                            nodeCache.put(nodeId, originalText, translatedText, targetLang)
                        }
                        readyTranslations.put(replacementObj)
                    }
                }
            }

            // 3. Complete structural in-place Dom swap in WebView
            if (readyTranslations.length() > 0) {
                val replacementPayload = readyTranslations.toString()
                withContext(Dispatchers.Main) {
                    DomReplacementEngine.replaceContent(webView, replacementPayload) { res ->
                        Log.d("TranslationCoordinator", "In-place DOM replacement finished with: $res")
                    }
                }
                tracker.recordTranslation(tabId, readyTranslations.length(), "auto")
                TranslationDebugger.textNodesTranslated.addAndGet(readyTranslations.length())
            }

            return readyTranslations.length()
        } catch (e: Exception) {
            Log.e("TranslationCoordinator", "Error processing transaction payload", e)
        }
        return 0
    }

    private suspend fun translateBatchWithRetry(
        items: List<TranslationRequestItem>,
        targetLang: String,
        maxRetries: Int
    ): List<JSONObject> {
        val results = mutableListOf<JSONObject>()
        val textList = items.map { it.originalText }
        
        var attempt = 0
        var success = false
        var translationsMap: Map<String, String>? = null

        while (attempt < maxRetries && !success) {
            attempt++
            try {
                translationsMap = currentProvider.translateBatch(textList, "auto", targetLang)
                success = true
            } catch (e: Exception) {
                Log.e("TranslationCoordinator", "Batch translation failure prompt (attempt $attempt/$maxRetries)", e)
                if (attempt >= maxRetries) {
                    TranslationDebugger.recordFailedBatch()
                } else {
                    delay(500L * attempt) // Progressive backoff delay
                }
            }
        }

        if (success && translationsMap != null) {
            // Write results to cache
            memoryCache.putBatch(translationsMap, targetLang)
            repo.saveBatch(translationsMap, targetLang)

            items.forEach { item ->
                val translatedText = translationsMap[item.originalText] ?: item.originalText
                val obj = JSONObject()
                    .put("id", item.id)
                    .put("type", item.type)
                    .put("translatedText", translatedText)
                results.add(obj)
            }
        } else {
            // Fallback to original text if translation failed
            items.forEach { item ->
                val obj = JSONObject()
                    .put("id", item.id)
                    .put("type", item.type)
                    .put("translatedText", item.originalText)
                results.add(obj)
            }
        }

        return results
    }

    private data class TranslationRequestItem(
        val id: String,
        val type: String,
        val originalText: String
    )
}
