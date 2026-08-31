package com.swift.browser.translateengine

import android.content.Context
import com.swift.browser.databasecore.DatabaseCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranslationRepository(private val context: Context) {
    private val db: TranslateDatabase by lazy {
        DatabaseCore.buildDatabase(
            context.applicationContext,
            TranslateDatabase::class.java,
            "swift_translate_db"
        )
    }
    private val dao by lazy { db.translationDao() }

    // Database / Cache methods
    suspend fun getTranslation(originalText: String, targetLang: String): String? = withContext(Dispatchers.IO) {
        dao.getTranslation(originalText, targetLang)?.translatedText
    }

    suspend fun saveTranslation(originalText: String, targetLang: String, translatedText: String) = withContext(Dispatchers.IO) {
        dao.insert(TranslationEntity(originalText, targetLang, translatedText))
    }

    suspend fun saveBatch(translations: Map<String, String>, targetLang: String) = withContext(Dispatchers.IO) {
        val entities = translations.map { (original, translated) ->
            TranslationEntity(originalText = original, targetLang = targetLang, translatedText = translated)
        }
        if (entities.isNotEmpty()) {
            dao.insertBatch(entities)
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dao.clearCache()
    }

    suspend fun getCacheCount(): Int = withContext(Dispatchers.IO) {
        dao.getCacheCount()
    }

    suspend fun pruneCache(olderThanMs: Long) = withContext(Dispatchers.IO) {
        val expiry = System.currentTimeMillis() - olderThanMs
        dao.pruneOldTranslations(expiry)
    }
}
