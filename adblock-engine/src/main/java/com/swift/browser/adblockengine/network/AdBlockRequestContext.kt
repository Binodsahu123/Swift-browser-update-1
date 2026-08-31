package com.swift.browser.adblockengine.network

/**
 * Encapsulates full metadata needed to accurately process rules on an active request.
 */
data class AdBlockRequestContext(
    val url: String,
    val documentUrl: String?,
    val isThirdParty: Boolean,
    val resourceType: String
)
