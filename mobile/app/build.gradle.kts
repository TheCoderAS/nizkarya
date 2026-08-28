plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI passes these on release builds (-Pnizkarya.versionCode=… -Pnizkarya.versionName=…)
// so every merge to main ships an auto-incremented version.
val appVersionCode = (project.findProperty("nizkarya.versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (project.findProperty("nizkarya.versionName") as String?) ?: "0.1.0-dev"

android {
    namespace = "com.nizkarya.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nizkarya.app"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        // Committed debug-distribution keystore: every APK (local or CI) is
        // signed with the same key so installs upgrade cleanly across builds.
        // This key is for debug distribution only — a Play Store release would
        // use a separate private key.
        create("shared") {
            storeFile = rootProject.file("keystore/nizkarya-debug.keystore")
            storePassword = "nizkarya"
            keyAlias = "nizkarya"
            keyPassword = "nizkarya"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.auth)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

// google-services.json is developer-provided (see mobile/README.md) and kept
// out of git; CI injects it from a secret when configured. Apply the plugin
// only when the file exists so the project always compiles.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
