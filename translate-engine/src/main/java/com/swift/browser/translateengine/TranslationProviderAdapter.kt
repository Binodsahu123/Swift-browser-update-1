package com.swift.browser.translateengine

interface TranslationProvider {
    val name: String
    suspend fun translate(text: String, srcLang: String, targetLang: String): String
    suspend fun translateBatch(texts: List<String>, srcLang: String, targetLang: String): Map<String, String>
}

sealed class TranslationProviderType {
    object GoogleTranslate : TranslationProviderType()
    object LibreTranslate : TranslationProviderType()
    object MicrosoftTranslator : TranslationProviderType()
    object ArgosTranslate : TranslationProviderType()
    object GeminiAI : TranslationProviderType()
}
