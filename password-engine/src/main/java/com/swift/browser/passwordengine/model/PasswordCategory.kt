package com.swift.browser.passwordengine.model

enum class PasswordCategory(val displayName: String) {
    GENERAL("General"),
    SOCIAL("Social Media"),
    WORK("Work & Business"),
    FINANCE("Banking & Finance"),
    PERSONAL("Personal"),
    ENTERTAINMENT("Streaming & Media"),
    SHOPPING("Shopping & E-commerce");

    companion object {
        fun fromString(value: String): PasswordCategory {
            return entries.find { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) }
                ?: GENERAL
        }
    }
}
