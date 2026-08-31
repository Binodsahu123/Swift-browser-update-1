package com.swift.browser.aiengine

data class AIProvider(
    val name: String,
    val displayName: String,
    val isGuestSupported: Boolean,
    val isLoginRequiredByDefault: Boolean,
    val models: List<String>
)

object AIProviderManager {
    val providers = listOf(
        AIProvider(
            name = "Gemini",
            displayName = "Gemini (Google)",
            isGuestSupported = true,
            isLoginRequiredByDefault = false,
            models = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-3.1-flash-lite", "gemini-2.1-flash", "gemini-2.5-flash-image")
        ),
        AIProvider(
            name = "ChatGPT",
            displayName = "ChatGPT (OpenAI)",
            isGuestSupported = false,
            isLoginRequiredByDefault = true,
            models = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")
        ),
        AIProvider(
            name = "Claude",
            displayName = "Claude (Anthropic)",
            isGuestSupported = false,
            isLoginRequiredByDefault = true,
            models = listOf("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest", "claude-3-opus")
        ),
        AIProvider(
            name = "DeepSeek",
            displayName = "DeepSeek AI",
            isGuestSupported = false,
            isLoginRequiredByDefault = true,
            models = listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner")
        ),
        AIProvider(
            name = "Qwen",
            displayName = "Qwen (Alibaba)",
            isGuestSupported = true,
            isLoginRequiredByDefault = false,
            models = listOf("qwen-turbo", "qwen-plus", "qwen-max")
        ),
        AIProvider(
            name = "Mistral",
            displayName = "Mistral AI",
            isGuestSupported = true,
            isLoginRequiredByDefault = false,
            models = listOf("mistral-large-latest", "mistral-medium-latest", "mistral-small-latest")
        ),
        AIProvider(
            name = "Grok",
            displayName = "Grok (xAI)",
            isGuestSupported = true,
            isLoginRequiredByDefault = false,
            models = listOf("grok-2", "grok-beta")
        ),
        AIProvider(
            name = "Custom Provider",
            displayName = "Custom Provider",
            isGuestSupported = true,
            isLoginRequiredByDefault = false,
            models = listOf("custom-model-1", "custom-model-2")
        )
    )

    fun getProvider(name: String): AIProvider? {
        return providers.find { it.name.equals(name, ignoreCase = true) }
    }

    fun getModelsForProvider(provider: String): List<String> {
        return getProvider(provider)?.models ?: listOf("Default")
    }

    fun getProvidersList(): List<String> {
        return providers.map { it.name }
    }
}
