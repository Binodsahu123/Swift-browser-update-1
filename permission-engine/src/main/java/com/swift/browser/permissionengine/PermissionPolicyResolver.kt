package com.swift.browser.permissionengine

object PermissionPolicyResolver {
    var originEligibility: PermissionOriginEligibility = StandardPermissionOriginEligibility()

    fun isSecureOrigin(origin: String): Boolean {
        return originEligibility.evaluate(origin, "") == OriginEligibilityResult.ALLOWED
    }

    fun evaluateOriginEligibility(origin: String, permissionType: String): OriginEligibilityResult {
        return originEligibility.evaluate(origin, permissionType)
    }

    fun isAutoGrantResource(resource: String): Boolean {
        return false // Do NOT blindly auto-grant protected media
    }

    fun mapResourceToPermissionType(resource: String): String {
        return PermissionDescriptorRegistry.mapResourceToPermissionType(resource)
    }
}
