package com.swift.browser.translateengine

import kotlin.math.ceil

object TranslationBatchManager {

    /**
     * Chunk a list of text nodes into batches.
     * Scale batch sizes and concurrency workers dynamically.
     * For 1000+ items: batches of 50
     * For 5000+ items: batches of 100 - 200, allowing more workers in parallel to prevent congestion.
     */
    fun <T> createBatches(items: List<T>): BatchConfig<T> {
        val totalSize = items.size
        val batchSize = when {
            totalSize >= 5000 -> 150
            totalSize >= 1000 -> 60
            else -> 30
        }

        val workerCount = when {
            totalSize >= 5000 -> 6
            totalSize >= 1000 -> 4
            else -> 2
        }

        val batches = ArrayList<List<T>>()
        for (i in items.indices step batchSize) {
            val end = (i + batchSize).coerceAtMost(items.size)
            batches.add(items.subList(i, end))
        }

        return BatchConfig(
            batches = batches,
            recommendedWorkers = workerCount,
            batchSize = batchSize
        )
    }

    data class BatchConfig<T>(
        val batches: List<List<T>>,
        val recommendedWorkers: Int,
        val batchSize: Int
    )
}
