# Orion Extension Compatibility Matrix (Part 3)

| Extension API | Compatibility Level | Notes |
|---|---|---|
| chrome.alarms | FULL | Implemented via ExtensionAlarmsAdapter |
| chrome.bookmarks | FULL | Proxies to BookmarkEngine |
| chrome.browserAction | FULL | Handled via ExtensionActionAdapter |
| chrome.commands | FULL | Keyboard shortcuts mapped |
| chrome.contextMenus | FULL | Context menu adapter |
| chrome.cookies | FULL | Backed by Android CookieManager |
| chrome.declarativeNetRequest | PARTIAL | Full URL filtering, limited response header modification via WebView |
| chrome.downloads | FULL | Backed by DownloadEngine |
| chrome.extension | FULL | Legacy aliases mapped |
| chrome.history | FULL | Proxies to HistoryEngine |
| chrome.idle | FULL | Backed by system states |
| chrome.management | FULL | Handled by ExtensionManagementAdapter |
| chrome.notifications | FULL | Proxies to NotificationEngine |
| chrome.omnibox | FULL | Handled via ExtensionOmniboxAdapter |
| chrome.pageAction | FULL | Handled via ExtensionActionAdapter |
| chrome.permissions | FULL | ExtensionPermissionAdapter |
| chrome.runtime | FULL | Full message bus and port implementation |
| chrome.scripting | PARTIAL | Dependent on JS_INJECTION_IN_FRAME_AND_WORLD support in AndroidX Webkit |
| chrome.search | FULL | Proxies to SearchEngine |
| chrome.sessions | PARTIAL | Limited by Android window abstraction |
| chrome.sidePanel | FULL | ExtensionSidePanelAdapter |
| chrome.storage | FULL | Implemented via Room local DB (sync is local only) |
| chrome.tabs | FULL | Maps directly to TabEngine |
| chrome.topSites | FULL | Backed by local analytics/history |
| chrome.tts | FULL | ExtensionTtsAdapter via Android TextToSpeech |
| chrome.webRequest | PARTIAL | Limited synchronous blocking capabilities due to WebView architecture |
| chrome.windows | PARTIAL | Maps to Android multi-window/activity where available |
