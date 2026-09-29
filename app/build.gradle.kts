plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.minimal.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.minimal.launcher"
        minSdk = 26
        targetSdk = 35
        // Auf GitHub Actions steigt die Versionsnummer mit jedem Build, damit Updates sauber installieren.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
    }

    signingConfigs {
        // Fester Schlüssel, damit neue Builds über alte installiert werden können.
        // Für den Play Store eigenen Schlüssel über die SIGNING_*-Umgebungsvariablen setzen.
        create("shared") {
            storeFile = System.getenv("SIGNING_KEYSTORE_PATH")?.let { file(it) }
                ?: rootProject.file("keystore/launcher.keystore")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "launcher"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
