plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.library)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "com.swift.browser.browserengine"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // browser-engine depends on all the sub-engines
    implementation(project(":desktop-engine"))
    implementation(project(":tab-engine"))
    implementation(project(":search-engine"))
    implementation(project(":download-engine"))
    implementation(project(":bookmark-engine"))
    implementation(project(":history-engine"))
    implementation(project(":translate-engine"))
    implementation(project(":reader-engine"))
    implementation(project(":ai-engine"))
    implementation(project(":news-engine"))
    implementation(project(":adblock-engine"))
    implementation(project(":security-engine"))
    implementation(project(":privacy-shield-engine"))
    implementation(project(":settings-engine"))
    implementation(project(":permission-engine"))
    implementation(project(":password-engine"))
    implementation(project(":notification-engine"))
    implementation(project(":backup-engine"))
    implementation(project(":network-stats-engine"))
    implementation(project(":extension-engine"))
    implementation(project(":developer-tools-engine"))
    implementation(project(":network-core"))
    implementation(project(":analytics-core"))
    implementation(project(":download-ui-engine"))
    implementation(project(":database-core"))
    implementation(project(":cookie-engine"))
    implementation(project(":video-engine"))
    implementation(project(":audio-engine"))
    implementation(project(":vpn-engine"))
    implementation(project(":private-mode-engine"))
    implementation(project(":web-studio"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core)
}
