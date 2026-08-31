package com.swift.browser.extensionengine

typealias ExtensionInstallState = ExtensionState

enum class ExtensionState {
    UNINSTALLED,
    STAGED,
    VALIDATED,
    INSTALLED_DISABLED,
    INSTALLED_ENABLED,
    ACTIVE,
    ERROR;

    fun canTransitionTo(target: ExtensionState): Boolean {
        if (this == target) return true
        if (target == ERROR || target == UNINSTALLED) return true

        return when (this) {
            UNINSTALLED -> target == STAGED
            STAGED -> target == VALIDATED
            VALIDATED -> target == INSTALLED_ENABLED || target == INSTALLED_DISABLED
            INSTALLED_DISABLED -> target == INSTALLED_ENABLED || target == ACTIVE
            INSTALLED_ENABLED -> target == INSTALLED_DISABLED || target == ACTIVE
            ACTIVE -> target == INSTALLED_ENABLED || target == INSTALLED_DISABLED
            ERROR -> target == STAGED || target == UNINSTALLED
        }
    }
}

data class RegisteredExtensionInfo(
    val extension: ParsedExtension,
    val state: ExtensionState,
    val generation: Int = 1,
    val installedTimestamp: Long = System.currentTimeMillis(),
    val lastStateChangeTimestamp: Long = System.currentTimeMillis()
)
