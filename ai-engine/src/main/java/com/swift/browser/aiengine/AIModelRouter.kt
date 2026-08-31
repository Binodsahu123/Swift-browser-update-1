package com.swift.browser.aiengine

object AIModelRouter {
    data class RouterConfig(
        val endpointUrl: String,
        val apiKeyHeaderName: String,
        val modelParameterName: String,
        val resolvedModelName: String,
        val useBearerToken: Boolean = true
    )

    fun resolveRoute(provider: String, selectedModel: String, settingsManager: AISettingsManager): RouterConfig {
        return when (provider) {
            "Gemini" -> {
                val model = if (selectedModel == "Default" || selectedModel.isEmpty()) "gemini-3.5-flash" else selectedModel
                RouterConfig(
                    endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                    apiKeyHeaderName = "x-goog-api-key",
                    modelParameterName = "model",
                    resolvedModelName = model,
                    useBearerToken = false
                )
            }
            "ChatGPT" -> {
                val model = if (selectedModel == "Default" || selectedModel.isEmpty()) "gpt-4o-mini" else selectedModel
                RouterConfig(
                    endpointUrl = "https://api.openai.com/v1/chat/completions",
                    apiKeyHeaderName = "Authorization",
                    modelParameterName = "model",
                    resolvedModelName = model,
                    useBearerToken = true
                )
            }
            "Claude" -> {
                val model = if (selectedModel == "Default" || selectedModel.isEmpty()) "claude-3-5-sonnet-latest" else selectedModel
                RouterConfig(
                    endpointUrl = "https://api.anthropic.com/v1/messages",
                    apiKeyHeaderName = "x-api-key",
                    modelParameterName = "model",
                    resolvedModelName = model,
                    useBearerToken = false
                )
            }
            "DeepSeek" -> {
                val model = if (selectedModel == "Default" || selectedModel.isEmpty()) "deepseek-chat" else selectedModel
                RouterConfig(
                    endpointUrl = "https://api.deepseek.com/v1/chat/completions",
                    apiKeyHeaderName = "Authorization",
                    modelParameterName = "model",
                    resolvedModelName = model,
                    useBearerToken = true
                )
            }
            "Qwen" -> {
                val model = if (selectedModel == "Default" || selectedModel.isEmpty()) "qwen-plus" else selectedModel
                RouterConfig(
                    endpointUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
                    apiKeyHeaderName = "Authorization",
                    modelParameterName = "model",
                    resolvedModelName = model,
                    useBearerToken = true
                )
            }
            "Mistral" -> {
                val model = if (selectedModel == "Default" || selectedModel.isEmpty()) "mistral-small-latest" else selectedModel
                RouterConfig(
                    endpointUrl = "https://api.mistral.ai/v1/chat/completions",
                    apiKeyHeaderName = "Authorization",
                    modelParameterName = "model",
                    resolvedModelName = model,
                    useBearerToken = true
                )
            }
            "Llama" -> {
                val model = if (selectedModel == "Default" || selectedModel.isEmpty()) "llama-3-8b-instruct" else selectedModel
                RouterConfig(
                    endpointUrl = "https://api.together.xyz/v1/chat/completions",
                    apiKeyHeaderName = "Authorization",
                    modelParameterName = "model",
                    resolvedModelName = model,
                    useBearerToken = true
                )
            }
            "OpenRouter" -> {
                val model = if (selectedModel == "Default" || selectedModel.isEmpty()) "google/gemini-2.5-flash" else selectedModel
                RouterConfig(
                    endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                    apiKeyHeaderName = "Authorization",
                    modelParameterName = "model",
                    resolvedModelName = model,
                    useBearerToken = true
                )
            }
            else -> {
                RouterConfig(
                    endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
                    apiKeyHeaderName = "x-goog-api-key",
                    modelParameterName = "model",
                    resolvedModelName = "gemini-3.5-flash",
                    useBearerToken = false
                )
            }
        }
    }
}
