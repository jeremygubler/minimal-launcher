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
        // Fester Schlüssel der Sideload-Version, damit neue Builds über alte installiert werden können.
        create("shared") {
            storeFile = rootProject.file("keystore/launcher.keystore")
            storePassword = "android"
            keyAlias = "launcher"
            keyPassword = "android"
        }
        // Upload-Schlüssel für Google Play – kommt ausschließlich aus Umgebungsvariablen (GitHub Secrets).
        create("upload") {
            // Nicht gesetzte GitHub Secrets kommen als leerer Text an – dann Testschlüssel verwenden.
            fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
            val path = env("UPLOAD_KEYSTORE_PATH")
            storeFile = path?.let { file(it) } ?: rootProject.file("keystore/launcher.keystore")
            storePassword = if (path != null) env("UPLOAD_STORE_PASSWORD") else "android"
            keyAlias = if (path != null) env("UPLOAD_KEY_ALIAS") else "launcher"
            keyPassword = if (path != null) env("UPLOAD_KEY_PASSWORD") else "android"
        }
    }

    // Zwei Varianten aus demselben Code:
    // sideload = APK für GitHub (alles frei), play = Google-Play-Version mit Pro-Freischaltung.
    flavorDimensions += "store"
    productFlavors {
        create("sideload") {
            dimension = "store"
            buildConfigField("boolean", "STORE_BUILD", "false")
            resValue("string", "app_name", providers.gradleProperty("launcherName").getOrElse("Kanso"))
            signingConfig = signingConfigs.getByName("shared")
        }
        create("play") {
            dimension = "store"
            applicationId = providers.gradleProperty("playApplicationId").getOrElse("ch.hazzar.kanso")
            buildConfigField("boolean", "STORE_BUILD", "true")
            resValue("string", "app_name", providers.gradleProperty("launcherName").getOrElse("Kanso"))
            signingConfig = signingConfigs.getByName("upload")
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
            // Signatur kommt aus der jeweiligen Variante (sideload: fester Schlüssel, play: Upload-Schlüssel).
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
    // Die Tests prüfen die deutschen Texte; Englisch wird gezielt in LangTest geprüft.
    testOptions {
        unitTests.all { it.jvmArgs("-Duser.language=de", "-Duser.country=CH") }
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

    // Google Play Billing nur in der Play-Variante.
    "playImplementation"("com.android.billingclient:billing-ktx:7.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
