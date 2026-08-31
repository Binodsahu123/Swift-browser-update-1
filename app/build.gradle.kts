plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.swift.browser"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.swift.browser"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val hasKeystore = file(keystorePath).exists() && System.getenv("STORE_PASSWORD") != null
      if (hasKeystore) {
        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
      enableV1Signing = true
      enableV2Signing = true
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
    implementation(project(":reader-engine"))
    implementation(project(":ai-engine"))
    implementation(project(":database-core"))
  implementation(project(":web-studio"))
  implementation("androidx.media:media:1.7.0")
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation("androidx.core:core-splashscreen:1.0.1")
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.webkit)
  implementation(libs.coil.compose)
  implementation(libs.coil.video)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  
  implementation(project(":ui-core"))
  implementation(project(":desktop-engine"))
  implementation(project(":analytics-core"))
  implementation(project(":browser-engine"))
  implementation(project(":security-engine"))
  implementation(project(":settings-engine"))
  implementation(project(":news-engine"))
  implementation(project(":reader-engine"))
  implementation(project(":bookmark-engine"))
  implementation(project(":history-engine"))
  implementation(project(":cookie-engine"))
  implementation(project(":tab-engine"))
  implementation(project(":search-engine"))
  implementation(project(":extension-engine"))
  implementation(project(":permission-engine"))
  implementation(project(":adblock-engine"))
  implementation(project(":translate-engine"))
  implementation(project(":notification-engine"))
  implementation(project(":image-engine"))
  implementation(project(":video-engine"))
  implementation(project(":audio-engine"))
  implementation(project(":developer-tools-engine"))
  implementation(project(":download-engine"))
  implementation(project(":download-ui-engine"))
  implementation(project(":web-studio"))
  implementation(project(":weather-engine"))
  implementation(project(":antivirus-engine"))
  implementation(project(":data-saver-engine"))
  implementation(project(":privacy-shield-engine"))
  implementation(project(":network-stats-engine"))
  implementation(project(":battery-saver-engine"))
  implementation(project(":network-core"))
  implementation(project(":vpn-engine"))
  implementation(project(":password-engine"))
  implementation(project(":private-mode-engine"))
}

tasks.register("copyApkToOutputs") {
    val targetRootDir = rootDir
    val targetBuildDir = layout.buildDirectory.get().asFile
    
    doLast {
        val visibleDir = File(targetRootDir, "build-outputs")
        val hiddenDir = File(targetRootDir, ".build-outputs")
        
        visibleDir.mkdirs()
        hiddenDir.mkdirs()
        
        val debugApkFile = File(targetBuildDir, "outputs/apk/debug/app-debug.apk")
        if (debugApkFile.exists()) {
            debugApkFile.copyTo(File(visibleDir, "app-debug.apk"), overwrite = true)
            debugApkFile.copyTo(File(hiddenDir, "app-debug.apk"), overwrite = true)
            logger.lifecycle("Debug APK copied to visible 'build-outputs' and hidden '.build-outputs' directories.")
        }
        
        val releaseApkFile = File(targetBuildDir, "outputs/apk/release/app-release.apk")
        if (releaseApkFile.exists()) {
            releaseApkFile.copyTo(File(visibleDir, "app-release.apk"), overwrite = true)
            releaseApkFile.copyTo(File(hiddenDir, "app-release.apk"), overwrite = true)
            logger.lifecycle("Release APK copied to visible 'build-outputs' and hidden '.build-outputs' directories.")
        }
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("copyApkToOutputs")
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("copyApkToOutputs")
}
