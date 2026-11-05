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
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"
    }

    testOptions {
        unitTests {
            // The open-the-screen tests run the real activities under
            // Robolectric, which needs the merged resources.
            isIncludeAndroidResources = true
            all { test -> test.maxHeapSize = "2g" }
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
