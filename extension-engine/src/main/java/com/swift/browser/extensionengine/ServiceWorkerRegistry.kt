package com.swift.browser.extensionengine

import java.util.concurrent.ConcurrentHashMap

/**
 * Registry maintaining declared MV3 service worker registrations and tracking their state and generation.
 */
class ServiceWorkerRegistry(
    private var context: android.content.Context? = null,
    private var extensionRegistry: ExtensionRegistry? = null
) {
    // Secondary constructor to maintain compatibility with existing instantiations if any pass permissionManager
    constructor(
        context: android.content.Context?,
        extensionRegistry: ExtensionRegistry?,
        permissionManager: Any?
    ) : this(context, extensionRegistry)

    private val workers = ConcurrentHashMap<String, ServiceWorkerRegistration>()

    fun setContextAndRegistry(
        context: android.content.Context,
        extensionRegistry: ExtensionRegistry
    ) {
        this.context = context
        this.extensionRegistry = extensionRegistry
    }

    fun setContextAndRegistry(
        context: android.content.Context,
        extensionRegistry: ExtensionRegistry,
        permissionManager: Any?
    ) {
        this.context = context
        this.extensionRegistry = extensionRegistry
    }

    fun register(extensionId: String, scriptPath: String, isMV3: Boolean = true): ServiceWorkerRegistration {
        val extRegistry = extensionRegistry
        val ctx = context

        if (extRegistry != null) {
            val ext = extRegistry.getExtension(extensionId) ?: throw IllegalArgumentException("SERVICE_WORKER_REGISTRATION_FAILED")
            if (ext.manifestVersion != 3 && isMV3) {
                throw IllegalArgumentException("SERVICE_WORKER_REGISTRATION_FAILED")
            }
            if (scriptPath.isBlank()) {
                throw IllegalArgumentException("SERVICE_WORKER_REGISTRATION_FAILED")
            }
            if (!PathSanitizer.isSafePath(scriptPath)) {
                throw IllegalArgumentException("SERVICE_WORKER_REGISTRATION_FAILED")
            }
            if (!extRegistry.isExtensionEnabled(extensionId)) {
                throw IllegalArgumentException("SERVICE_WORKER_REGISTRATION_FAILED")
            }
            if (ctx != null) {
                val extensionDir = ExtensionDirectoryResolver.getExtensionDir(ctx, extensionId, ext.name)
                val targetFile = java.io.File(extensionDir, scriptPath)
                if (!PathSanitizer.verifyCanonicalContainment(extensionDir, targetFile.path)) {
                    throw IllegalArgumentException("SERVICE_WORKER_REGISTRATION_FAILED")
                }
                // Check if file exists
                if (!targetFile.exists()) {
                    throw IllegalArgumentException("SERVICE_WORKER_REGISTRATION_FAILED")
                }
            }
        } else {
            // Fallback basic checks for un-configured registry tests
            if (scriptPath.isBlank() || !PathSanitizer.isSafePath(scriptPath)) {
                throw IllegalArgumentException("SERVICE_WORKER_REGISTRATION_FAILED")
            }
        }

        val existing = workers[extensionId]
        val currentGen = (existing?.workerGenerationId ?: 0) + 1

        val ext = extRegistry?.getExtension(extensionId)
        val reg = ServiceWorkerRegistration(
            extensionId = extensionId,
            scriptPath = scriptPath,
            state = ServiceWorkerState.REGISTERED,
            isMV3 = isMV3,
            type = ext?.backgroundSpec?.type?.ifBlank { "classic" } ?: "classic",
            enabled = extRegistry?.isExtensionEnabled(extensionId) ?: true,
            privateAllowed = ext?.allowedInPrivate ?: false,
            workerGenerationId = currentGen,
            registeredAt = System.currentTimeMillis()
        )
        workers[extensionId] = reg
        return reg
    }

    fun unregister(extensionId: String): ServiceWorkerRegistration? {
        return workers.remove(extensionId)
    }

    fun getWorker(extensionId: String): ServiceWorkerRegistration? {
        return workers[extensionId]
    }

    fun getWorkerGeneration(extensionId: String): Int {
        return workers[extensionId]?.workerGenerationId ?: 0
    }

    fun incrementWorkerGeneration(extensionId: String): Int {
        val worker = workers[extensionId] ?: return 0
        worker.workerGenerationId += 1
        return worker.workerGenerationId
    }

    fun transitionState(extensionId: String, targetState: ServiceWorkerState): Boolean {
        val worker = workers[extensionId] ?: return false
        if (!worker.state.canTransitionTo(targetState)) {
            return false
        }
        worker.state = targetState
        if (targetState == ServiceWorkerState.ACTIVE || targetState == ServiceWorkerState.EVENT || targetState == ServiceWorkerState.RUNNING) {
            worker.lastActiveTimestamp = System.currentTimeMillis()
        }
        if (targetState == ServiceWorkerState.STOPPED || targetState == ServiceWorkerState.SUSPENDED || targetState == ServiceWorkerState.DORMANT) {
            worker.lastStopAt = System.currentTimeMillis()
        }
        return true
    }

    fun getState(extensionId: String): ServiceWorkerState {
        return workers[extensionId]?.state ?: ServiceWorkerState.NOT_REGISTERED
    }

    fun getAllWorkers(): List<ServiceWorkerRegistration> {
        return workers.values.toList()
    }

    fun isRegistered(extensionId: String): Boolean {
        return workers.containsKey(extensionId)
    }

    fun clear() {
        workers.clear()
    }
}

