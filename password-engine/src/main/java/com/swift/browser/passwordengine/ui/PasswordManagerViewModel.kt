package com.swift.browser.passwordengine.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swift.browser.passwordengine.PasswordEngine
import com.swift.browser.passwordengine.model.PasswordCategory
import com.swift.browser.passwordengine.model.PasswordEntry
import com.swift.browser.passwordengine.repository.PasswordRepository
import com.swift.browser.passwordengine.security.PasswordStrengthEvaluator
import com.swift.browser.passwordengine.security.StrengthTier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import android.net.Uri
import com.swift.browser.passwordengine.importexport.ImportSummary

data class SecurityAuditSummary(
    val totalCount: Int = 0,
    val weakCount: Int = 0,
    val reusedCount: Int = 0,
    val strongCount: Int = 0,
    val averageScore: Int = 0
)

class PasswordManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PasswordEngine.getInstance(application)
    private val repository: PasswordRepository = engine.repository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<PasswordCategory?>(null)
    val selectedCategory: StateFlow<PasswordCategory?> = _selectedCategory.asStateFlow()

    private val _isMasterUnlocked = MutableStateFlow(false)
    val isMasterUnlocked: StateFlow<Boolean> = _isMasterUnlocked.asStateFlow()

    private val _masterPin = MutableStateFlow("1234") // Default test PIN for unlock simulation

    private val _revealedPasswordIds = MutableStateFlow<Set<Long>>(emptySet())
    val revealedPasswordIds: StateFlow<Set<Long>> = _revealedPasswordIds.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val passwords: StateFlow<List<PasswordEntry>> = combine(_searchQuery, _selectedCategory) { query, cat ->
        Pair(query, cat)
    }.flatMapLatest { (query, cat) ->
        if (query.isNotBlank()) {
            repository.searchPasswords(query)
        } else if (cat != null) {
            repository.getPasswordsByCategory(cat.displayName)
        } else {
            repository.allPasswords
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val auditSummary: StateFlow<SecurityAuditSummary> = repository.allPasswords.combine(MutableStateFlow(Unit)) { list, _ ->
        if (list.isEmpty()) {
            SecurityAuditSummary()
        } else {
            var weak = 0
            var strong = 0
            var sumScore = 0
            val passwordsMap = mutableMapOf<String, Int>()

            list.forEach { entry ->
                val decrypted = repository.decryptPassword(entry)
                val evaluation = PasswordStrengthEvaluator.evaluate(decrypted)
                sumScore += evaluation.score
                if (evaluation.tier == StrengthTier.VERY_WEAK || evaluation.tier == StrengthTier.WEAK) {
                    weak++
                } else if (evaluation.tier == StrengthTier.STRONG || evaluation.tier == StrengthTier.VERY_STRONG) {
                    strong++
                }
                passwordsMap[decrypted] = (passwordsMap[decrypted] ?: 0) + 1
            }

            val reused = passwordsMap.filter { it.value > 1 }.map { it.value }.sum()

            SecurityAuditSummary(
                totalCount = list.size,
                weakCount = weak,
                reusedCount = reused,
                strongCount = strong,
                averageScore = sumScore / list.size
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SecurityAuditSummary()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: PasswordCategory?) {
        _selectedCategory.value = category
    }

    fun unlockMaster(pin: String): Boolean {
        if (pin == _masterPin.value) {
            _isMasterUnlocked.value = true
            return true
        }
        return false
    }

    fun lockMaster() {
        _isMasterUnlocked.value = false
        _revealedPasswordIds.value = emptySet()
    }

    fun togglePasswordVisibility(id: Long) {
        val current = _revealedPasswordIds.value
        _revealedPasswordIds.value = if (current.contains(id)) {
            current - id
        } else {
            current + id
        }
    }

    fun decryptPassword(entry: PasswordEntry): String {
        return repository.decryptPassword(entry)
    }

    fun savePassword(
        id: Long = 0,
        siteUrl: String,
        siteTitle: String,
        username: String,
        rawPassword: String,
        notes: String = "",
        category: String = "General",
        isFavorite: Boolean = false
    ) {
        viewModelScope.launch {
            if (id == 0L) {
                repository.savePassword(siteUrl, siteTitle, username, rawPassword, notes, category, isFavorite)
            } else {
                repository.updatePassword(id, siteUrl, siteTitle, username, rawPassword, notes, category, isFavorite)
            }
        }
    }

    fun deletePassword(id: Long) {
        viewModelScope.launch {
            repository.deletePassword(id)
        }
    }

    fun toggleFavorite(entry: PasswordEntry) {
        viewModelScope.launch {
            repository.updatePassword(
                id = entry.id,
                siteUrl = entry.siteUrl,
                siteTitle = entry.siteTitle,
                username = entry.username,
                rawPassword = repository.decryptPassword(entry),
                notes = entry.notes,
                category = entry.category,
                isFavorite = !entry.isFavorite
            )
        }
    }

    fun generateNewPassword(
        length: Int = 16,
        includeLower: Boolean = true,
        includeUpper: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true,
        avoidAmbiguous: Boolean = true
    ): String {
        return engine.generatePassword(
            length = length,
            includeLower = includeLower,
            includeUpper = includeUpper,
            includeNumbers = includeNumbers,
            includeSymbols = includeSymbols,
            avoidAmbiguous = avoidAmbiguous
        )
    }

    fun importCredentialsFromUri(uri: Uri, onResult: (ImportSummary) -> Unit) {
        viewModelScope.launch {
            val result = engine.importer.importFromUri(getApplication(), uri)
            onResult(result)
        }
    }

    fun importCredentialsFromText(text: String, onResult: (ImportSummary) -> Unit) {
        viewModelScope.launch {
            val result = engine.importer.importFromText(text)
            onResult(result)
        }
    }
}
