package com.swift.browser.extensionengine

import android.content.Context
import android.speech.tts.TextToSpeech
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ExtensionTtsAdapter(
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private fun validate(sender: ExtensionSender): ParsedExtension {
        val extId = sender.extensionId.lowercase().trim()
        val ext = registry.getExtension(extId) ?: throw SecurityException("Extension $extId not found")
        if (!registry.isExtensionEnabled(extId)) {
            throw SecurityException("Extension $extId is disabled")
        }
        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(extId)) {
            throw SecurityException("Extension $extId is not allowed in private mode")
        }
        if (!permissionManager.hasApiPermission(extId, ext.permissions, "tts")) {
            throw SecurityException("SecurityError: Extension does not have 'tts' permission")
        }
        return ext
    }

    private fun ensureTts(context: Context?) {
        if (tts == null && context != null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
        }
    }

    fun speak(sender: ExtensionSender, utterance: String, options: JSONObject, context: Context? = null): JSONObject {
        validate(sender)
        ensureTts(context)

        val rate = options.optDouble("rate", 1.0).toFloat()
        val pitch = options.optDouble("pitch", 1.0).toFloat()
        val lang = options.optString("lang", "en-US")

        tts?.let { engine ->
            engine.setSpeechRate(rate)
            engine.setPitch(pitch)
            if (lang.isNotBlank()) {
                val parts = lang.split("-")
                val locale = if (parts.size > 1) Locale(parts[0], parts[1]) else Locale(parts[0])
                engine.setLanguage(locale)
            }
            engine.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, "ext_utterance_${System.currentTimeMillis()}")
        }

        return JSONObject().put("status", "speaking").put("utterance", utterance)
    }

    fun stop(sender: ExtensionSender): JSONObject {
        validate(sender)
        tts?.stop()
        return JSONObject().put("status", "stopped")
    }

    fun pause(sender: ExtensionSender): JSONObject {
        validate(sender)
        tts?.stop()
        return JSONObject().put("status", "paused")
    }

    fun resume(sender: ExtensionSender): JSONObject {
        validate(sender)
        return JSONObject().put("status", "resumed")
    }

    fun isSpeaking(sender: ExtensionSender): JSONObject {
        validate(sender)
        val speaking = tts?.isSpeaking == true
        return JSONObject().put("speaking", speaking)
    }

    fun getVoices(sender: ExtensionSender): JSONArray {
        validate(sender)
        val result = JSONArray()
        tts?.voices?.forEach { v ->
            result.put(JSONObject().apply {
                put("voiceName", v.name)
                put("lang", v.locale.toLanguageTag())
                put("remote", v.isNetworkConnectionRequired)
            })
        }
        return result
    }
}
