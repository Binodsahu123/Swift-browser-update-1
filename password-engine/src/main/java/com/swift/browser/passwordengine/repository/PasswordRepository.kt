package com.swift.browser.passwordengine.repository

import com.swift.browser.passwordengine.db.PasswordDao
import com.swift.browser.passwordengine.model.PasswordEntry
import com.swift.browser.passwordengine.security.PasswordEncryptionManager
import com.swift.browser.passwordengine.security.PasswordStrengthEvaluator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PasswordRepository(
    private val passwordDao: PasswordDao,
    private val encryptionManager: PasswordEncryptionManager
) {

    val allPasswords: Flow<List<PasswordEntry>> = passwordDao.getAllPasswords()
    val favoritePasswords: Flow<List<PasswordEntry>> = passwordDao.getFavoritePasswords()
    val passwordCount: Flow<Int> = passwordDao.getPasswordCount()

    fun searchPasswords(query: String): Flow<List<PasswordEntry>> {
        return passwordDao.searchPasswords(query)
    }

    fun getPasswordsByCategory(category: String): Flow<List<PasswordEntry>> {
        return passwordDao.getPasswordsByCategory(category)
    }

    suspend fun getPasswordById(id: Long): PasswordEntry? {
        return passwordDao.getPasswordById(id)
    }

    suspend fun findCredentialsForUrl(url: String): List<PasswordEntry> {
        val domain = extractDomain(url)
        if (domain.isEmpty()) return emptyList()
        return passwordDao.getPasswordsForDomain(domain)
    }

    suspend fun savePassword(
        siteUrl: String,
        siteTitle: String,
        username: String,
        rawPassword: String,
        notes: String = "",
        category: String = "General",
        isFavorite: Boolean = false,
        isPrivate: Boolean = false,
        isExplicitUserAction: Boolean = false
    ): Long {
        if (isPrivate && !isExplicitUserAction) {
            // Private browsing MUST NOT silently save newly entered credentials
            return -1L
        }
        val encrypted = encryptionManager.encrypt(rawPassword)
        val strength = PasswordStrengthEvaluator.evaluate(rawPassword).score
        val entry = PasswordEntry(
            siteUrl = siteUrl.trim(),
            siteTitle = if (siteTitle.isBlank()) extractDomain(siteUrl) else siteTitle.trim(),
            username = username.trim(),
            encryptedPassword = encrypted,
            notes = notes.trim(),
            category = category,
            isFavorite = isFavorite,
            strengthRating = strength,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis()
        )
        return passwordDao.insertPassword(entry)
    }

    suspend fun updatePassword(
        id: Long,
        siteUrl: String,
        siteTitle: String,
        username: String,
        rawPassword: String,
        notes: String = "",
        category: String = "General",
        isFavorite: Boolean = false
    ) {
        val encrypted = encryptionManager.encrypt(rawPassword)
        val strength = PasswordStrengthEvaluator.evaluate(rawPassword).score
        val existing = passwordDao.getPasswordById(id)
        val entry = PasswordEntry(
            id = id,
            siteUrl = siteUrl.trim(),
            siteTitle = if (siteTitle.isBlank()) extractDomain(siteUrl) else siteTitle.trim(),
            username = username.trim(),
            encryptedPassword = encrypted,
            notes = notes.trim(),
            category = category,
            isFavorite = isFavorite,
            strengthRating = strength,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis()
        )
        passwordDao.updatePassword(entry)
    }

    suspend fun deletePassword(id: Long) {
        passwordDao.deletePasswordById(id)
    }

    suspend fun updateLastUsed(id: Long) {
        val existing = passwordDao.getPasswordById(id)
        if (existing != null) {
            passwordDao.updatePassword(existing.copy(lastUsedAt = System.currentTimeMillis()))
        }
    }

    fun decryptPassword(entry: PasswordEntry): String {
        return encryptionManager.decrypt(entry.encryptedPassword)
    }

    private fun extractDomain(url: String): String {
        var clean = url.trim().lowercase()
        clean = clean.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        val slashIndex = clean.indexOf('/')
        if (slashIndex != -1) {
            clean = clean.substring(0, slashIndex)
        }
        val colonIndex = clean.indexOf(':')
        if (colonIndex != -1) {
            clean = clean.substring(0, colonIndex)
        }
        return clean
    }
}
