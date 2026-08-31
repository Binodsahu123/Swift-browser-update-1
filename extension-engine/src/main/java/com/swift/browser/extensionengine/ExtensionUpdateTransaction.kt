package com.swift.browser.extensionengine

import java.io.File
import java.util.UUID

/**
 * State machine for atomic extension update transactions.
 */
enum class UpdateTransactionState {
    CREATED,
    DOWNLOADING,
    VERIFYING,
    STAGED,
    COMMITTING,
    COMMITTED,
    ROLLING_BACK,
    ROLLED_BACK,
    FAILED
}

/**
 * Tracks an individual atomic extension update operation.
 */
data class ExtensionUpdateTransaction(
    val transactionId: String = UUID.randomUUID().toString(),
    val extensionId: String,
    val currentVersion: String,
    var candidateVersion: String? = null,
    var stagingDir: File? = null,
    var backupDir: File? = null,
    var targetDir: File? = null,
    var tempPackageFile: File? = null,
    var state: UpdateTransactionState = UpdateTransactionState.CREATED,
    var trustState: ExtensionTrustState = ExtensionTrustState.UNTRUSTED_REJECTED,
    var verificationState: PackageVerificationState = PackageVerificationState.UNVERIFIED,
    val startTimestamp: Long = System.currentTimeMillis(),
    var completedTimestamp: Long? = null,
    var error: Throwable? = null
) {
    fun transition(newState: UpdateTransactionState) {
        this.state = newState
        if (newState == UpdateTransactionState.COMMITTED ||
            newState == UpdateTransactionState.ROLLED_BACK ||
            newState == UpdateTransactionState.FAILED
        ) {
            this.completedTimestamp = System.currentTimeMillis()
        }
    }

    fun cleanupTemporaryFiles() {
        try {
            stagingDir?.let { if (it.exists()) it.deleteRecursively() }
        } catch (_: Exception) {}
        try {
            backupDir?.let { if (it.exists()) it.deleteRecursively() }
        } catch (_: Exception) {}
        try {
            tempPackageFile?.let { if (it.exists()) it.delete() }
        } catch (_: Exception) {}
    }
}
