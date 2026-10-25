import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
val keystoreFile = rootProject.file("keystore/reelio.jks")
val media3 = "1.9.0"

android {
    namespace = "com.rady.reelio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rady.reelio"
        minSdk = 26
        targetSdk = 35
        versionCode = 1 + runNumber
        versionName = "1.0.$runNumber"
    }

    signingConfigs {
        create("reelio") {
            storeFile = keystoreFile
            storePassword = "reelio2026"
            keyAlias = "reelio"
            keyPassword = "reelio2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("reelio")
        }
        debug {
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("reelio")
        }
    }

    // One APK per phone type keeps the download small (VLC's engine is large).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Main playback engine + background session
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-common:$media3")
    // Extra audio codecs for the main engine (AC3, DTS, TrueHD, ALAC, ...)
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:$media3+1")
    // Fallback engine for everything else (WMA, WMV, ASF, RMVB, APE, ...)
    implementation("org.videolan.android:libvlc-all:3.7.6")
}
