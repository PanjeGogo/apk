import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Debug keystore tetap (standar: alias androiddebugkey, password android; bukan rahasia).
// Disimpan sebagai teks base64 di .github/debug-keystore.b64 lalu didekode saat konfigurasi Gradle,
// sehingga setiap build (termasuk CI) memakai tanda tangan yang sama dan APK baru bisa
// menimpa versi lama tanpa uninstall.
val fixedDebugKeystoreB64 = File(rootDir, ".github/debug-keystore.b64")
val fixedDebugKeystore = File(rootDir, ".gradle/fixed-debug.keystore")
if (fixedDebugKeystoreB64.exists()) {
    fixedDebugKeystore.parentFile.mkdirs()
    fixedDebugKeystore.writeBytes(
        Base64.getMimeDecoder().decode(fixedDebugKeystoreB64.readText().trim())
    )
}

android {
    namespace = "com.pramstudio.hudoverlay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.PanjeGoGo.AppOverlay"
        minSdk = 28
        targetSdk = 34
        versionCode = 8
        versionName = "2.2.1"
    }

    signingConfigs {
        getByName("debug") {
            if (fixedDebugKeystore.exists()) {
                storeFile = fixedDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
