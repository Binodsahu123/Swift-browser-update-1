package com.swift.browser.privatemode

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages BiometricPrompt authentication (fingerprint / face / device PIN)
 * for protecting and gating access to active Private Browsing tabs.
 */
class PrivateBiometricAuthManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "PrivateBiometricAuth"
    }

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isBiometricRequired = MutableStateFlow(true)
    val isBiometricRequired: StateFlow<Boolean> = _isBiometricRequired.asStateFlow()

    private val _isAutoPurgeOnTimeoutOrExit = MutableStateFlow(true)
    val isAutoPurgeOnTimeoutOrExit: StateFlow<Boolean> = _isAutoPurgeOnTimeoutOrExit.asStateFlow()

    private val _timeoutDurationMillis = MutableStateFlow(0L)
    val timeoutDurationMillis: StateFlow<Long> = _timeoutDurationMillis.asStateFlow()

    private val _lastAuthResult = MutableStateFlow<BiometricAuthResult?>(null)
    val lastAuthResult: StateFlow<BiometricAuthResult?> = _lastAuthResult.asStateFlow()

    @Volatile
    private var lastBackgroundTimestamp: Long = 0L

    @Volatile
    private var lastActiveTimestamp: Long = System.currentTimeMillis()

    fun setAutoPurgeOnTimeoutOrExit(enabled: Boolean) {
        _isAutoPurgeOnTimeoutOrExit.value = enabled
    }

    fun isAutoPurgeOnTimeoutOrExit(): Boolean = _isAutoPurgeOnTimeoutOrExit.value

    fun setTimeoutDurationMillis(millis: Long) {
        _timeoutDurationMillis.value = millis
    }

    fun getTimeoutDurationMillis(): Long = _timeoutDurationMillis.value

    fun recordActivity() {
        lastActiveTimestamp = System.currentTimeMillis()
    }

    fun onAppBackgrounded(
        onPurgeAction: (suspend () -> Unit)? = null,
        scope: CoroutineScope? = null
    ) {
        lastBackgroundTimestamp = System.currentTimeMillis()
        if (_timeoutDurationMillis.value == 0L && _isBiometricRequired.value) {
            val wasUnlocked = _isUnlocked.value
            lock()
            if (wasUnlocked && _isAutoPurgeOnTimeoutOrExit.value && onPurgeAction != null) {
                scope?.launch { onPurgeAction() }
            }
        }
    }

    fun onAppForegrounded(
        onPurgeAction: (suspend () -> Unit)? = null,
        scope: CoroutineScope? = null
    ) {
        val now = System.currentTimeMillis()
        val elapsed = if (lastBackgroundTimestamp > 0L) now - lastBackgroundTimestamp else 0L
        if (_isBiometricRequired.value && elapsed >= _timeoutDurationMillis.value) {
            val wasUnlocked = _isUnlocked.value
            lock()
            if (wasUnlocked && _isAutoPurgeOnTimeoutOrExit.value && onPurgeAction != null) {
                scope?.launch { onPurgeAction() }
            }
        }
        recordActivity()
    }

    fun onBiometricTimeout(
        onPurgeAction: (suspend () -> Unit)? = null,
        scope: CoroutineScope? = null
    ) {
        val wasUnlocked = _isUnlocked.value
        lock()
        if (wasUnlocked && _isAutoPurgeOnTimeoutOrExit.value && onPurgeAction != null) {
            scope?.launch { onPurgeAction() }
        }
    }

    fun onAppExit(
        onPurgeAction: (suspend () -> Unit)? = null,
        scope: CoroutineScope? = null
    ) {
        if (_isAutoPurgeOnTimeoutOrExit.value && onPurgeAction != null) {
            scope?.launch { onPurgeAction() }
        }
    }

    /**
     * Checks if biometric authentication hardware and enrollments are ready on this device.
     */
    fun checkBiometricAvailability(targetContext: Context = context): BiometricAvailability {
        return try {
            val biometricManager = BiometricManager.from(targetContext)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                                 BiometricManager.Authenticators.DEVICE_CREDENTIAL
            when (biometricManager.canAuthenticate(authenticators)) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.SECURITY_UPDATE_REQUIRED
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricAvailability.UNSUPPORTED
                else -> BiometricAvailability.UNSUPPORTED
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed checking biometric availability: ${e.message}")
            BiometricAvailability.UNSUPPORTED
        }
    }

    /**
     * Returns true if biometric prompt is available or if fallback device credentials can be used.
     */
    fun isAuthenticationAvailable(targetContext: Context = context): Boolean {
        val availability = checkBiometricAvailability(targetContext)
        return availability == BiometricAvailability.AVAILABLE || 
               availability == BiometricAvailability.NONE_ENROLLED
    }

    /**
     * Locks private tabs, requiring biometric or device credential unlock to view them again.
     */
    fun lock() {
        _isUnlocked.value = false
        Log.d(TAG, "Private mode locked with biometrics")
    }

    /**
     * Unlocks private tabs directly (e.g. if authentication succeeded or disabled).
     */
    fun unlock() {
        _isUnlocked.value = true
        _lastAuthResult.value = BiometricAuthResult.Success
        Log.d(TAG, "Private mode unlocked")
    }

    /**
     * Configures whether biometric authentication is required before viewing private tabs.
     */
    fun setBiometricRequired(required: Boolean) {
        _isBiometricRequired.value = required
        if (!required) {
            _isUnlocked.value = true
        }
    }

    /**
     * Returns whether private tabs can currently be viewed.
     */
    fun canAccessPrivateTabs(): Boolean {
        return !_isBiometricRequired.value || _isUnlocked.value
    }

    /**
     * Triggers BiometricPrompt on the given FragmentActivity to authenticate the user
     * with fingerprint, face unlock, or device PIN/pattern.
     */
    fun authenticate(
        activity: FragmentActivity,
        config: PrivateBiometricConfig = PrivateBiometricConfig(),
        onResult: (BiometricAuthResult) -> Unit = {}
    ) {
        if (!_isBiometricRequired.value) {
            unlock()
            onResult(BiometricAuthResult.Success)
            return
        }

        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.i(TAG, "Biometric authentication succeeded for private tabs")
                    _isUnlocked.value = true
                    _lastAuthResult.value = BiometricAuthResult.Success
                    onResult(BiometricAuthResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.w(TAG, "Biometric authentication error ($errorCode): $errString")
                    val result = if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        BiometricAuthResult.Cancelled
                    } else {
                        BiometricAuthResult.Error(errorCode, errString.toString())
                    }
                    _lastAuthResult.value = result
                    onResult(result)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Biometric authentication failed (fingerprint/face not recognized)")
                    val result = BiometricAuthResult.Failed
                    _lastAuthResult.value = result
                    onResult(result)
                }
            }

            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(config.promptTitle)
                .setSubtitle(config.promptSubtitle)
                .setDescription(config.promptDescription)

            if (config.allowDeviceCredentialFallback) {
                // On API 30+ or with BiometricManager, DEVICE_CREDENTIAL allows PIN/Pattern fallback
                promptInfoBuilder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                promptInfoBuilder.setNegativeButtonText("Cancel")
                promptInfoBuilder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
            }

            val promptInfo = promptInfoBuilder.build()
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Throwable) {
            Log.e(TAG, "Error launching BiometricPrompt: ${e.message}", e)
            val unavailable = BiometricAuthResult.Unavailable(e.message ?: "Biometric prompt error")
            _lastAuthResult.value = unavailable
            onResult(unavailable)
        }
    }
}
