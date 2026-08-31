package com.swift.browser.aiengine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AIWebsiteBridgeSystem private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AIWebsiteBridge"
        
        @Volatile
        private var instance: AIWebsiteBridgeSystem? = null

        fun getInstance(context: Context): AIWebsiteBridgeSystem {
            return instance ?: synchronized(this) {
                instance ?: AIWebsiteBridgeSystem(context.applicationContext).also { instance = it }
            }
        }
    }

    private val webViewsMap = java.util.concurrent.ConcurrentHashMap<String, WebView>()
    private val _loginNeededMap = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<Boolean>>()

    private var chatGPTWebView: WebView? = null
    private var geminiWebView: WebView? = null

    private val _chatGptLoginNeeded = MutableStateFlow(false)
    val chatGptLoginNeeded = _chatGptLoginNeeded.asStateFlow()

    private val _geminiLoginNeeded = MutableStateFlow(false)
    val geminiLoginNeeded = _geminiLoginNeeded.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun getLoginNeededFlow(provider: String): kotlinx.coroutines.flow.StateFlow<Boolean> {
        val cleanName = provider.lowercase().trim().replace(" ", "")
        return _loginNeededMap.getOrPut(cleanName) { MutableStateFlow(false) }.asStateFlow()
    }

    /**
     * Initializes or returns the WebView for the requested provider.
     * MUST be called on the Main Thread.
     */
    fun getOrCreateWebView(provider: String): WebView {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalStateException("WebView operations must be performed on the Main Thread.")
        }
        val cleanName = provider.uppercase().replace(" ", "_")
        
        // Match legacy ChatGPT and Gemini WebViews for full backwards compatibility
        if (cleanName == "CHATGPT" && chatGPTWebView != null) return chatGPTWebView!!
        if (cleanName == "GEMINI" && geminiWebView != null) return geminiWebView!!

        val existing = webViewsMap[cleanName]
        if (existing != null) return existing

        val webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = com.swift.browser.desktopengine.useragent.UserAgentManager.getDesktopUserAgent("ai-bridge", context)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "[Bridge WebView PageFinished] $provider: $url")
                    checkLoginStateAndUrl(provider, url)
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    checkLoginStateAndUrl(provider, url)
                }
            }
        }

        // Enable cookie isolation
        val cookieEngine = com.swift.browser.cookieengine.CookieEngineApi.getInstance(context)
        cookieEngine.setupNormalCookies(webView)

        val targetUrl = getTargetUrlForProvider(provider)
        webView.loadUrl(targetUrl)

        if (cleanName == "CHATGPT") {
            chatGPTWebView = webView
        } else if (cleanName == "GEMINI") {
            geminiWebView = webView
        }
        webViewsMap[cleanName] = webView

        return webView
    }

    fun getTargetUrlForProvider(provider: String): String {
        return when (provider.lowercase().trim().replace(" ", "")) {
            "chatgpt" -> "https://chatgpt.com"
            "gemini" -> "https://gemini.google.com/app"
            "claude" -> "https://claude.ai"
            "deepseek" -> "https://chat.deepseek.com"
            "qwen" -> "https://chat.qwenlm.ai"
            "mistral" -> "https://chat.mistral.ai"
            "grok" -> "https://grok.com"
            "customprovider" -> "https://chatgpt.com"
            else -> "https://chatgpt.com"
        }
    }

    fun checkLoginStateAndUrl(provider: String, url: String?) {
        val path = url ?: ""
        val cleanName = provider.lowercase().trim().replace(" ", "")
        val state = _loginNeededMap.getOrPut(cleanName) { MutableStateFlow(false) }

        val needsLogin = when (cleanName) {
            "chatgpt" -> path.contains("auth", ignoreCase = true) || path.contains("login", ignoreCase = true) || path.contains("signup", ignoreCase = true)
            "gemini" -> path.contains("accounts.google.com", ignoreCase = true) || path.contains("signin", ignoreCase = true)
            "claude" -> path.contains("login", ignoreCase = true) || path.contains("auth", ignoreCase = true)
            "deepseek" -> path.contains("login", ignoreCase = true) || path.contains("sign", ignoreCase = true)
            "qwen" -> path.contains("login", ignoreCase = true) || path.contains("sign", ignoreCase = true)
            "mistral" -> path.contains("login", ignoreCase = true) || path.contains("auth", ignoreCase = true)
            "grok" -> path.contains("login", ignoreCase = true) || path.contains("sign", ignoreCase = true)
            else -> path.contains("login", ignoreCase = true) || path.contains("auth", ignoreCase = true)
        }
        state.value = needsLogin

        if (cleanName == "chatgpt") {
            _chatGptLoginNeeded.value = needsLogin
        } else if (cleanName == "gemini") {
            _geminiLoginNeeded.value = needsLogin
        }
    }

    /**
     * Executes JS on the WebView on the Main Thread.
     */
    private suspend fun evalJs(webView: WebView, script: String): String = suspendCancellableCoroutine { cont ->
        mainHandler.post {
            try {
                webView.evaluateJavascript(script) { result ->
                    if (cont.isActive) {
                        cont.resume(result ?: "null")
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume("Error: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Triggers sending a prompt to the AI Website by inputting the prompt into the input textbox
     * on the page and then invoking its Send/Submit button.
     */
    suspend fun submitPrompt(provider: String, prompt: String): Boolean = withContext(Dispatchers.Main) {
        val webView = getOrCreateWebView(provider)
        val escapedPrompt = JSONObject.quote(prompt)
        
        val jsCode = """
            (function() {
                try {
                    let inputEl = document.querySelector('#prompt-textarea') || 
                                 document.querySelector('textarea') || 
                                 document.querySelector('[role="textbox"]') ||
                                 document.getElementById('m-editor-textarea') ||
                                 document.querySelector('input[type="text"]');
                                 
                    if (!inputEl) {
                        return "ERROR: Input text box not found on $provider page. Make sure you are logged in if required.";
                    }
                    
                    inputEl.value = $escapedPrompt;
                    inputEl.innerText = $escapedPrompt;
                    if (inputEl.tagName.toLowerCase() === 'div') {
                        inputEl.textContent = $escapedPrompt;
                    }
                    
                    // Trigger input changes so scripts register the prompt
                    let ev = new Event('input', { bubbles: true });
                    inputEl.dispatchEvent(ev);
                    
                    let evChange = new Event('change', { bubbles: true });
                    inputEl.dispatchEvent(evChange);
                    
                    let evKey = new KeyboardEvent('keyup', { bubbles: true, key: 'a' });
                    inputEl.dispatchEvent(evKey);
                    
                    // Locate and click the Submit/Send action button
                    let btn = document.querySelector('button[data-testid="send-button"]') ||
                              document.querySelector('button[data-testid*="send"]') ||
                              document.querySelector('button[aria-label*="Send message"]') ||
                              document.querySelector('button[aria-label*="Send"]') ||
                              document.querySelector('button[aria-label*="send"]') ||
                              document.querySelector('.send-button') ||
                              document.querySelector('button[type="submit"]') ||
                              document.querySelector('form button');
                              
                    if (btn) {
                        btn.click();
                        return "SENT";
                    }
                    
                    // Press Enter as fallback
                    let enterEvent = new KeyboardEvent('keydown', {
                        bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13
                    });
                    inputEl.dispatchEvent(enterEvent);
                    return "SENT_FALLBACK";
                } catch (e) {
                    return "ERROR: " + e.message;
                }
            })()
        """.trimIndent()

        val submitResult = suspendCancellableCoroutine<String> { cont ->
            webView.evaluateJavascript(jsCode) { res ->
                cont.resume(res ?: "")
            }
        }
        
        Log.d(TAG, "submitPrompt result for $provider: $submitResult")
        return@withContext !submitResult.contains("ERROR", ignoreCase = true)
    }

    /**
     * Polls the page to scrape the newly generated response.
     */
    suspend fun getPageResponse(provider: String): String = withContext(Dispatchers.Main) {
        val webView = getOrCreateWebView(provider)
        
        val scraperJs = """
            (function() {
                try {
                    let assistants = [];
                    
                    // Check ChatGPT response classes
                    let elements = document.querySelectorAll('[data-message-author-role="assistant"] .markdown, [data-message-author-role="assistant"]');
                    if (elements.length > 0) {
                        elements.forEach(e => assistants.push(e.innerText || e.textContent));
                    } else {
                        // Check Gemini elements
                        let geminiElements = document.querySelectorAll('message-content, .message-content, .conversation-container .assistant, .model-response');
                        if (geminiElements.length > 0) {
                            geminiElements.forEach(e => assistants.push(e.innerText || e.textContent));
                        } else {
                            // General fallback divs
                            let fallbackMarkdown = document.querySelectorAll('.markdown, .response-container');
                            fallbackMarkdown.forEach(e => assistants.push(e.innerText || e.textContent));
                        }
                    }

                    // Look for ongoing generation status
                    let isGenerating = false;
                    let stopButton = document.querySelector('button[aria-label*="Stop"]') || 
                                     document.querySelector('button[data-testid*="stop"]') ||
                                     document.querySelector('[data-testid$="stop-button"]');
                    if (stopButton) {
                        isGenerating = true;
                    }
                    
                    let spinner = document.querySelector('.generating, .spinner, .loading, .progress-bar, [role="progressbar"]');
                    if (spinner) {
                        isGenerating = true;
                    }
                    
                    let lastResponse = "";
                    if (assistants.length > 0) {
                        lastResponse = assistants[assistants.length - 1];
                    }
                    
                    return JSON.stringify({
                        isGenerating: isGenerating,
                        responsesCount: assistants.length,
                        lastResponse: lastResponse.trim()
                    });
                } catch(e) {
                    return JSON.stringify({isGenerating: false, error: e.message, lastResponse: ""});
                }
            })()
        """.trimIndent()

        var attempts = 0
        val maxAttempts = 60 // 30 seconds max wait
        var finalResponse = ""
        var consecutiveUnchanged = 0
        var previousResponseLength = 0

        while (attempts < maxAttempts) {
            attempts++
            delay(500) // Poll every 500ms
            
            val pollResultJson = suspendCancellableCoroutine<String> { cont ->
                webView.evaluateJavascript(scraperJs) { res ->
                    cont.resume(res ?: "{}")
                }
            }

            // Clean wrapping quotes from JS string return
            val jsonRaw = if (pollResultJson.startsWith("\"") && pollResultJson.endsWith("\"") && pollResultJson.length > 2) {
                try {
                    org.json.JSONTokener(pollResultJson).nextValue() as? String ?: pollResultJson
                } catch (e: Exception) {
                    pollResultJson
                }
            } else {
                pollResultJson
            }

            try {
                val json = JSONObject(jsonRaw)
                val isGenerating = json.optBoolean("isGenerating", false)
                val lastResponse = json.optString("lastResponse", "")
                
                if (lastResponse.isNotBlank()) {
                    finalResponse = lastResponse
                }

                if (isGenerating) {
                    // Update counter if length hasn't changed
                    if (lastResponse.length == previousResponseLength) {
                        consecutiveUnchanged++
                    } else {
                        consecutiveUnchanged = 0
                        previousResponseLength = lastResponse.length
                    }
                } else if (lastResponse.isNotBlank()) {
                    // Not generating and we got content -> we're probably done!
                    consecutiveUnchanged++
                    if (consecutiveUnchanged > 2) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Page Scrape JSON Error: ${e.message}")
            }
        }
        
        if (finalResponse.isBlank()) {
            return@withContext "Response scraping timeout or blank response from $provider. Please make sure the hidden session is active and authenticated if required."
        }
        
        return@withContext finalResponse
    }

    /**
     * Executes the absolute bridge flow seamlessly: submit prompt, poll for scrape, and output answer.
     */
    suspend fun executePrompt(provider: String, prompt: String): String = withContext(Dispatchers.IO) {
        val isChatGPT = provider.equals("ChatGPT", ignoreCase = true)
        val loginNeeded = if (isChatGPT) _chatGptLoginNeeded.value else _geminiLoginNeeded.value
        
        if (loginNeeded) {
            return@withContext "[Authentication Required]\nPlease log in to your $provider account first. Use the 'Login' action displayed on the AI Specialist panel top bar to enter your credentials securely on the official website."
        }

        // 1. Submit the prompt
        val sent = submitPrompt(provider, prompt)
        if (!sent) {
            return@withContext "Error: Failed to inject prompt into $provider page. Ensure the page has loaded successfully and is ready."
        }

        // 2. Poll and read the response
        delay(1000) // Give page script 1 second to accept click and start drawing assistant replies
        val response = getPageResponse(provider)
        return@withContext response
    }
}
