package com.swift.browser.adblockengine.core

/**
 * Enumerates potential outcome decisions returned by the AdBlock Rule Engine.
 */
enum class AdBlockRequestDecision {
    ALLOW,
    BLOCK,
    HIDE,
    REDIRECT,
    ALLOW_WITH_EXCEPTION
}
