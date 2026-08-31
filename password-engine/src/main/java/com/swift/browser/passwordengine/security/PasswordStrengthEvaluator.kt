package com.swift.browser.passwordengine.security

import kotlin.math.log2
import kotlin.math.pow

enum class StrengthTier(val label: String, val colorHex: String) {
    VERY_WEAK("Very Weak", "#EF4444"),
    WEAK("Weak", "#F97316"),
    MEDIUM("Medium", "#EAB308"),
    STRONG("Strong", "#22C55E"),
    VERY_STRONG("Very Strong", "#10B981")
}

data class StrengthResult(
    val score: Int, // 0 to 100
    val tier: StrengthTier,
    val feedback: List<String>,
    val entropyBits: Double = 0.0,
    val poolSize: Int = 0,
    val crackTimeDisplay: String = "Instant",
    val length: Int = 0,
    val lowerCount: Int = 0,
    val upperCount: Int = 0,
    val digitCount: Int = 0,
    val specialCharCount: Int = 0,
    val hasMinLength: Boolean = false,
    val hasSpecialChars: Boolean = false,
    val hasMixedCase: Boolean = false,
    val hasNumbers: Boolean = false,
    val hasHighEntropy: Boolean = false
)

object PasswordStrengthEvaluator {

    fun evaluate(password: String): StrengthResult {
        if (password.isEmpty()) {
            return StrengthResult(
                score = 0,
                tier = StrengthTier.VERY_WEAK,
                feedback = listOf("Password cannot be empty"),
                crackTimeDisplay = "Instant"
            )
        }

        val length = password.length
        val lowerCount = password.count { it.isLowerCase() }
        val upperCount = password.count { it.isUpperCase() }
        val digitCount = password.count { it.isDigit() }
        val specialCharCount = password.count { !it.isLetterOrDigit() }

        val hasLower = lowerCount > 0
        val hasUpper = upperCount > 0
        val hasDigit = digitCount > 0
        val hasSymbol = specialCharCount > 0

        // Determine pool size
        var poolSize = 0
        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSymbol) poolSize += 33 // Standard special symbols pool
        if (poolSize == 0) poolSize = 26

        // Standard Information Entropy (bits) = L * log2(poolSize)
        val infoEntropy = length * log2(poolSize.toDouble())

        // Shannon Entropy to penalize low-variety / repeating characters
        val charFrequencies = password.groupingBy { it }.eachCount()
        var shannonEntropyPerChar = 0.0
        for ((_, count) in charFrequencies) {
            val p = count.toDouble() / length
            shannonEntropyPerChar -= p * log2(p)
        }
        val shannonEntropyTotal = shannonEntropyPerChar * length

        // Effective entropy balances pool size with char variation
        val effectiveEntropy = minOf(infoEntropy, shannonEntropyTotal * 1.25)
        val roundedEntropy = (Math.round(effectiveEntropy * 10.0) / 10.0)

        // Calculate time to crack (assuming 10 billion guesses/sec)
        val crackTimeDisplay = calculateCrackTime(effectiveEntropy)

        val feedback = mutableListOf<String>()

        // Checklists & Scores
        val isMinLength = length >= 12
        val isMixedCase = hasUpper && hasLower
        val isSpecial = specialCharCount >= 1
        val isNumber = hasDigit
        val isHighEntropy = roundedEntropy >= 60.0

        if (length < 8) {
            feedback.add("Critically short! Aim for at least 12-16 characters.")
        } else if (length < 12) {
            feedback.add("Increase length to 12+ characters for better entropy.")
        }

        if (!hasUpper) feedback.add("Add uppercase letters (A-Z).")
        if (!hasLower) feedback.add("Add lowercase letters (a-z).")
        if (!hasDigit) feedback.add("Add numbers (0-9).")
        if (!hasSymbol) feedback.add("Add special characters (!@#$).")

        // Pattern & repetition penalties
        val lowerPass = password.lowercase()
        val commonPatterns = listOf("password", "123456", "qwerty", "admin", "welcome", "letmein", "abc123")
        var patternPenalty = 0
        if (commonPatterns.any { lowerPass.contains(it) }) {
            patternPenalty += 30
            feedback.add("Avoid dictionary words or sequential key patterns.")
        }

        // Repeating sequence penalty
        val maxRepeats = charFrequencies.values.maxOrNull() ?: 0
        if (length > 3 && maxRepeats.toDouble() / length > 0.5) {
            patternPenalty += 20
            feedback.add("Avoid repeating the same character multiple times.")
        }

        // Calculate Score (0 - 100) based on Entropy (50%), Length (25%), Variety (25%)
        var score = 0

        // Entropy contribution (0 - 50 pts)
        score += when {
            effectiveEntropy >= 80 -> 50
            effectiveEntropy >= 60 -> 40
            effectiveEntropy >= 40 -> 28
            effectiveEntropy >= 20 -> 16
            else -> (effectiveEntropy * 0.8).toInt()
        }

        // Length contribution (0 - 25 pts)
        score += when {
            length >= 16 -> 25
            length >= 12 -> 20
            length >= 8 -> 10
            else -> 4
        }

        // Character Variety & Special Chars (0 - 25 pts)
        var varietyScore = 0
        if (hasLower) varietyScore += 5
        if (hasUpper) varietyScore += 5
        if (hasDigit) varietyScore += 5
        if (hasSymbol) varietyScore += 10
        score += varietyScore

        // Apply penalties
        score = (score - patternPenalty).coerceIn(0, 100)

        // Tier Determination
        val tier = when {
            score >= 85 && effectiveEntropy >= 60.0 -> StrengthTier.VERY_STRONG
            score >= 70 && effectiveEntropy >= 45.0 -> StrengthTier.STRONG
            score >= 50 && effectiveEntropy >= 30.0 -> StrengthTier.MEDIUM
            score >= 30 -> StrengthTier.WEAK
            else -> StrengthTier.VERY_WEAK
        }

        return StrengthResult(
            score = score,
            tier = tier,
            feedback = feedback,
            entropyBits = roundedEntropy,
            poolSize = poolSize,
            crackTimeDisplay = crackTimeDisplay,
            length = length,
            lowerCount = lowerCount,
            upperCount = upperCount,
            digitCount = digitCount,
            specialCharCount = specialCharCount,
            hasMinLength = isMinLength,
            hasSpecialChars = isSpecial,
            hasMixedCase = isMixedCase,
            hasNumbers = isNumber,
            hasHighEntropy = isHighEntropy
        )
    }

    private fun calculateCrackTime(entropyBits: Double): String {
        if (entropyBits <= 0) return "Instant"

        // Expected guesses = 2^(bits - 1)
        // Rate = 10,000,000,000 (10 billion guesses/second)
        val rate = 10_000_000_000.0
        val log10Seconds = (entropyBits - 1.0) * log2(2.0) / log2(10.0) - log2(rate) / log2(10.0)

        if (log10Seconds < 0) return "Instant"

        val seconds = 10.0.pow(log10Seconds)

        val minutes = seconds / 60.0
        val hours = minutes / 60.0
        val days = hours / 24.0
        val years = days / 365.25
        val centuries = years / 100.0

        return when {
            seconds < 1.0 -> "Instant"
            seconds < 60.0 -> "${seconds.toInt()} seconds"
            minutes < 60.0 -> "${minutes.toInt()} minutes"
            hours < 24.0 -> "${hours.toInt()} hours"
            days < 365.0 -> "${days.toInt()} days"
            years < 100.0 -> "${years.toInt()} years"
            centuries < 1000.0 -> "${formatWithCommas(centuries.toLong())} centuries"
            else -> "Millions of years"
        }
    }

    private fun formatWithCommas(value: Long): String {
        return java.text.NumberFormat.getIntegerInstance().format(value)
    }
}

