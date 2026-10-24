plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.finnley.pcswitch"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.finnley.pcswitch"
        minSdk = 26
        targetSdk = 35
        versionCode = 25
        versionName = "2.1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    // FileProvider für den APK-Install-Flow (Self-Update)
    implementation("androidx.core:core-ktx:1.15.0")
    // Compose (natives UI, ersetzt den WebView)
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    // Icons für die UI (Visibility, VisibilityOff)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // HA-WebSocket + Update-Download
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
