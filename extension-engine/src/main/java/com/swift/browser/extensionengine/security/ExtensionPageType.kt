package com.swift.browser.extensionengine.security

/**
 * Categorization of execution contexts in the extension architecture.
 */
enum class ExtensionPageType {
    BACKGROUND_PAGE,
    POPUP_PAGE,
    OPTIONS_PAGE,
    SIDE_PANEL_PAGE,
    EXTENSION_PAGE,
    SANDBOX_PAGE,
    CONTENT_SCRIPT,
    WEB_PAGE;

    val isPrivilegedContext: Boolean
        get() = this in listOf(
            BACKGROUND_PAGE,
            POPUP_PAGE,
            OPTIONS_PAGE,
            SIDE_PANEL_PAGE,
            EXTENSION_PAGE
        )

    val isSandboxContext: Boolean
        get() = this == SANDBOX_PAGE

    val isContentScriptContext: Boolean
        get() = this == CONTENT_SCRIPT

    val isWebContext: Boolean
        get() = this == WEB_PAGE
}
