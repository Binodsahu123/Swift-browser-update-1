package com.swift.browser.vpnengine.domain

import java.io.File

data class ValidationResult(
    val isValid: Boolean,
    val protocol: String? = null,
    val errors: List<String> = emptyList()
)

class VpnProfileValidator {
    suspend fun validateProfile(file: File): ValidationResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val errors = mutableListOf<String>()
        if (!file.exists()) {
            return@withContext ValidationResult(false, null, listOf("File does not exist"))
        }

        val name = file.name.lowercase()
        val content = file.readText()
        var protocol: String? = null

        when {
            name.endsWith(".ovpn") -> {
                protocol = "OPENVPN"
                if (!content.contains("client")) errors.add("Missing 'client' directive")
                if (!content.contains("remote ")) errors.add("Missing 'remote' server address")
                if (!content.contains("<ca>")) errors.add("Missing CA certificate")
            }
            name.endsWith(".conf") -> {
                protocol = "WIREGUARD"
                if (!content.contains("[Interface]")) errors.add("Missing [Interface] section")
                if (!content.contains("PrivateKey")) errors.add("Missing PrivateKey")
                if (!content.contains("[Peer]")) errors.add("Missing [Peer] section")
                if (!content.contains("PublicKey")) errors.add("Missing PublicKey in Peer")
                if (!content.contains("Endpoint")) errors.add("Missing Endpoint")
            }
            name.endsWith(".zip") -> {
                // Future implementation for ZIP packages
                return@withContext ValidationResult(true, "PACKAGE", emptyList())
            }
            else -> {
                errors.add("Unsupported file format. Use .ovpn, .conf, or .zip")
            }
        }

        ValidationResult(errors.isEmpty(), protocol, errors)
    }
}
