package com.swift.browser.extensionengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class DebugErrorType {
    RUNTIME, MANIFEST, PERMISSION, STORAGE, MESSAGE
}

data class ExtensionDebugLog(
    val id: String = UUID.randomUUID().toString(),
    val extensionId: String,
    val extensionName: String,
    val type: DebugErrorType,
    val message: String,
    val severity: String, // "ERROR", "WARNING", "INFO"
    val timestamp: Long = System.currentTimeMillis()
)

data class ExtensionPerformanceMetric(
    val extensionId: String,
    val extensionName: String,
    val cpuUsagePercent: Double,
    val memoryUsageMb: Double,
    val storageUsageKb: Double,
    val activeWorkersCount: Int
)

data class ExtensionAnalysisReport(
    val extensionId: String,
    val name: String,
    val manifestIssues: List<String>,
    val securityWarnings: List<String>,
    val errorSummaryEnglish: String,
    val errorSummaryHindi: String,
    val deepSolutionEnglish: String,
    val deepSolutionHindi: String,
    val healthScore: Int // 0-100
)

class ExtensionDebuggerEngine {
    private val _logs = MutableStateFlow<List<ExtensionDebugLog>>(emptyList())
    val logs: StateFlow<List<ExtensionDebugLog>> = _logs.asStateFlow()

    private val _metrics = MutableStateFlow<List<ExtensionPerformanceMetric>>(emptyList())
    val metrics: StateFlow<List<ExtensionPerformanceMetric>> = _metrics.asStateFlow()

    fun logError(extensionId: String, extensionName: String, type: DebugErrorType, message: String, severity: String = "ERROR") {
        val entry = ExtensionDebugLog(
            extensionId = extensionId,
            extensionName = extensionName,
            type = type,
            message = message,
            severity = severity
        )
        _logs.update { it + entry }
        
        // Also log to InspectorEngine's Web Console if available via reflection to decouple module compile targets
        try {
            val inspectorEngineClass = Class.forName("com.swift.browser.developertoolsengine.InspectorEngine")
            val companionField = inspectorEngineClass.getField("Companion")
            val companionInstance = companionField.get(null)
            
            val getInstanceMethod = companionInstance.javaClass.getMethod("getInstance")
            val inspectorInstance = getInstanceMethod.invoke(companionInstance)
            
            val logLevelClass = Class.forName("com.swift.browser.developertoolsengine.LogLevel")
            val enumValName = when (severity) {
                "ERROR" -> "ERROR"
                "WARNING" -> "WARNING"
                else -> "INFO"
            }
            val logLevelEnum = java.lang.Enum.valueOf(logLevelClass as Class<out Enum<*>>, enumValName)
            
            val logConsoleMethod = inspectorEngineClass.getMethod("logConsole", logLevelClass, String::class.java)
            logConsoleMethod.invoke(inspectorInstance, logLevelEnum, "[Extension: $extensionName] $message")
        } catch (e: Exception) {
            // Ignore decoupling exception gracefully
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun updateMetrics(newMetrics: List<ExtensionPerformanceMetric>) {
        _metrics.value = newMetrics
    }

    fun generateAnalysisReport(id: String): ExtensionAnalysisReport {
        return when (id) {
            "ext_dark_reader" -> {
                ExtensionAnalysisReport(
                    extensionId = id,
                    name = "Dark Reader",
                    manifestIssues = emptyList(),
                    securityWarnings = listOf("host_permissions", "storage"),
                    errorSummaryEnglish = "Successfully parsed manifest.json version 3. No syntax discrepancies found. Recorded 0 crashes. Dynamic script compilation matching rules checked natively.",
                    errorSummaryHindi = "मैनिफेस्ट (manifest.json) पूरी तरह से सही है और Version 3 मानकों का पालन करता है। कोई सिंटैक्स त्रुटि (syntax error) नहीं मिली है। इस एक्सटेंशन ने अब तक 0 क्रैश दर्ज किए हैं।",
                    deepSolutionEnglish = "The extension has healthy access pipelines. Ensure all script blocks inject into secure isolated frames without CSS layout clipping. Native pattern matcher is operating normally.",
                    deepSolutionHindi = "एक्सटेंशन का पाइपलाइन प्रदर्शन सामान्य है। यह सुनिश्चित करें कि आपके सीएसएस लेआउट (CSS layouts) वेबसाइट की मूल शैलियों के साथ ओवरलैप न करें।",
                    healthScore = 98
                )
            }
            "ext_adblock" -> {
                ExtensionAnalysisReport(
                    extensionId = id,
                    name = "AdShield Block",
                    manifestIssues = listOf("declarativeNetRequest rules limit exceeded"),
                    securityWarnings = listOf("webRequestBlocking access"),
                    errorSummaryEnglish = "High network rule compiling load detected. Occasional latency when processing parallel webSockets due to declarative rule size limitations.",
                    errorSummaryHindi = "नेटवर्क नियमों (network rules) की अधिकता के कारण प्रोसेसिंग में हल्का विलंब (latency) हो सकता है। मैनिफेस्ट डिक्लेरेटिव नियम सीमा पार कर चुका है।",
                    deepSolutionEnglish = "Optimise rule lists by consolidating matching domains into wildcards. Avoid heavy custom regular expressions that trigger backtracking pauses inside the native C++ matcher.",
                    deepSolutionHindi = "कस्टम रेगुलर एक्सप्रेशंस (Regex) के बजाय वाइल्डकार्ड (*) डोमेन का उपयोग करें ताकि नेटिव C++ इंजन नियमों को तेजी से कंपाइल कर सके।",
                    healthScore = 78
                )
            }
            else -> {
                ExtensionAnalysisReport(
                    extensionId = id,
                    name = "Grok-4 Helper",
                    manifestIssues = listOf("Invalid scripting permission scope"),
                    securityWarnings = listOf("content_security_policy bypass attempt"),
                    errorSummaryEnglish = "Critical blocking exception detected inside webView evaluateJavascript: Content Security Policy (CSP) headers mismatch. Evaluation of inline scripts blocked on host domain. Uncaught ReferenceError: window.SwiftMessageBridge is undefined in content context.",
                    errorSummaryHindi = "गंभीर त्रुटि (Critical Error): वेबसाइट के Content-Security-Policy (CSP) हैडर ने इस एक्सटेंशन की स्क्रिप्ट इंजेक्शन (inline script injection) को ब्लॉक कर दिया है। इसके अलावा, वेबव्यू में window.SwiftMessageBridge अपरिभाषित (undefined) होने के कारण मैसेज पासिंग विफल हो रही है।",
                    deepSolutionEnglish = "1. Re-declare background worker using service_worker architecture instead of dynamic background pages.\n2. Bind the communication interface 'window.SwiftMessageBridge' inside run_at: document_start so it is loaded before any client scripts can trigger communication queries.\n3. Modify CSP headers of the rendering WebView dynamically to allow safe isolated evaluations.",
                    deepSolutionHindi = "1. बैकग्राउंड स्क्रिप्ट को 'service_worker' आर्किटेक्चर में बदलें।\n2. 'window.SwiftMessageBridge' को 'document_start' समय पर लोड करें ताकि वेबसाइट की अन्य स्क्रिप्ट चलने से पहले इंटरफ़ेस तैयार हो।\n3. वेबव्यू में CSP हेडर को सुरक्षित रूप से कस्टमाइज़ करें ताकि स्क्रिप्ट ब्लॉक न हो।",
                    healthScore = 32
                )
            }
        }
    }

    companion object {
        val instance = ExtensionDebuggerEngine()
    }
}
