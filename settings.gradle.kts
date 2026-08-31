pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SwiftBrowser"
include(":app")
include(":adblock-engine")
include(":ai-engine")
include(":analytics-core")
include(":antivirus-engine")
include(":audio-engine")
include(":backup-engine")
include(":battery-saver-engine")
include(":bookmark-engine")
include(":browser-engine")
include(":cookie-engine")
include(":data-saver-engine")
include(":database-core")
include(":desktop-engine")
include(":developer-tools-engine")
include(":download-engine")
include(":download-ui-engine")
include(":extension-engine")
include(":history-engine")
include(":image-engine")
include(":network-core")
include(":network-stats-engine")
include(":news-engine")
include(":notification-engine")
include(":password-engine")
include(":permission-engine")
include(":privacy-shield-engine")
include(":private-mode-engine")
include(":private-storage-engine")
include(":reader-engine")
include(":search-engine")
include(":security-engine")
include(":settings-engine")
include(":tab-engine")
include(":translate-engine")
include(":ui-core")
include(":video-engine")
include(":vpn-engine")
include(":weather-engine")
include(":web-studio")

