package com.swift.browser.analyticscore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FeatureUsageAnalyticsManager {
    private val _featureRecords = MutableStateFlow<List<FeatureUsageRecord>>(emptyList())
    val featureRecords: StateFlow<List<FeatureUsageRecord>> = _featureRecords.asStateFlow()

    private val _featureCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val featureCounts: StateFlow<Map<String, Int>> = _featureCounts.asStateFlow()

    fun trackFeature(featureId: String, action: String = "use", metadata: Map<String, String> = emptyMap()) {
        val record = FeatureUsageRecord(
            featureId = featureId,
            action = action,
            metadata = metadata
        )
        val currentRecords = _featureRecords.value.toMutableList()
        if (currentRecords.size >= 200) {
            currentRecords.removeAt(0)
        }
        currentRecords.add(record)
        _featureRecords.value = currentRecords

        val counts = _featureCounts.value.toMutableMap()
        counts[featureId] = (counts[featureId] ?: 0) + 1
        _featureCounts.value = counts
    }

    fun getCountForFeature(featureId: String): Int {
        return _featureCounts.value[featureId] ?: 0
    }

    fun clear() {
        _featureRecords.value = emptyList()
        _featureCounts.value = emptyMap()
    }
}
