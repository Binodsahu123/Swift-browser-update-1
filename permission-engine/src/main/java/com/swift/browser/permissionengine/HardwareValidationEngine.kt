package com.swift.browser.permissionengine

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

object HardwareValidationEngine {
    fun validateHardware(context: Context, permissionType: String): Boolean {
        val descriptor = PermissionDescriptorRegistry.getDescriptor(permissionType)
        if (descriptor != null) {
            return validateHardwareForDescriptor(context, descriptor)
        }

        val pm = context.packageManager
        if (permissionType.contains("CAMERA") && permissionType.contains("MICROPHONE")) {
            val hasCam = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            val hasMic = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
            return hasCam && hasMic
        }
        
        return when (permissionType.uppercase()) {
            "CAMERA" -> pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            "MICROPHONE" -> pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
            "LOCATION" -> {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val gpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
                val networkEnabled = lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
                gpsEnabled || networkEnabled
            }
            else -> true
        }
    }

    fun validateHardwareForDescriptor(context: Context, descriptor: PermissionDescriptor): Boolean {
        if (!descriptor.requiresHardware) return true
        val pm = context.packageManager

        if (descriptor.hardwareRequirements.isNotEmpty()) {
            for (feature in descriptor.hardwareRequirements) {
                val hasFeature = pm.hasSystemFeature(feature)
                if (!hasFeature) {
                    PermissionLogger.logFailure("hardware_check", descriptor.permissionType, "Missing system feature $feature")
                    return false
                }
            }
        } else if (descriptor.hardwareFeature != null) {
            val hasFeature = pm.hasSystemFeature(descriptor.hardwareFeature)
            if (!hasFeature) {
                PermissionLogger.logFailure("hardware_check", descriptor.permissionType, "Missing system feature ${descriptor.hardwareFeature}")
                return false
            }
        }

        if (descriptor.permissionType == "LOCATION") {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val gpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            val networkEnabled = lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            val hasLocation = gpsEnabled || networkEnabled
            if (!hasLocation) {
                PermissionLogger.logFailure("hardware_check", "LOCATION", "No location providers are currently enabled.")
            }
            return hasLocation
        }

        return true
    }
}

