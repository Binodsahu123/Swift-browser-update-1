package com.swift.browser.translateengine

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object TranslationDebugger {
    var detectedLanguage: String = "Unknown"
    var targetLanguage: String = "hi"
    
    val textNodesFound = AtomicInteger(0)
    val textNodesTranslated = AtomicInteger(0)
    val cacheHits = AtomicInteger(0)
    val cacheMisses = AtomicInteger(0)
    val failedBatches = AtomicInteger(0)
    
    val totalTranslationTimeMs = AtomicLong(0)
    var activeWorkersCount: Int = 0
    var queueLength: Int = 0

    fun reset() {
        detectedLanguage = "Unknown"
        textNodesFound.set(0)
        textNodesTranslated.set(0)
        cacheHits.set(0)
        cacheMisses.set(0)
        failedBatches.set(0)
        totalTranslationTimeMs.set(0)
        activeWorkersCount = 0
        queueLength = 0
    }

    fun recordCacheHit() {
        cacheHits.incrementAndGet()
    }

    fun recordCacheMiss(count: Int = 1) {
        cacheMisses.addAndGet(count)
    }

    fun recordFailedBatch() {
        failedBatches.incrementAndGet()
    }

    fun addTranslationTime(ms: Long) {
        totalTranslationTimeMs.addAndGet(ms)
    }

    fun getTelemetrySummary(): String {
        return """
            [Translation Telemetry]
            - Language Pair: $detectedLanguage -> $targetLanguage
            - Nodes Found: ${textNodesFound.get()}
            - Nodes Translated: ${textNodesTranslated.get()}
            - Cache Ratio: ${cacheHits.get()} hits / ${cacheMisses.get()} misses
            - Queue Backlog: $queueLength (Workers: $activeWorkersCount)
            - Latency: ${totalTranslationTimeMs.get()}ms
            - Failures: ${failedBatches.get()}
        """.trimIndent()
    }
}
