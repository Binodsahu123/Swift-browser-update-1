package com.swift.browser.webstudio.model

data class WebStudioError(
    val code: Int = 0,
    val message: String,
    val throwable: Throwable? = null
)
