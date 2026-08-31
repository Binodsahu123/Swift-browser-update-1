package com.swift.browser.aiengine

object AIPageAnalyzer {
    
    fun calculateReadingTimeMinutes(text: String): Int {
        val wordCount = text.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        return (wordCount / 200).coerceAtLeast(1)
    }

    fun prepareAnalysisPrompt(text: String, preferredLanguage: String, responseLength: String): String {
        val languageInstruction = if (preferredLanguage == "Same as Page") {
            """
            Analyze the language of the provided text:
            - If the page language is Hindi, you MUST respond in Hindi (हिंदी).
            - If the page language is English, you MUST respond in English.
            - If a translated page is active, you MUST respond in that translated language.
            - Otherwise, default to the main language of the webpage text provided.
            """.trimIndent()
        } else {
            "Respond strictly in the following language: $preferredLanguage."
        }

        val lengthInstruction = when (responseLength) {
            "Short" -> "Keep descriptions and summaries extremely concise, maximum 1-2 indices/sentences per section."
            "Long" -> "Provide comprehensive and detailed reports with robust explanations."
            else -> "Provide standard medium-length reports, balanced and easy to digest."
        }

        // Avoid exceeding text limits, keep the core text
        val cleanedText = text.replace("\\s+".toRegex(), " ").trim()
        val truncatedText = if (cleanedText.length > 8000) cleanedText.substring(0, 8000) + "... [Content Truncated]" else cleanedText

        return """
            You are a professional browser assistant. Analyze the following webpage content and provide a comprehensive structured analysis.
            $languageInstruction
            $lengthInstruction

            Format your response exactly as follows, using the specific section headers in capital bracket keys as markers so they can be parsed programmatically:

            [MAIN TOPIC]
            State the primary topic of the page in one short, clear line.

            [SUMMARY]
            Provide a polished, engaging overview explaining what this page is about.

            [KEY POINTS]
            - Key point 1...
            - Key point 2...

            [PROS AND CONS]
            Pros:
            - Pro 1...
            - Pro 2...
            Cons:
            - Con 1...
            - Con 2...

            [IMPORTANT FACTS & STATS]
            - Fact or stat 1...
            - Fact or stat 2...

            [IMPORTANT DATES]
            - Date 1: Description...
            - Date 2: Description...

            [IMPORTANT PEOPLE & ENTITIES]
            - Entity 1: Description...
            - Entity 2: Description...

            Webpage Content to Analyze:
            -----------------
            $truncatedText
            -----------------
        """.trimIndent()
    }
}
