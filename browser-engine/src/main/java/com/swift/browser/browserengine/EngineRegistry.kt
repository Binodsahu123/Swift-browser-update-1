package com.swift.browser.browserengine

import com.swift.browser.permissionengine.EngineStateModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EngineRegistry {
    private val _engines = MutableStateFlow<Map<String, EngineStateModel>>(
        mapOf(
            "browser_core" to EngineStateModel(engineId = "browser_core", engineName = "Browser Core"),
            "privacy_engine" to EngineStateModel(engineId = "privacy_engine", engineName = "Privacy Engine"),
            "devmode_engine" to EngineStateModel(engineId = "devmode_engine", engineName = "DevMode Engine"),
            "ai_engine" to EngineStateModel(engineId = "ai_engine", engineName = "AI Engine"),
            "extension_engine" to EngineStateModel(engineId = "extension_engine", engineName = "Extension Engine")
        )
    )
    val engines: StateFlow<Map<String, EngineStateModel>> = _engines.asStateFlow()

    fun getEngine(engineId: String): EngineStateModel? {
        return _engines.value[engineId]
    }

    fun updateEngineState(engineId: String, transform: (EngineStateModel) -> EngineStateModel) {
        val currentMap = _engines.value.toMutableMap()
        val oldState = currentMap[engineId] ?: EngineStateModel(engineId = engineId, engineName = engineId)
        currentMap[engineId] = transform(oldState)
        _engines.value = currentMap
    }
}
