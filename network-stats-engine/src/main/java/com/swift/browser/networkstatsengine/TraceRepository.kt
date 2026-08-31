package com.swift.browser.networkstatsengine

import com.swift.browser.analyticscore.AnalyticsCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object TraceRepository {
    private val _traces = MutableStateFlow<List<TraceModel>>(emptyList())
    val traces = _traces.asStateFlow()

    fun addTrace(trace: TraceModel) {
        val currentList = _traces.value.toMutableList()
        if (currentList.size >= 500) {
            currentList.removeAt(0)
        }
        currentList.add(trace)
        _traces.value = currentList

        // Forward trace to AnalyticsCore as centralized owner
        AnalyticsCore.logDiagnostic(
            engineName = "network_stats",
            module = "TraceRepository",
            function = "addTrace",
            reason = trace.message
        )
    }

    fun getTracesByClass(clazz: Class<out TraceModel>): List<TraceModel> {
        return _traces.value.filter { clazz.isInstance(it) }
    }

    fun clearAllTraces() {
        _traces.value = emptyList()
    }
}
