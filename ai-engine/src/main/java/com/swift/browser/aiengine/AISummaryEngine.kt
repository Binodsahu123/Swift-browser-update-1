package com.swift.browser.aiengine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AISummaryEngine {
    private const val TAG = "AISummaryEngine"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

    private fun md5(input: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    suspend fun analyzePage(
        pageText: String,
        settingsManager: AISettingsManager,
        context: Context
    ): String {
        AICacheEngine.initialize(context)
        val contentHash = md5(pageText)
        val cacheKey = "hash_${contentHash}_${settingsManager.defaultProvider}_${settingsManager.responseLength}"
        val cached = AICacheEngine.getCachedResponse(cacheKey)
        if (cached != null) {
            Log.i(TAG, "Cached summary hit for MD5: $contentHash")
            return cached.responseText
        }
        val result = analyzePageRaw(pageText, settingsManager, context)
        if (!result.contains("Failover Completed") && !result.contains("We were unable to complete the analysis report")) {
            AICacheEngine.cacheResponse(cacheKey, result, settingsManager.defaultProvider)
        }
        return result
    }

    suspend fun analyzePageRaw(
        pageText: String,
        settingsManager: AISettingsManager,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        var currentProvider = settingsManager.defaultProvider
        val isGemini = currentProvider.equals("Gemini", ignoreCase = true)
        val hasGeminiApiKey = isGemini && settingsManager.getApiKey("Gemini").isNotEmpty()

        // Integrated AI Website Bridge Mode for ChatGPT and Gemini (only if Gemini has no API key)
        if (currentProvider.equals("ChatGPT", ignoreCase = true) || (isGemini && !hasGeminiApiKey)) {
            val contextObj = try {
                FastAIExtractor.parseJsonToContext(pageText)
            } catch (e: Exception) {
                FastPageContext(paragraphs = listOf(pageText))
            }

            // Detect language of the website
            val sampleTextForLanguageDetection = if (contextObj.paragraphs.isNotEmpty()) {
                contextObj.paragraphs.joinToString(" ").take(400)
            } else {
                pageText.take(400)
            }
            
            val detectedCode = try {
                com.swift.browser.translateengine.LanguageDetector().detectLanguage(sampleTextForLanguageDetection)
            } catch (e: Exception) {
                "en"
            }

            val detectedLanguageName = when (detectedCode.lowercase().substringBefore("-")) {
                "en" -> "English"
                "hi" -> "Hindi"
                "ta" -> "Tamil"
                "te" -> "Telugu"
                "bn" -> "Bengali"
                "es" -> "Spanish"
                "fr" -> "French"
                "de" -> "German"
                "ja" -> "Japanese"
                "zh" -> "Chinese"
                "ar" -> "Arabic"
                else -> "English"
            }

            val preferredLanguage = settingsManager.preferredLanguage
            val responseLength = settingsManager.responseLength
            val prompt = PageAnalyzer.prepareAnalysisPrompt(contextObj, preferredLanguage, detectedLanguageName, responseLength)
            
            try {
                val bridgeResult = AIWebsiteBridgeSystem.getInstance(context).executePrompt(currentProvider, prompt)
                return@withContext bridgeResult
            } catch (e: Exception) {
                Log.e(TAG, "Bridge execution for $currentProvider failed, trying fallback...", e)
            }
        }

        var attempts = 0
        val maxAttempts = 3
        var lastErrorMessage = ""

        while (attempts < maxAttempts) {
            attempts++
            val selectedModel = if (currentProvider.equals(settingsManager.defaultProvider, ignoreCase = true)) {
                settingsManager.defaultModel
            } else {
                "Default"
            }
            val preferredLanguage = settingsManager.preferredLanguage
            val responseLength = settingsManager.responseLength
            
            val guestModeManager = AIGuestModeManager(context)
            val accountManager = AIAccountManager(context)
            val apiKey = settingsManager.getApiKey(currentProvider)
            
            // Check access clearance (guest permissions or login validation)
            if (apiKey.isEmpty() && 
                !currentProvider.equals("Gemini", ignoreCase = true) && 
                !guestModeManager.isGuestAllowed(currentProvider) && 
                !accountManager.isLoggedIn(currentProvider)
            ) {
                lastErrorMessage = "Credentials or authentication required for provider '$currentProvider'"
                Log.w(TAG, "$lastErrorMessage. Retrying fallback router...")
                val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                    currentProvider = next
                    continue
                } else {
                    break
                }
            }

            val contextObj = try {
                FastAIExtractor.parseJsonToContext(pageText)
            } catch (e: Exception) {
                FastPageContext(paragraphs = listOf(pageText))
            }

            // Detect language of the website
            val sampleTextForLanguageDetection = if (contextObj.paragraphs.isNotEmpty()) {
                contextObj.paragraphs.joinToString(" ").take(400)
            } else {
                pageText.take(400)
            }
            
            val detectedCode = try {
                com.swift.browser.translateengine.LanguageDetector().detectLanguage(sampleTextForLanguageDetection)
            } catch (e: Exception) {
                "en"
            }

            val detectedLanguageName = when (detectedCode.lowercase().substringBefore("-")) {
                "en" -> "English"
                "hi" -> "Hindi"
                "ta" -> "Tamil"
                "te" -> "Telugu"
                "bn" -> "Bengali"
                "es" -> "Spanish"
                "fr" -> "French"
                "de" -> "German"
                "ja" -> "Japanese"
                "zh" -> "Chinese"
                "ar" -> "Arabic"
                else -> "English"
            }

            val prompt = PageAnalyzer.prepareAnalysisPrompt(contextObj, preferredLanguage, detectedLanguageName, responseLength)
            val route = AIModelRouter.resolveRoute(currentProvider, selectedModel, settingsManager)

            try {
                val jsonRequest = buildRequestBody(currentProvider, route.resolvedModelName, prompt, settingsManager)
                val requestBuilder = Request.Builder()
                    .url(route.endpointUrl)
                    .post(jsonRequest.toString().toRequestBody(MEDIA_TYPE_JSON))

                if (currentProvider.equals("Gemini", ignoreCase = true)) {
                    requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
                } else {
                    if (route.useBearerToken) {
                        requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                    } else {
                        requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
                    }
                }

                if (currentProvider.equals("Claude", ignoreCase = true)) {
                    requestBuilder.addHeader("anthropic-version", "2023-06-01")
                }

                val request = requestBuilder.build()
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        lastErrorMessage = "HTTP ${response.code} from $currentProvider: $bodyString"
                        Log.e(TAG, "Dynamic execution failed: $lastErrorMessage")
                        
                        val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                        if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                            currentProvider = next
                            // trigger next iteration in loop
                        } else {
                            return@withContext parseResponse(currentProvider, bodyString)
                        }
                    } else {
                        return@withContext parseResponse(currentProvider, bodyString)
                    }
                }
            } catch (e: Exception) {
                lastErrorMessage = e.localizedMessage ?: "Timeout connecting to $currentProvider"
                Log.e(TAG, "Network exception calling $currentProvider: $lastErrorMessage")
                
                val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                    currentProvider = next
                    continue
                } else {
                    break
                }
            }
        }

        return@withContext """
            [MAIN TOPIC]
            Failover Completed
            
            [SUMMARY]
            We were unable to complete the analysis report because the selected AI provider failed and no fallback alternative was active or configured.
            
            Details: $lastErrorMessage
            
            Please verify your network connectivity or API settings inside the Browser's AI Settings.
        """.trimIndent()
    }

    suspend fun chatSession(
        chatHistory: List<Pair<String, String>>,
        newMessage: String,
        settingsManager: AISettingsManager,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        var currentProvider = settingsManager.defaultProvider

        val isGemini = currentProvider.equals("Gemini", ignoreCase = true)
        val hasGeminiApiKey = isGemini && settingsManager.getApiKey("Gemini").isNotEmpty()

        // Integrated AI Website Bridge Mode for ChatGPT and Gemini Chat Session (only if Gemini has no API key)
        if (currentProvider.equals("ChatGPT", ignoreCase = true) || (isGemini && !hasGeminiApiKey)) {
            try {
                val bridgeResult = AIWebsiteBridgeSystem.getInstance(context).executePrompt(currentProvider, newMessage)
                return@withContext bridgeResult
            } catch (e: Exception) {
                Log.e(TAG, "Bridge chat session for $currentProvider failed, trying fallback...", e)
            }
        }

        var attempts = 0
        val maxAttempts = 3
        var lastErrorMessage = ""

        while (attempts < maxAttempts) {
            attempts++
            val selectedModel = if (currentProvider.equals(settingsManager.defaultProvider, ignoreCase = true)) {
                settingsManager.defaultModel
            } else {
                "Default"
            }
            
            val guestModeManager = AIGuestModeManager(context)
            val accountManager = AIAccountManager(context)
            val apiKey = settingsManager.getApiKey(currentProvider)
            
            if (apiKey.isEmpty() && 
                !currentProvider.equals("Gemini", ignoreCase = true) && 
                !guestModeManager.isGuestAllowed(currentProvider) && 
                !accountManager.isLoggedIn(currentProvider)
            ) {
                lastErrorMessage = "Authentication credentials required for provider '$currentProvider'"
                val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                    currentProvider = next
                    continue
                } else {
                    break
                }
            }

            val route = AIModelRouter.resolveRoute(currentProvider, selectedModel, settingsManager)

            try {
                val jsonRequest = buildChatRequestBody(currentProvider, route.resolvedModelName, chatHistory, newMessage, settingsManager)
                val requestBuilder = Request.Builder()
                    .url(route.endpointUrl)
                    .post(jsonRequest.toString().toRequestBody(MEDIA_TYPE_JSON))

                if (currentProvider.equals("Gemini", ignoreCase = true)) {
                    requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
                } else {
                    if (route.useBearerToken) {
                        requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                    } else {
                        requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
                    }
                }

                if (currentProvider.equals("Claude", ignoreCase = true)) {
                    requestBuilder.addHeader("anthropic-version", "2023-06-01")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        lastErrorMessage = "HTTP ${response.code}: $bodyString"
                        val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                        if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                            currentProvider = next
                            // trigger next loop iteration
                        } else {
                            return@withContext "Error: HTTP ${response.code} from $currentProvider"
                        }
                    } else {
                        return@withContext parseResponse(currentProvider, bodyString)
                    }
                }
            } catch (e: Exception) {
                lastErrorMessage = e.localizedMessage ?: "Timeout or response connection lost"
                val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                    currentProvider = next
                    continue
                } else {
                    break
                }
            }
        }

        return@withContext "Failed to fetch response: $lastErrorMessage"
    }

    private fun getSystemInstruction(settingsManager: AISettingsManager): String {
        return when (settingsManager.chatbotRole) {
            "Web Researcher" -> "You are an expert Web Researcher. Help the user analyze webpages and perform deep grounded research. Highlight key findings clearly, reference sources, and provide links where applicable."
            "Hindi Specialist" -> "आप एक अत्यंत मददगार हिंदी सहायक (Hindi Specialist) हैं। उपयोगकर्ता की हर संभव मदद हिंदी भाषा में करें। वेब पेज का अनुवाद या विश्लेषण हिंदी में सुंदर और स्पष्ट रूप से प्रस्तुत करें।"
            "Code Explainer" -> "You are an expert Software Engineer and Technical Code Explainer. Explain coding concepts, snippets, frameworks, and logic clearly with structural explanations and complete example snippets."
            else -> "You are a helpful, versatile browser assistant. Answer accurately, cleanly, and frame ideas elegantly."
        }
    }

    private fun buildRequestBody(provider: String, model: String, prompt: String, settingsManager: AISettingsManager): JSONObject {
        return when (provider) {
            "Gemini" -> {
                val part = JSONObject().put("text", prompt)
                val content = JSONObject().put("parts", JSONArray().put(part))
                val json = JSONObject().put("contents", JSONArray().put(content))

                val systemInstructionText = getSystemInstruction(settingsManager)
                val systemPart = JSONObject().put("text", systemInstructionText)
                val systemInstructionObj = JSONObject().put("parts", JSONArray().put(systemPart))
                json.put("systemInstruction", systemInstructionObj)

                if (settingsManager.searchGroundingEnabled && model.startsWith("gemini-")) {
                    val tool = JSONObject().put("googleSearch", JSONObject())
                    json.put("tools", JSONArray().put(tool))
                }

                json
            }
            "Claude" -> {
                val msg = JSONObject().put("role", "user").put("content", prompt)
                JSONObject()
                    .put("model", model)
                    .put("max_tokens", 4000)
                    .put("messages", JSONArray().put(msg))
            }
            else -> {
                val msg = JSONObject().put("role", "user").put("content", prompt)
                JSONObject()
                    .put("model", model)
                    .put("messages", JSONArray().put(msg))
            }
        }
    }

    private fun buildChatRequestBody(
        provider: String,
        model: String,
        history: List<Pair<String, String>>,
        message: String,
        settingsManager: AISettingsManager
    ): JSONObject {
        return when (provider) {
            "Gemini" -> {
                val contents = JSONArray()
                history.forEach { (sender, text) ->
                    val role = if (sender == "user") "user" else "model"
                    val contentObj = JSONObject()
                        .put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                    contents.put(contentObj)
                }
                val currentObj = JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", message)))
                contents.put(currentObj)

                val json = JSONObject().put("contents", contents)

                val systemInstructionText = getSystemInstruction(settingsManager)
                val systemPart = JSONObject().put("text", systemInstructionText)
                val systemInstructionObj = JSONObject().put("parts", JSONArray().put(systemPart))
                json.put("systemInstruction", systemInstructionObj)

                if (settingsManager.searchGroundingEnabled && model.startsWith("gemini-")) {
                    val tool = JSONObject().put("googleSearch", JSONObject())
                    json.put("tools", JSONArray().put(tool))
                }

                json
            }
            "Claude" -> {
                val messages = JSONArray()
                history.forEach { (sender, text) ->
                    messages.put(JSONObject().put("role", sender).put("content", text))
                }
                messages.put(JSONObject().put("role", "user").put("content", message))
                JSONObject()
                    .put("model", model)
                    .put("max_tokens", 4000)
                    .put("messages", messages)
            }
            else -> {
                val messages = JSONArray()
                history.forEach { (sender, text) ->
                    messages.put(JSONObject().put("role", sender).put("content", text))
                }
                messages.put(JSONObject().put("role", "user").put("content", message))
                JSONObject()
                    .put("model", model)
                    .put("messages", messages)
            }
        }
    }

    private fun parseResponse(provider: String, responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            when (provider) {
                "Gemini" -> {
                    json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                }
                "Claude" -> {
                    json.getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text")
                }
                else -> {
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Response Parsing Error: Unable to read JSON block from provider response.\nRaw Output:\n$responseBody"
        }
    }
}
