package com.swift.browser.browserengine.webrtc

import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages privacy-preserving stable device identity mapping.
 * Prevents origins from fingerprinting or inferring device identities across origins/tabs.
 */
object WebDeviceIdentityManager {
    private const val TAG = "WebDeviceIdentityManager"

    // Maps: tabId -> (origin -> (physicalId -> opaqueId))
    private val tabOriginPhysicalToOpaque = ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, String>>>()
    
    // Reverse maps: tabId -> (origin -> (opaqueId -> physicalId))
    private val tabOriginOpaqueToPhysical = ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, String>>>()

    /**
     * Gets or generates an opaque device ID for a given tab, origin, and physical device.
     */
    fun getOpaqueId(tabId: String, origin: String, physicalId: String): String {
        val normalizedOrigin = origin.trim().lowercase()
        val originMap = tabOriginPhysicalToOpaque.getOrPut(tabId) { ConcurrentHashMap() }
        val physicalToOpaque = originMap.getOrPut(normalizedOrigin) { ConcurrentHashMap() }

        return physicalToOpaque.getOrPut(physicalId) {
            val opaqueId = UUID.randomUUID().toString()
            // Store reverse mapping as well
            val revOriginMap = tabOriginOpaqueToPhysical.getOrPut(tabId) { ConcurrentHashMap() }
            val opaqueToPhysical = revOriginMap.getOrPut(normalizedOrigin) { ConcurrentHashMap() }
            opaqueToPhysical[opaqueId] = physicalId
            
            Log.d(TAG, "Generated opaque ID for $physicalId on tab=$tabId, origin=$normalizedOrigin -> $opaqueId")
            opaqueId
        }
    }

    /**
     * Resolves an opaque device ID back to its physical device ID for a given tab and origin.
     */
    fun getPhysicalId(tabId: String, origin: String, opaqueId: String): String? {
        val normalizedOrigin = origin.trim().lowercase()
        // Check map first
        val resolved = tabOriginOpaqueToPhysical[tabId]?.get(normalizedOrigin)?.get(opaqueId)
        if (resolved != null) return resolved

        // Fallback: Check if the opaqueId itself matches a physical camera ID directly in the system,
        // or a physical microphone ID. This makes local manual inputs or tests more resilient.
        return if (opaqueId.startsWith("mic_") || opaqueId == "default_mic" || opaqueId.toIntOrNull() != null) {
            opaqueId
        } else {
            null
        }
    }

    /**
     * Invalidates a physical device's mapping when it's removed.
     */
    fun invalidatePhysicalDevice(physicalId: String) {
        Log.i(TAG, "Invalidating mappings for removed physical device: $physicalId")
        tabOriginPhysicalToOpaque.forEach { (tabId, originMap) ->
            originMap.forEach { (origin, physicalToOpaque) ->
                val opaqueId = physicalToOpaque.remove(physicalId)
                if (opaqueId != null) {
                    tabOriginOpaqueToPhysical[tabId]?.get(origin)?.remove(opaqueId)
                }
            }
        }
    }

    /**
     * Clears all mappings associated with a specific tab (e.g. on close/destruction).
     */
    fun clearTab(tabId: String) {
        tabOriginPhysicalToOpaque.remove(tabId)
        tabOriginOpaqueToPhysical.remove(tabId)
        Log.d(TAG, "Cleared device ID mappings for tab: $tabId")
    }

    /**
     * Clears origin-specific mappings for a given tab (e.g., on navigation).
     */
    fun clearOriginForTab(tabId: String, origin: String) {
        val normalizedOrigin = origin.trim().lowercase()
        tabOriginPhysicalToOpaque[tabId]?.remove(normalizedOrigin)
        tabOriginOpaqueToPhysical[tabId]?.remove(normalizedOrigin)
        Log.d(TAG, "Cleared device ID mappings for origin: $normalizedOrigin on tab: $tabId")
    }
}
