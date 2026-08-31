package com.swift.browser.passwordengine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.swift.browser.passwordengine.autofill.CredentialAutofillEngine
import com.swift.browser.passwordengine.autofill.CredentialContext
import com.swift.browser.passwordengine.repository.PasswordRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PasswordPrivateModeTest {

    private lateinit var context: Context
    private lateinit var engine: PasswordEngine
    private lateinit var repository: PasswordRepository
    private lateinit var autofillEngine: CredentialAutofillEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        engine = PasswordEngine.getInstance(context)
        repository = engine.repository
        autofillEngine = engine.autofillEngine
    }

    @Test
    fun testExistingPasswordReadInPrivateMode() = runBlocking {
        val saveId = repository.savePassword(
            siteUrl = "https://example.com",
            siteTitle = "Example Domain",
            username = "user123",
            rawPassword = "SecretPassword123!",
            isPrivate = false,
            isExplicitUserAction = true
        )
        assertTrue(saveId > 0)

        // Query autofill suggestions in private context
        val context = CredentialContext(siteUrl = "https://example.com", isPrivate = true, privateSessionId = "priv_s1")
        val suggestions = autofillEngine.getAutofillSuggestionsForUrl("https://example.com", context)

        assertEquals(1, suggestions.size)
        assertEquals("user123", suggestions[0].username)
        assertEquals("SecretPassword123!", suggestions[0].password)
    }

    @Test
    fun testNewPasswordNotAutoSavedInPrivateMode() = runBlocking {
        val context = CredentialContext(siteUrl = "https://login.example.com", isPrivate = true, privateSessionId = "priv_s1")

        // Auto-save attempt in private mode without explicit user action
        val result = autofillEngine.saveCredential(
            siteUrl = "https://login.example.com",
            siteTitle = "Example Login",
            username = "priv_user",
            rawPassword = "PrivatePass123!",
            context = context,
            isExplicitUserAction = false
        )

        assertEquals(-1L, result)

        // Confirm database has no entry for this site
        val found = repository.findCredentialsForUrl("https://login.example.com")
        assertTrue(found.isEmpty())
    }

    @Test
    fun testNormalModeAutoSaveAndExplicitSave() = runBlocking {
        // Normal mode save
        val saveId = repository.savePassword(
            siteUrl = "https://normal.com",
            siteTitle = "Normal Site",
            username = "normal_user",
            rawPassword = "NormalPassword1!",
            isPrivate = false,
            isExplicitUserAction = false
        )

        assertTrue(saveId > 0)

        val found = repository.findCredentialsForUrl("https://normal.com")
        assertEquals(1, found.size)
        assertEquals("normal_user", found[0].username)
    }

    @Test
    fun testPrivateSessionCloseDoesNotDeleteNormalPasswords() = runBlocking {
        // Save normal password
        val saveId = repository.savePassword(
            siteUrl = "https://persistent.com",
            siteTitle = "Persistent Site",
            username = "persistent_user",
            rawPassword = "PersistentPass123!",
            isPrivate = false,
            isExplicitUserAction = true
        )
        assertTrue(saveId > 0)

        // Close private session
        autofillEngine.onPrivateSessionClosed("priv_s1")

        // Normal password must remain intact
        val count = repository.passwordCount.first()
        assertTrue(count > 0)

        val found = repository.findCredentialsForUrl("https://persistent.com")
        assertEquals(1, found.size)
    }
}
