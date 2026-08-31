package com.swift.browser.passwordengine.security

import java.security.SecureRandom

data class PasswordGeneratorConfig(
    val length: Int = 16,
    val includeLower: Boolean = true,
    val includeUpper: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val avoidAmbiguous: Boolean = true
)

object PasswordGenerator {

    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    private val AMBIGUOUS_CHARS = setOf('l', '1', 'I', 'o', '0', 'O')

    fun generatePassword(
        length: Int = 16,
        includeLower: Boolean = true,
        includeUpper: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true,
        avoidAmbiguous: Boolean = true
    ): String {
        return generatePassword(
            PasswordGeneratorConfig(
                length = length,
                includeLower = includeLower,
                includeUpper = includeUpper,
                includeNumbers = includeNumbers,
                includeSymbols = includeSymbols,
                avoidAmbiguous = avoidAmbiguous
            )
        )
    }

    fun generatePassword(config: PasswordGeneratorConfig): String {
        val random = SecureRandom()
        val targetLength = config.length.coerceIn(8, 64)

        var lowerPool = if (config.includeLower) LOWERCASE else ""
        var upperPool = if (config.includeUpper) UPPERCASE else ""
        var numberPool = if (config.includeNumbers) NUMBERS else ""
        var symbolPool = if (config.includeSymbols) SYMBOLS else ""

        if (config.avoidAmbiguous) {
            lowerPool = lowerPool.filter { it !in AMBIGUOUS_CHARS }
            upperPool = upperPool.filter { it !in AMBIGUOUS_CHARS }
            numberPool = numberPool.filter { it !in AMBIGUOUS_CHARS }
            symbolPool = symbolPool.filter { it !in AMBIGUOUS_CHARS }
        }

        val combinedPool = lowerPool + upperPool + numberPool + symbolPool
        val safePool = if (combinedPool.isEmpty()) LOWERCASE else combinedPool

        val passwordChars = mutableListOf<Char>()

        // Ensure at least one character from each selected category if pool is non-empty
        if (config.includeLower && lowerPool.isNotEmpty()) {
            passwordChars.add(lowerPool[random.nextInt(lowerPool.length)])
        }
        if (config.includeUpper && upperPool.isNotEmpty()) {
            passwordChars.add(upperPool[random.nextInt(upperPool.length)])
        }
        if (config.includeNumbers && numberPool.isNotEmpty()) {
            passwordChars.add(numberPool[random.nextInt(numberPool.length)])
        }
        if (config.includeSymbols && symbolPool.isNotEmpty()) {
            passwordChars.add(symbolPool[random.nextInt(symbolPool.length)])
        }

        // Fill remaining slots uniformly from the safe combined pool
        while (passwordChars.size < targetLength) {
            passwordChars.add(safePool[random.nextInt(safePool.length)])
        }

        // Cryptographically secure shuffle using SecureRandom
        for (i in passwordChars.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = passwordChars[i]
            passwordChars[i] = passwordChars[j]
            passwordChars[j] = temp
        }

        return passwordChars.joinToString("")
    }
}

