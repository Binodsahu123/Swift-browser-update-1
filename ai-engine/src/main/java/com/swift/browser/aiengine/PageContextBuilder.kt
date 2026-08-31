package com.swift.browser.aiengine

import android.net.Uri

enum class PageType {
    ARTICLE,
    BLOG,
    NEWS,
    DOCUMENTATION,
    FORUM,
    VIDEO,
    GENERAL
}

object PageContextBuilder {

    fun detectPageType(url: String, context: PriorityContext): PageType {
        val lowercaseUrl = url.lowercase()
        val host = try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" }.lowercase()
        val path = try { Uri.parse(url).path ?: "" } catch (e: Exception) { "" }.lowercase()
        val title = context.title.lowercase()
        
        return when {
            host.contains("vimeo.com") ||
            path.contains("/video/") || title.contains("video") || title.contains("playlist") -> {
                PageType.VIDEO
            }
            host.contains("news") || host.contains("times") || host.contains("cnn.com") || host.contains("nytimes") ||
            host.contains("bbc.co.uk") || host.contains("reuters") || path.contains("news") || path.contains("/article/") -> {
                PageType.NEWS
            }
            path.contains("/docs/") || path.contains("/developer/") || path.contains("/guide/") || 
            path.contains("/reference/") || path.contains("/api/") || path.contains("wiki") || title.contains("documentation") -> {
                PageType.DOCUMENTATION
            }
            host.contains("reddit.com") || host.contains("stackoverflow.com") || host.contains("quora.com") ||
            path.contains("/forum/") || path.contains("/discussions/") || title.contains("forum") || title.contains("discussion") -> {
                PageType.FORUM
            }
            path.contains("blog") || host.contains("medium.com") || host.contains("substack") || 
            title.contains("blog") -> {
                PageType.BLOG
            }
            context.paragraphs.size >= 5 -> {
                PageType.ARTICLE
            }
            else -> {
                PageType.GENERAL
            }
        }
    }

    fun prepareSummaryPrompt(
        contextValue: PriorityContext,
        pageType: PageType,
        targetLanguage: String,
        detectedLangName: String,
        responseLength: String
    ): String {
        val resolvedLanguage = if (targetLanguage.equals("Same as Page", ignoreCase = true)) {
            detectedLangName
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

        val lengthInstruction = when (responseLength) {
            "Short" -> "Keep explanations and summaries extremely concise, maximum 1-2 bullet points or short sentences per section."
            "Long" -> "Provide highly comprehensive, detailed and exhaustive summaries of the webpage content."
            else -> "Provide standard medium-length balanced summaries, highly readable and structured."
        }

        val pageTypeDescription = when (pageType) {
            PageType.VIDEO -> "This is a Video Page. Focus on summarizing the video description, video cues, title and transcripts or video details if present."
            PageType.NEWS -> "This is a News Article. Highlight chronological events, quotes, key announcements, dates, and names of people or organizations involved."
            PageType.DOCUMENTATION -> "This is Technical Documentation/API Guide. Prioritize technical specifications, code requirements, setup guidelines, and references."
            PageType.FORUM -> "This is a Community Discussion/Forum Thread. Analyze different user viewpoints, common consensus or solutions, and community advice."
            PageType.BLOG -> "This is a Personal/Editorial Blog Post. Highlight the author's voice, key takeaways, arguments, and recommendations."
            else -> "This is a Standard Webpage. Focus on extracting the primary thesis, key points, highlighted details, and relevant hyperlinked references."
        }

        val formattedContext = contextValue.toFormattedPromptString()

        return """
            You are a professional web content analyst and Chrome-style AI assistant.
            $pageTypeDescription
            
            $languageInstruction
            $lengthInstruction

            Analyze the following prioritized webpage structure (Title, Main Paragraphs, Headings, Lists, Tables and Emphasized Texts) and provide a polished structured response.

            You MUST format your response exactly as follows. Use the unique bracketed section headers in CAPITAL LETTERS as exact markers. Do not add bold symbols ** inside the section header line.

            [MAIN TOPIC]
            Provide a short (one-line) bold title indicating the primary topic of this page.

            [SHORT SUMMARY]
            Write a 2-3 sentence engaging high-level summary overview.

            [DETAILED SUMMARY]
            Write a detailed, comprehensive synthesis of the content explaining all nuance and context.

            [KEY POINTS]
            - bulletpoint 1
            - bulletpoint 2
            
            [HIGHLIGHTS & TAKEAWAYS]
            - Highlight 1
            - Highlight 2

            [PROS AND CONS]
            Pros:
            - Pro 1
            Cons:
            - Con 1

            [IMPORTANT FACTS]
            - Fact or visual stat 1
            - Fact or visual stat 2

            [IMPORTANT DATES]
            - Date: Event/nuance 1
            
            [IMPORTANT PEOPLE & ENTITIES]
            - Name: Description/Role 1

            [IMPORTANT LINKS]
            List any important hyperlink references found in the context. Format exactly like:
            - Topic/Link Text: URL Reference

            Webpage Context:
            -----------------
            $formattedContext
            -----------------
        """.trimIndent()
    }
}
