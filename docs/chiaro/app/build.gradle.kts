plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.callbackdev.chiaro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.callbackdev.chiaro"
        // 33 (Android 13): the system per-app language picker the IT/EN split needs,
        // one runtime path for POST_NOTIFICATIONS, themed icons, full java.time.
        // Same floor as the rest of the series, for the same reasons.
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Shared debug keystore, committed on purpose (see CLAUDE.md): debug builds
        // from CI and from any machine carry one signature and can update an install.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "chiaro-debug"
            keyPassword = "android"
        }

        // Real release key. The keystore lives OUTSIDE the repo; the four properties
        // come from ~/.gradle/gradle.properties locally and from ORG_GRADLE_PROJECT_*
        // env vars (GitHub Secrets) in the release workflow. Created only when fully
        // configured, so a clean checkout still builds.
        val releaseStore = findProperty("CHIARO_KEYSTORE") as String?
        val releaseStorePassword = findProperty("CHIARO_KEYSTORE_PASSWORD") as String?
        val releaseKeyAlias = findProperty("CHIARO_KEY_ALIAS") as String?
        val releaseKeyPassword = findProperty("CHIARO_KEY_PASSWORD") as String?
        if (!releaseStore.isNullOrBlank() && !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // Different app id, so the dev build installs side by side with the
            // release-signed one instead of being uninstallable over it.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // The real key wins whenever configured. Otherwise -PsignReleaseWithDebugKey
            // signs the minified build with the debug key so it can be smoke-tested
            // (R8 breakage shows up nowhere else). Opt-in, so an unconfigured checkout
            // can never produce an installable release by accident.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
                    .takeIf { project.hasProperty("signReleaseWithDebugKey") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // The Application reads VERSION_NAME for the User-Agent it hands the data
        // layer; AGP 8 does not generate BuildConfig unless asked.
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        getByName("main") { java.srcDirs("src/main/kotlin") }
        getByName("test") { java.srcDirs("src/test/kotlin") }
    }
}

dependencies {
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // The stand-in weather icon set until Fase 2 imports Meteocons (ui/icons/ChiaroIcons).
    // R8 keeps only what is referenced, which is a dozen glyphs.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
