package com.swift.browser.data

import com.swift.browser.historyengine.HistoryItem

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.net.URI

class BrowserRepository(private val db: BrowserDatabase) {
        private val historyDao = db.historyDao()
    private val topSiteDao = db.topSiteDao()
    private val articleDao = db.articleDao()
    private val downloadDao = db.downloadDao()

        val historyEngine = com.swift.browser.historyengine.api.HistoryEngineProvider.getEngine(historyDao)
    val history = historyEngine.getHistoryFlow()
    val downloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()

    suspend fun saveDownloadToDb(downloadId: Long, fileName: String, url: String, mimeType: String, status: String = "PENDING") {
        downloadDao.insertDownload(DownloadItem(downloadId, fileName, url, mimeType, status))
    }

    suspend fun updateDownloadStatusInDb(downloadId: Long, status: String) {
        downloadDao.updateDownloadStatus(downloadId, status)
    }

    suspend fun updateDownloadFileNameInDb(downloadId: Long, fileName: String) {
        downloadDao.updateDownloadFileName(downloadId, fileName)
    }

    suspend fun deleteDownloadFromDb(downloadId: Long) {
        downloadDao.deleteDownload(downloadId)
    }

    fun getArticlesByCategory(category: String): Flow<List<ArticleCacheEntity>> {
        return articleDao.getArticlesByCategory(category)
    }

    suspend fun saveArticles(articles: List<ArticleCacheEntity>) {
        articleDao.insertArticles(articles)
    }

    suspend fun clearArticlesByCategory(category: String) {
        articleDao.deleteArticlesByCategory(category)
    }

    fun getRecentHistory(limit: Int): Flow<List<HistoryItem>> {
        return historyEngine.getRecentHistory(limit)
    }

    suspend fun deleteHistoryItem(id: Int) {
        historyEngine.deleteHistoryItem(id)
    }

    suspend fun clearAllHistory() {
        historyEngine.clearAllHistory()
    }

    suspend fun addHistory(url: String, title: String) {
        historyEngine.addHistoryItem(url, title)
    }
    

    // Custom Top Sites and hidden states
    suspend fun addCustomShortcut(url: String, title: String) {
        if (url.isBlank()) return
        val existing = topSiteDao.getTopSiteByUrl(url)
        if (existing != null) {
            topSiteDao.updateTopSite(existing.copy(title = title, isCustom = true, isHidden = false))
        } else {
            topSiteDao.insertTopSite(TopSite(url = url, title = title, isCustom = true))
        }
    }

    suspend fun hideTopSite(url: String) {
        if (url.isBlank()) return
        val existing = topSiteDao.getTopSiteByUrl(url)
        if (existing != null) {
            topSiteDao.updateTopSite(existing.copy(isHidden = true))
        } else {
            topSiteDao.insertTopSite(TopSite(url = url, title = "", isCustom = false, isHidden = true))
        }
    }

    suspend fun deleteCustomShortcut(url: String) {
        val existing = topSiteDao.getTopSiteByUrl(url)
        if (existing != null && existing.isCustom) {
            topSiteDao.deleteTopSite(existing)
        }
    }

    fun getMergedTopSites(): Flow<List<TopSite>> {
        return combine(topSiteDao.getAllTopSites(), historyDao.getAllHistory()) { customSites, historyItems ->
            val customActive = customSites.filter { it.isCustom && !it.isHidden }
            val hiddenUrls = customSites.filter { it.isHidden }.map { it.url.lowercase().trim() }.toSet()

            val historyGrouped = historyItems
                .filter { item ->
                    val urlLower = item.url.lowercase().trim()
                    !hiddenUrls.contains(urlLower) &&
                            customActive.none { it.url.lowercase().trim() == urlLower }
                }
                .groupBy { it.url }
                .map { (url, items) ->
                    val totalVisits = items.sumOf { it.visitCount }
                    val title = items.firstOrNull()?.title ?: getDomainName(url)
                    TopSite(url = url, title = title, isCustom = false, isHidden = false, visitCount = totalVisits)
                }
                .sortedByDescending { it.visitCount }

            val combined = (customActive + historyGrouped).take(8)

            if (combined.size < 8) {
                val defaultSites = listOf(
                    TopSite(url = "https://www.google.com", title = "Google", isCustom = false),
                    TopSite(url = "https://chromewebstore.google.com", title = "Chrome Web Store", isCustom = false),
                    TopSite(url = "https://x.com", title = "X", isCustom = false),
                    TopSite(url = "https://www.wikipedia.org", title = "Wikipedia", isCustom = false),
                    TopSite(url = "https://github.com", title = "GitHub", isCustom = false),
                    TopSite(url = "https://stackoverflow.com", title = "StackOverflow", isCustom = false),
                    TopSite(url = "https://www.reddit.com", title = "Reddit", isCustom = false)
                )
                val filled = combined.toMutableList()
                for (defaultSite in defaultSites) {
                    if (filled.size >= 8) break
                    val defaultUrlLower = defaultSite.url.lowercase().trim()
                    val alreadyExists = filled.any { it.url.lowercase().trim() == defaultUrlLower }
                    val isHidden = hiddenUrls.contains(defaultUrlLower)
                    if (!alreadyExists && !isHidden) {
                        filled.add(defaultSite)
                    }
                }
                filled
            } else {
                combined
            }
        }
    }

    

    suspend fun searchHistory(query: String, limit: Int): List<HistoryItem> {
        return historyDao.searchHistory(query, limit)
    }

    private fun getDomainName(url: String): String {
        return try {
            val uri = URI(url)
            val domain = uri.host ?: ""
            if (domain.startsWith("www.")) domain.substring(4) else domain
        } catch (e: Exception) {
            url
        }
    }

    // SSL domain whitelisting delegated to security-engine
    fun isSslWhitelisted(host: String): Boolean = com.swift.browser.securityengine.SwiftSecurityEngine.isSslWhitelisted(host)
    fun whitelistSslDomain(host: String) { com.swift.browser.securityengine.SwiftSecurityEngine.whitelistSslDomain(host) }

    // Tab session management
    fun getAllTabs(): Flow<List<TabSessionEntity>> = db.tabSessionDao().getAllTabsFlow()

    suspend fun saveTab(tabSession: TabSessionEntity) {
        db.tabSessionDao().saveTab(tabSession)
    }

    suspend fun deleteTab(tabSession: TabSessionEntity) {
        db.tabSessionDao().deleteTab(tabSession)
    }

    suspend fun deleteAllTabs() {
        db.tabSessionDao().deleteAllTabs()
    }

    suspend fun updateScroll(tabId: String, x: Int, y: Int) {
        db.tabSessionDao().updateScroll(tabId, x, y)
    }
}

