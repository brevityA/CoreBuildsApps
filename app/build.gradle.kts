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
        versionCode = 29
        versionName = "1.8.19"

        // Read by the shared updater code, which :pop also compiles. Values
        // are the classic pack's existing ones, so behaviour is unchanged.
        buildConfigField(
            "String",
            "UPDATE_AUTHORITY",
            "\"tv.corebuilds.iconpack.update\"",
        )
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://raw.githubusercontent.com/brevityA/CoreBuildsApps/" +
                "main/Latestrelease/version.json\"",
        )
        manifestPlaceholders["fileProviderAuthority"] = "tv.corebuilds.iconpack.update"
    }

    // This variant is intentionally separate from both production release and
    // the ordinary debug build. It is for maintainer-controlled Android TV
    // testing only: the suffix makes it install beside the production pack,
    // while initWith(debug) guarantees the production signing configuration is
    // never consulted.
    buildTypes {
        create("candidate") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".test"
            val sourceCommit = providers.gradleProperty("testSourceCommit").orElse("local").get()
            versionNameSuffix = "-test.$sourceCommit"
            resValue("string", "app_name", "Core Builds Icon Pack – Test")
            buildConfigField("String", "TEST_SOURCE_COMMIT", "\"$sourceCommit\"")
            buildConfigField("String", "UPDATE_AUTHORITY", "\"tv.corebuilds.iconpack.test.update\"")
            manifestPlaceholders["fileProviderAuthority"] = "tv.corebuilds.iconpack.test.update"
        }
    }

    // Keep the production release signing block below separate from the test
    // build type. In particular, no KEYSTORE_* value is read by :test.

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

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        // PNGs are already optimized by the pipeline; don't re-crunch.
        noCompress += listOf("png")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
