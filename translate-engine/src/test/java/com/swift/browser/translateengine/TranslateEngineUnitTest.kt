package com.swift.browser.translateengine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateEngineUnitTest {

    @Test
    fun testLanguageDetector() {
        val detector = LanguageDetector()
        
        // Hindi text
        val hindiText = "नमस्ते, आप कैसे हैं?"
        val detectedHindi = detector.detectScriptOffline(hindiText)
        assertEquals("hi", detectedHindi)

        // Bengali text
        val bengaliText = "কেমন আছেন আপনি?"
        val detectedBengali = detector.detectScriptOffline(bengaliText)
        assertEquals("bn", detectedBengali)

        // Arabic text
        val arabicText = "مرحبا كيف حالك؟"
        val detectedArabic = detector.detectScriptOffline(arabicText)
        assertEquals("ar", detectedArabic)

        // Russian / Cyrillic text
        val russianText = "Привет, как дела?"
        val detectedRussian = detector.detectScriptOffline(russianText)
        assertEquals("ru", detectedRussian)
    }

    @Test
    fun testTranslationStateManager() {
        val stateManager = TranslationStateManager()
        assertEquals(TranslationState.Hidden, stateManager.currentState.value)

        stateManager.transitionTo(TranslationState.Visible)
        assertEquals(TranslationState.Visible, stateManager.currentState.value)

        stateManager.transitionTo(TranslationState.Translating)
        assertEquals(TranslationState.Translating, stateManager.currentState.value)

        stateManager.transitionTo(TranslationState.Translated)
        assertEquals(TranslationState.Translated, stateManager.currentState.value)

        stateManager.transitionTo(TranslationState.Original)
        assertEquals(TranslationState.Original, stateManager.currentState.value)
    }

    @Test
    fun testTranslationProgressManager() {
        val progressManager = TranslationProgressManager()
        assertEquals(ProgressState.Idle, progressManager.state.value)

        progressManager.startTranslation(10)
        assertEquals(ProgressState.Translating, progressManager.state.value)
        assertEquals(10, progressManager.totalNodes.value)
        assertEquals(0, progressManager.translatedNodes.value)

        progressManager.updateProgress(5)
        assertEquals(5, progressManager.translatedNodes.value)
        assertEquals(5, progressManager.remainingNodes.value)

        progressManager.completeTranslation()
        assertEquals(ProgressState.Completed, progressManager.state.value)

        progressManager.reset()
        assertEquals(ProgressState.Idle, progressManager.state.value)
    }

    @Test
    fun testTranslationCacheAndNodeCache() {
        val cache = TranslationCache()
        cache.put("Hello", "hi", "नमस्ते")
        assertEquals("नमस्ते", cache.get("Hello", "hi"))
        assertNotNull(cache.get("Hello", "hi"))

        val nodeCache = TranslationNodeCache()
        nodeCache.put("node_1", "Hello", "नमस्ते", "hi")
        val entry = nodeCache.get("node_1")
        assertNotNull(entry)
        assertEquals("नमस्ते", entry?.translatedText)

        nodeCache.clear()
        assertEquals(null, nodeCache.get("node_1"))
    }

    @Test
    fun testTranslationSessionManager() {
        TranslationSessionManager.startSession("tab_1", "example.com", "es", "Spanish")
        assertTrue(TranslationSessionManager.isDomainTranslationActive("example.com"))
        assertEquals("es", TranslationSessionManager.getDomainTargetLanguageCode("example.com"))

        TranslationSessionManager.disableDomainTranslation("example.com")
        assertFalse(TranslationSessionManager.isDomainTranslationActive("example.com"))
    }
}
