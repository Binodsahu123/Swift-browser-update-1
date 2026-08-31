package com.swift.browser.adblockengine.filters

import android.content.Context
import android.util.Log
import com.swift.browser.adblockengine.brave.BraveRule
import com.swift.browser.adblockengine.brave.BraveRuleParser
import java.io.File

/**
 * Handles fast reading and writing of downloaded filter files from dynamic internal storage.
 */
object FilterListCache {
    private const val TAG = "FilterListCache"

    fun saveList(context: Context, name: String, rules: List<String>) {
        try {
            val file = File(context.filesDir, "adblock_cache_$name")
            file.writeText(rules.joinToString("\n"))
            Log.i(TAG, "Successfully cached list: $name with ${rules.size} elements.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed caching list: $name", e)
        }
    }

    fun readList(context: Context, name: String): List<BraveRule> {
        val rules = ArrayList<BraveRule>()
        try {
            val file = File(context.filesDir, "adblock_cache_$name")
            if (file.exists()) {
                file.forEachLine { line ->
                    BraveRuleParser.parseLine(line)?.let {
                        rules.add(it)
                    }
                }
                Log.i(TAG, "Successfully read list: $name with ${rules.size} parsed rules from cache.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading list from cache: $name", e)
        }

        // If rules list is empty, supply high-quality built-in fallback rules
        if (rules.isEmpty()) {
            val fallbackLines = if (name.contains("easylist")) {
                listOf(
                    "||doubleclick.net^",
                    "||pagead2.googlesyndication.com^",
                    "||adservice.google.com^",
                    "||adnxs.com^",
                    "||adsystem.com^",
                    "||taboola.com^",
                    "||outbrain.com^",
                    "||amazon-adsystem.com^",
                    "||pubmatic.com^",
                    "||rubiconproject.com^",
                    "##.ad-banner",
                    "##.adsbygoogle",
                    "##div[id^=\"google_ads_\"]",
                    "##.ad-container",
                    "##.ad-box",
                    "##.ad-wrapper",
                    "##.sponsored-post"
                )
            } else if (name.contains("easyprivacy")) {
                listOf(
                    "||google-analytics.com^",
                    "||googletagmanager.com^",
                    "||segment.io^",
                    "||mixpanel.com^",
                    "||hotjar.com^",
                    "||amplitude.com^",
                    "||sentry.io^",
                    "||crashlytics.com^",
                    "||optimizely.com^"
                )
            } else {
                emptyList()
            }

            for (line in fallbackLines) {
                BraveRuleParser.parseLine(line)?.let {
                    rules.add(it)
                }
            }
            Log.i(TAG, "Loaded ${rules.size} built-in offline fallback rules for $name")
        }

        return rules
    }

    fun deleteList(context: Context, name: String) {
        try {
            val file = File(context.filesDir, "adblock_cache_$name")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
