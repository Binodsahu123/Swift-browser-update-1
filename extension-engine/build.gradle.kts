plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.library)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "com.swift.browser.extensionengine"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":permission-engine"))
    implementation(project(":cookie-engine"))
    implementation(project(":desktop-engine"))
    implementation(project(":tab-engine"))
    implementation(project(":bookmark-engine"))
    implementation(project(":history-engine"))
    implementation(project(":download-engine"))
    implementation(project(":notification-engine"))
    implementation(project(":security-engine"))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(project(":database-core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    
    // Room database dependencies
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Moshi for stable JSON parsing/serialization
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core)
}
