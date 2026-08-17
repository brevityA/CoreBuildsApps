plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tv.corebuilds.iconpack"
    compileSdk = 34

    defaultConfig {
        applicationId = "tv.corebuilds.iconpack"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            // Supplied by CI (see .github/workflows/build.yml) or a local
            // keystore.properties. Unsigned builds still produce a usable
            // debug APK via assembleDebug.
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (ksPath != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Resource shrinking would strip drawables that are only ever
            // resolved by name at runtime. Keep every icon.
            isMinifyEnabled = false
            isShrinkResources = false
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null && file(ks).exists()) {
                signingConfig = signingConfigs.getByName("release")
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

    androidResources {
        // PNGs are already optimized by the pipeline; don't re-crunch.
        noCompress += listOf("png")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
