import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "tv.corebuilds.launcher"
    compileSdk = 37

    defaultConfig {
        // Distinct from any eventual public package so this can sit beside
        // another launcher build during private testing.
        applicationId = "tv.corebuilds.launcher.prototype"
        // Android 11 (API 30) to the latest: the supported range the owner set
        // for 0.2.0. Every screen is opened on API 30-35 in ScreensOpenTest.
        minSdk = 30
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.0"
    }

    testOptions {
        unitTests {
            // The screen tests run the real activities under Robolectric,
            // which needs the merged resources.
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "2g"
                // Where the screen tests write their 1920 x 1080 renders.
                test.systemProperty("launcher.screens.dir", layout.buildDirectory.dir("screens").get().asFile.absolutePath)
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-private"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
}

dependencies {
    // Test-only; nothing here ships in the APK. Same versions as coreeq/.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.6.1")
}
