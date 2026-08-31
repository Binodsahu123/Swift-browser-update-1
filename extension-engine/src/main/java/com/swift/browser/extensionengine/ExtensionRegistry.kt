package com.swift.browser.extensionengine

import java.util.concurrent.ConcurrentHashMap

/**
 * Production-grade Canonical Extension Registry.
 * Serves as the single source of truth for installed extensions and their strict lifecycle state transitions.
 */
class ExtensionRegistry {

    private val registryMap = ConcurrentHashMap<String, RegisteredExtensionInfo>()
    private val extensionGenerations = ConcurrentHashMap<String, Int>()

    /**
     * Registers a newly parsed extension into the registry.
     */
    fun register(
        extension: ParsedExtension,
        initialState: ExtensionState = ExtensionState.INSTALLED_ENABLED
    ) {
        val currentGen = (extensionGenerations[extension.id] ?: 0) + 1
        extensionGenerations[extension.id] = currentGen

        val now = System.currentTimeMillis()
        val existing = registryMap[extension.id]

        val info = RegisteredExtensionInfo(
            extension = extension,
            state = initialState,
            generation = currentGen,
            installedTimestamp = existing?.installedTimestamp ?: now,
            lastStateChangeTimestamp = now
        )

        registryMap[extension.id] = info
    }

    /**
     * Atomically transitions the lifecycle state of an extension, enforcing valid state machine rules.
     */
    fun transitionState(id: String, targetState: ExtensionState): RegisteredExtensionInfo {
        val currentInfo = registryMap[id]
            ?: throw ExtensionError.RegistryError.ExtensionNotFound(id)

        if (!currentInfo.state.canTransitionTo(targetState)) {
            throw ExtensionError.RegistryError.InvalidStateTransition(
                extensionId = id,
                currentState = currentInfo.state.name,
                targetState = targetState.name
            )
        }

        val updatedInfo = currentInfo.copy(
            state = targetState,
            lastStateChangeTimestamp = System.currentTimeMillis()
        )

        registryMap[id] = updatedInfo
        return updatedInfo
    }

    /**
     * Unregisters an extension from the canonical registry.
     */
    fun unregister(id: String) {
        registryMap.remove(id)
    }

    fun getEnabledExtensions(): List<ParsedExtension> = getAllActiveExtensions()

    fun registerExtension(extension: ParsedExtension, initialState: ExtensionState = ExtensionState.INSTALLED_ENABLED) {
        register(extension, initialState)
    }

    /**
     * Retrieves the parsed extension object if registered and active/installed.
     */
    fun getExtension(id: String): ParsedExtension? {
        return registryMap[id]?.extension
    }

    /**
     * Retrieves full registered metadata including state machine information.
     */
    fun getRegisteredInfo(id: String): RegisteredExtensionInfo? {
        return registryMap[id]
    }

    /**
     * Retrieves the current lifecycle state of an extension.
     */
    fun getExtensionState(id: String): ExtensionState {
        return registryMap[id]?.state ?: ExtensionState.UNINSTALLED
    }

    /**
     * Checks if an extension is in an active or enabled lifecycle state.
     */
    fun isExtensionEnabled(id: String): Boolean {
        val state = getExtensionState(id)
        return state == ExtensionState.ACTIVE || state == ExtensionState.INSTALLED_ENABLED
    }

    /**
     * Returns the generation/reload counter for an extension.
     */
    fun getExtensionGeneration(id: String): Int {
        return extensionGenerations[id] ?: 0
    }

    /**
     * Returns all active/enabled extensions.
     */
    fun getAllActiveExtensions(): List<ParsedExtension> {
        return registryMap.values
            .filter { it.state == ExtensionState.ACTIVE || it.state == ExtensionState.INSTALLED_ENABLED }
            .map { it.extension }
    }

    /**
     * Returns all registered extensions regardless of state.
     */
    fun getAllRegisteredExtensions(): List<RegisteredExtensionInfo> {
        return registryMap.values.toList()
    }

    /**
     * Clears all registrations.
     */
    fun clear() {
        registryMap.clear()
    }
}
