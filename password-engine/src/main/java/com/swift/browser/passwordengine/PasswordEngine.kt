package com.swift.browser.passwordengine

import android.content.Context
import com.swift.browser.passwordengine.autofill.CredentialAutofillEngine
import com.swift.browser.passwordengine.db.PasswordDatabase
import com.swift.browser.passwordengine.repository.PasswordRepository
import com.swift.browser.passwordengine.security.PasswordEncryptionManager
import com.swift.browser.passwordengine.security.PasswordGenerator
import com.swift.browser.passwordengine.security.PasswordStrengthEvaluator

import com.swift.browser.passwordengine.importexport.CredentialImporter
import com.swift.browser.passwordengine.importexport.ImportSummary

class PasswordEngine private constructor(context: Context) {

    val database: PasswordDatabase = PasswordDatabase.getDatabase(context)
    val encryptionManager: PasswordEncryptionManager = PasswordEncryptionManager(context)
    val repository: PasswordRepository = PasswordRepository(database.passwordDao(), encryptionManager)
    val autofillEngine: CredentialAutofillEngine = CredentialAutofillEngine(repository)
    val importer: CredentialImporter = CredentialImporter(repository)

    fun generatePassword(
        length: Int = 16,
        includeLower: Boolean = true,
        includeUpper: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true,
        avoidAmbiguous: Boolean = true
    ): String {
        return PasswordGenerator.generatePassword(
            length = length,
            includeLower = includeLower,
            includeUpper = includeUpper,
            includeNumbers = includeNumbers,
            includeSymbols = includeSymbols,
            avoidAmbiguous = avoidAmbiguous
        )
    }

    fun evaluateStrength(password: String) = PasswordStrengthEvaluator.evaluate(password)

    companion object {
        @Volatile
        private var INSTANCE: PasswordEngine? = null

        fun getInstance(context: Context): PasswordEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PasswordEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
