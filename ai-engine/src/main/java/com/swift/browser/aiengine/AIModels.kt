package com.swift.browser.aiengine

data class AIAnalysisResult(
    val mainTopic: String = "",
    val shortSummary: String = "",
    val summary: String = "",
    val keyPoints: List<String> = emptyList(),
    val highlights: List<String> = emptyList(),
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList(),
    val factsAndStats: List<String> = emptyList(),
    val dates: List<String> = emptyList(),
    val peopleAndEntities: List<String> = emptyList(),
    val links: List<Pair<String, String>> = emptyList(),
    val readingTime: Int = 0,
    val rawResponse: String = ""
)

fun parseAnalysis(raw: String): AIAnalysisResult {
    var currentSection = ""
    var mainTopic = ""
    var shortSummary = ""
    var summary = ""
    val keyPoints = mutableListOf<String>()
    val highlights = mutableListOf<String>()
    val pros = mutableListOf<String>()
    val cons = mutableListOf<String>()
    val factsAndStats = mutableListOf<String>()
    val dates = mutableListOf<String>()
    val peopleAndEntities = mutableListOf<String>()
    val links = mutableListOf<Pair<String, String>>()
    val lines = raw.lines()
    var inPros = false
    var inCons = false
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("#") || trimmed.startsWith("**")) {
            currentSection = trimmed.lowercase()
            inPros = currentSection.contains("pros")
            inCons = currentSection.contains("cons")
        } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
            val point = trimmed.removePrefix("-").removePrefix("*").trim()
            if (inPros) pros.add(point)
            else if (inCons) cons.add(point)
            else keyPoints.add(point)
        } else if (trimmed.isNotEmpty()) {
            if (mainTopic.isEmpty()) mainTopic = trimmed
            else if (shortSummary.isEmpty()) shortSummary = trimmed
            else summary += trimmed + "\n"
        }
    }
    return AIAnalysisResult(
        mainTopic = mainTopic,
        shortSummary = shortSummary,
        summary = summary.trim(),
        keyPoints = keyPoints,
        highlights = highlights,
        pros = pros,
        cons = cons,
        factsAndStats = factsAndStats,
        dates = dates,
        peopleAndEntities = peopleAndEntities,
        links = links,
        readingTime = 0,
        rawResponse = raw
    )
}
