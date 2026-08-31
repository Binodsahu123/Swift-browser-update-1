package com.swift.browser.aiengine

object PageAnalyzer {
    
    fun prepareAnalysisPrompt(
        context: FastPageContext,
        targetLanguage: String, // E.g., "Hindi", "English", "Bengali", "Same as Page"
        detectedPageLanguageName: String, // E.g., "Hindi", "English"
        responseLength: String
    ): String {
        val resolvedLanguage = if (targetLanguage.equals("Same as Page", ignoreCase = true)) {
            detectedPageLanguageName
        } else {
            targetLanguage
        }

        val languageInstruction = """
            Return the entire response STRICTLY in the following language: $resolvedLanguage.
            - If the language is Hindi, you MUST respond in Hindi (हिंदी).
            - If the language is English, you MUST respond in English.
            - If the language is Spanish, respond in Spanish.
            - If the language is Arabic, respond in Arabic.
            - If the language is Bengali, respond in Bengali.
            - Make sure all section headers and contents are written in $resolvedLanguage.
        """.trimIndent()

        val formattedContext = context.toFormattedPromptString()

        return """
            You are a professional browser assistant. Summarize this page.
            Provide: Main Topic, Key Points, Important Facts, Short Summary.
            NO DEEP ANALYSIS. NO RESEARCH MODE. NO LONG PROCESSING. Target speed: 1-5 seconds.

            $languageInstruction

            You MUST format your response exactly as follows. Use the unique bracketed section headers in CAPITAL LETTERS as exact markers. Do not add bold symbols ** inside the section header line.

            [MAIN TOPIC]
            A short, concise one-line title indicating the page topic.

            [SHORT SUMMARY]
            A brief, highly focused 2-3 sentence overview.

            [KEY POINTS]
            - bulletpoint 1
            - bulletpoint 2
            - bulletpoint 3

            [IMPORTANT FACTS]
            - Fact 1
            - Fact 2

            Webpage Context:
            -----------------
            $formattedContext
            -----------------
        """.trimIndent()
    }
}
