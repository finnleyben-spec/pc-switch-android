import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.finnley.pcswitch"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.finnley.pcswitch"
        minSdk = 26
        targetSdk = 36
        versionCode = 79
        versionName = "3.0.0-alpha.1"
    }

    // Release-Signing: Credentials bleiben lokal in keystore.properties
    // (gitignored, siehe keystore.properties.example). Ohne lokale Keystore-
    // Dateien wird Release mit Debug-Signing gebaut (z. B. für Play-Upload,
    // wo Play App Signing signiert).
    val keystorePropsFile = rootDir.resolve("keystore.properties")
    val keystoreConf: Properties? = if (keystorePropsFile.exists()) {
        Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
    } else null
    val keystoreFile = keystoreConf?.getProperty("KEYSTORE_FILE")?.let { rootDir.resolve(it) }
        ?: rootDir.resolve("pcswitch-release.keystore")
    val hasKeystore = keystoreFile.exists() &&
        !keystoreConf?.getProperty("KEYSTORE_PASSWORD").isNullOrBlank()

    signingConfigs {
        create("release") {
            if (hasKeystore) {
                val p = keystoreConf!!
                storeFile = keystoreFile
                storePassword = p.getProperty("KEYSTORE_PASSWORD").orEmpty()
                keyAlias = p.getProperty("KEY_ALIAS") ?: "pcswitch"
                keyPassword = p.getProperty("KEY_PASSWORD") ?: p.getProperty("KEYSTORE_PASSWORD").orEmpty()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    testOptions {
        unitTests {
            // B6-Logs in HaClient/NetworkWatchdog: android.util.Log in JVM-Tests
            // darf Default-Werte liefern statt Exception zu werfen.
            isReturnDefaultValues = true
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
    // mDNS-Discovery für Home-Assistant (reine Java-Lib, ~100 KB)
    implementation("javax.jmdns:jmdns:3.4.1")
    // DataStore für Switch-Konfiguration
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.datastore:datastore:1.1.1")
    // EncryptedSharedPreferences für Token-Speicherung (security audit)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Baseline profiles for improved startup performance
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.json:json:20231013")
    testImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
