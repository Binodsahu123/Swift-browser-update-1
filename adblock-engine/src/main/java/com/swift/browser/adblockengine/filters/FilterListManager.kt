package com.swift.browser.adblockengine.filters

import android.content.Context
import android.util.Log
import com.swift.browser.adblockengine.brave.BraveAdblockAdapter
import com.swift.browser.adblockengine.brave.BraveRule
import com.swift.browser.adblockengine.brave.BraveRuleParser
import com.swift.browser.adblockengine.core.AdBlockDiagnostics
import com.swift.browser.adblockengine.core.AdBlockPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Orchestrates activation, rules compilations, and synchronizations of EasyList,
 * EasyPrivacy, and user-defined filter arrays.
 */
object FilterListManager {
    private const val TAG = "FilterListManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun init(context: Context) {
        // Load initial cached rule sets at bootup
        rebuildActiveRules(context)
    }

    fun rebuildActiveRules(context: Context) {
        scope.launch {
            val rules = ArrayList<BraveRule>()

            // 1. EasyList Compiled Source
            if (AdBlockPolicy.isEasyListEnabled) {
                val list = FilterListCache.readList(context, "easylist.txt")
                rules.addAll(list)
            }

            // 2. EasyPrivacy Compiled Source
            if (AdBlockPolicy.isEasyPrivacyEnabled) {
                val list = FilterListCache.readList(context, "easyprivacy.txt")
                rules.addAll(list)
            }

            // 3. Custom Filters Added By User
            if (AdBlockPolicy.isCustomFiltersEnabled) {
                val customList = CustomFilterSource.getCustomFilters(context)
                for (line in customList) {
                    BraveRuleParser.parseLine(line)?.let {
                        rules.add(it)
                    }
                }
            }

            // Load into Brave-style core matchers
            BraveAdblockAdapter.updateRules(rules)
            Log.i(TAG, "Rebuild completed. Active rules count: ${BraveAdblockAdapter.getRulesCount()}")
            AdBlockDiagnostics.logEvent("Engine Rebuild", "Active Rules Synchronized. Count: ${BraveAdblockAdapter.getRulesCount()}")
        }
    }
}
