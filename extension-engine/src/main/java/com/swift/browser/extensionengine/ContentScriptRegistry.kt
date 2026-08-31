package com.swift.browser.extensionengine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ContentScriptRegistry {
    private val definitions = ConcurrentHashMap<String, ContentScriptDefinition>()
    private val generationCounter = AtomicInteger(1)

    fun register(definition: ContentScriptDefinition) {
        definitions[definition.scriptId] = definition
    }

    fun unregister(scriptId: String) {
        definitions.remove(scriptId)
    }

    fun unregisterAllForExtension(extensionId: String) {
        val keysToRemove = definitions.keys.filter { definitions[it]?.extensionId == extensionId }
        for (key in keysToRemove) {
            definitions.remove(key)
        }
    }

    fun getDefinitionsForExtension(extensionId: String): List<ContentScriptDefinition> {
        return definitions.values.filter { it.extensionId == extensionId }
    }

    fun getAllActiveDefinitions(): List<ContentScriptDefinition> {
        return definitions.values.filter { it.enabled }
    }

    fun getDefinition(scriptId: String): ContentScriptDefinition? {
        return definitions[scriptId]
    }

    fun incrementGeneration(): Int {
        return generationCounter.incrementAndGet()
    }

    fun getGeneration(): Int {
        return generationCounter.get()
    }

    fun clear() {
        definitions.clear()
        generationCounter.set(1)
    }
}
