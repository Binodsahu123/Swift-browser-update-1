package com.swift.browser.passwordengine.autofill

import com.swift.browser.passwordengine.model.PasswordEntry
import com.swift.browser.passwordengine.repository.PasswordRepository

data class DecryptedCredential(
    val entryId: Long,
    val siteTitle: String,
    val siteUrl: String,
    val username: String,
    val password: String
)

data class CredentialContext(
    val siteUrl: String,
    val isPrivate: Boolean = false,
    val privateSessionId: String? = null
)

class CredentialAutofillEngine(private val repository: PasswordRepository) {

    suspend fun getAutofillSuggestionsForUrl(url: String, context: CredentialContext? = null): List<DecryptedCredential> {
        val entries = repository.findCredentialsForUrl(url)
        return entries.map { entry ->
            DecryptedCredential(
                entryId = entry.id,
                siteTitle = entry.siteTitle,
                siteUrl = entry.siteUrl,
                username = entry.username,
                password = repository.decryptPassword(entry)
            )
        }
    }

    suspend fun saveCredential(
        siteUrl: String,
        siteTitle: String,
        username: String,
        rawPassword: String,
        context: CredentialContext? = null,
        isExplicitUserAction: Boolean = false
    ): Long {
        val isPrivate = context?.isPrivate == true
        return repository.savePassword(
            siteUrl = siteUrl,
            siteTitle = siteTitle,
            username = username,
            rawPassword = rawPassword,
            isPrivate = isPrivate,
            isExplicitUserAction = isExplicitUserAction
        )
    }

    fun onPrivateSessionClosed(sessionId: String) {
        // Rules: Never delete existing password entries when private session closes.
        // Do not create a second password store.
    }

    fun buildAutofillJavascript(credential: DecryptedCredential): String {
        val usernameEscaped = credential.username.replace("'", "\\'")
        val passwordEscaped = credential.password.replace("'", "\\'")
        
        return """
            (function() {
                var usernameFields = document.querySelectorAll("input[type='text'], input[type='email'], input[name*='user'], input[name*='login'], input[id*='user'], input[id*='email']");
                var passwordFields = document.querySelectorAll("input[type='password']");
                
                if (usernameFields.length > 0) {
                    var uField = usernameFields[0];
                    uField.value = '$usernameEscaped';
                    uField.dispatchEvent(new Event('input', { bubbles: true }));
                    uField.dispatchEvent(new Event('change', { bubbles: true }));
                }
                
                if (passwordFields.length > 0) {
                    var pField = passwordFields[0];
                    pField.value = '$passwordEscaped';
                    pField.dispatchEvent(new Event('input', { bubbles: true }));
                    pField.dispatchEvent(new Event('change', { bubbles: true }));
                }
            })();
        """.trimIndent()
    }
}
