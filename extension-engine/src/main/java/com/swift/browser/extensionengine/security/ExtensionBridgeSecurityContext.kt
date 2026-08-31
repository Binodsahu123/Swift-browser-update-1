package com.swift.browser.extensionengine.security

import com.swift.browser.extensionengine.origin.ExtensionOrigin
import com.swift.browser.extensionengine.origin.ExtensionUrl

/**
 * Security Context tracking the origin, capabilities, and execution boundaries for a privileged extension WebView/Bridge.
 */
data class ExtensionBridgeSecurityContext(
    val extensionId: String,
    val contextType: ExtensionPageType = ExtensionPageType.EXTENSION_PAGE,
    val physicalOrigin: String = "${ExtensionOrigin.SCHEME_CHROME_EXTENSION}://$extensionId/",
    val logicalOrigin: String = "${ExtensionOrigin.SCHEME_CHROME_EXTENSION}://$extensionId/",
    val privateSessionId: String? = null,
    val webViewInstanceId: String = "",
    val enabled: Boolean = true,
    val installationState: String = "INSTALLED",
    val isPrivate: Boolean = privateSessionId != null
) {
    val isPrivileged: Boolean
        get() = enabled && contextType.isPrivilegedContext && !contextType.isSandboxContext && !contextType.isWebContext

    val isSandbox: Boolean
        get() = contextType.isSandboxContext

    val isValidOrigin: Boolean
        get() = ExtensionUrl.getExtensionId(logicalOrigin)?.equals(extensionId, ignoreCase = true) == true
}
