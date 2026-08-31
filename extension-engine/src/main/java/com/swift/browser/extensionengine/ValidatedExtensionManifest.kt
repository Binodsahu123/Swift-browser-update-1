package com.swift.browser.extensionengine

data class ExternallyConnectableSpec(
    val matches: List<String> = emptyList(),
    val ids: List<String> = emptyList(),
    val acceptsTlsChannelId: Boolean = false
)

data class ValidatedExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val manifestVersion: Int,
    val permissions: List<String>,
    val hostPermissions: List<String>,
    val optionalPermissions: List<String>,
    val optionalHostPermissions: List<String> = emptyList(),
    val backgroundSpec: BackgroundSpec,
    val actionSpec: ActionSpec,
    val contentScripts: List<ContentScriptSpec>,
    val optionsPage: String,
    val webAccessibleResources: List<WebAccessibleResourceSpec>,
    val contentSecurityPolicy: ContentSecurityPolicySpec,
    val externallyConnectable: ExternallyConnectableSpec,
    val key: String?,
    val defaultLocale: String,
    val minimumChromeVersion: String,
    val allowedInPrivate: Boolean,
    val rawJson: String,
    val sidePanelPath: String = "",
    val devtoolsPagePath: String = "",
    val optionsInTab: Boolean = false,
    val urlOverrides: Map<String, String> = emptyMap()
) {
    fun toParsedExtension(
        identity: ExtensionIdentity,
        installPath: String = "",
        iconPath: String = "",
        popupPath: String = "",
        backgroundPath: String = "",
        manifestPath: String = "",
        isEnabled: Boolean = true
    ): ParsedExtension {
        return ParsedExtension(
            id = id,
            name = name,
            version = version,
            description = description,
            manifestVersion = manifestVersion,
            permissions = permissions,
            hostPermissions = hostPermissions,
            backgroundScripts = backgroundSpec.scripts.ifEmpty {
                if (backgroundSpec.serviceWorker.isNotBlank()) listOf(backgroundSpec.serviceWorker) else emptyList()
            },
            isServiceWorker = backgroundSpec.serviceWorker.isNotBlank(),
            contentScripts = contentScripts,
            actionPopup = actionSpec.defaultPopup,
            optionsPage = optionsPage,
            manifestJson = rawJson,
            shortName = name,
            iconPath = iconPath,
            installPath = installPath,
            popupPath = popupPath.ifBlank { actionSpec.defaultPopup },
            manifestPath = manifestPath,
            backgroundPath = backgroundPath.ifBlank {
                if (backgroundSpec.serviceWorker.isNotBlank()) backgroundSpec.serviceWorker else backgroundSpec.scripts.firstOrNull() ?: ""
            },
            isEnabled = isEnabled,
            allowedInPrivate = allowedInPrivate,
            identity = identity,
            key = key,
            defaultLocale = defaultLocale,
            minimumChromeVersion = minimumChromeVersion,
            actionSpec = actionSpec,
            backgroundSpec = backgroundSpec,
            webAccessibleResources = webAccessibleResources,
            contentSecurityPolicy = contentSecurityPolicy,
            optionalPermissions = optionalPermissions,
            optionalHostPermissions = optionalHostPermissions,
            externallyConnectable = externallyConnectable,
            sidePanelPath = sidePanelPath,
            devtoolsPagePath = devtoolsPagePath,
            optionsInTab = optionsInTab,
            urlOverrides = urlOverrides
        )
    }
}
