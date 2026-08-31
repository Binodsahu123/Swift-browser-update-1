package com.swift.browser.browserengine

import com.swift.browser.data.BrowserRepository
import com.swift.browser.data.TopSite
import kotlinx.coroutines.flow.Flow

/**
 * Domain engine responsible for Top Site / Custom Shortcut management.
 * Encapsulates shortcut creation, top site removal/hiding, and merged site feeds.
 */
interface TopSiteEngine {
    fun getTopSites(): Flow<List<TopSite>>
    suspend fun addCustomShortcut(url: String, title: String)
    suspend fun removeTopSite(topSite: TopSite)
}

class TopSiteEngineImpl(
    private val repository: BrowserRepository
) : TopSiteEngine {

    override fun getTopSites(): Flow<List<TopSite>> {
        return repository.getMergedTopSites()
    }

    override suspend fun addCustomShortcut(url: String, title: String) {
        if (url.isBlank()) return
        repository.addCustomShortcut(url, title)
    }

    override suspend fun removeTopSite(topSite: TopSite) {
        if (topSite.isCustom) {
            repository.deleteCustomShortcut(topSite.url)
        } else {
            repository.hideTopSite(topSite.url)
        }
    }
}
