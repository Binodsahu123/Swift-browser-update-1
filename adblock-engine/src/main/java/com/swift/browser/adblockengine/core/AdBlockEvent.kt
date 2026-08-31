package com.swift.browser.adblockengine.core

/**
 * Declares event wrappers for dispatching adblock status signals inside the subsystem.
 */
sealed class AdBlockEvent {
    object FilterListUpdated : AdBlockEvent()
    data class RequestBlocked(val url: String, val category: String) : AdBlockEvent()
    data class RequestAllowed(val url: String, val reason: String) : AdBlockEvent()
    data class ElementHidden(val selector: String) : AdBlockEvent()
    data class WhitelistChanged(val domain: String, val isAdded: Boolean) : AdBlockEvent()
    data class UpdateFailed(val error: String) : AdBlockEvent()
    object DiagnosticsRequested : AdBlockEvent()
}
