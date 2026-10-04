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
        versionCode = 42
        versionName = "2.0.0"

        // Read by the updater code: where to check for a newer release and
        // which FileProvider authority serves the downloaded APK.
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
        // English-only: resConfigs strips the ~70 translated locales the
        // support libraries ship, which nothing in this app reads.
        resConfigs("en")
        // The square twin (glyphs/) the Banners/Glyphs toggle points
        // launchers at. Empty in the candidate build, which has no companion.
        buildConfigField(
            "String",
            "GLYPHS_PACKAGE",
            "\"tv.corebuilds.iconpack.glyphs\"",
        )
        manifestPlaceholders["glyphsPackage"] = "tv.corebuilds.iconpack.glyphs"
        // github: self-updates from GitHub releases. play: see the play
        // build type below and Distribution.kt.
        buildConfigField("String", "DISTRIBUTION", "\"github\"")
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
            // Debug-signed: it could never pass the companion's signature
            // check against a release-signed Glyphs APK, so it offers none.
            buildConfigField("String", "GLYPHS_PACKAGE", "\"\"")
            manifestPlaceholders["fileProviderAuthority"] = "tv.corebuilds.iconpack.test.update"
        }
    }

    // Keep the production release signing block below separate from the test
    // build type. In particular, no KEYSTORE_* value is read by :test.

    signingConfigs {
        // The Google Play upload key: its own keystore, never the sideload
        // release key (PLAY_KEYSTORE_* in .github/workflows/play.yml). Play
        // App Signing re-signs what users install.
        create("play") {
            val ksPath = System.getenv("PLAY_KEYSTORE_PATH")
            if (ksPath != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("PLAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PLAY_KEY_ALIAS")
                keyPassword = System.getenv("PLAY_KEY_PASSWORD")
            }
        }
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
            // The drawables are only ever resolved by name at runtime
            // (appfilter strings, getIdentifier), which is exactly what the
            // generated res/raw/keep.xml pins — shrinking is safe because
            // the keep set comes from the same catalog as the art.
            isMinifyEnabled = true
            isShrinkResources = true
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null && file(ks).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // The Google Play app: `./gradlew :app:bundlePlay` -> an AAB at
        // app/build/outputs/bundle/play/, built by .github/workflows/play.yml.
        // A separate app from the sideload one: its own application id
        // (tv.corebuilds.iconpack.play), its own Glyphs twin
        // (tv.corebuilds.iconpack.glyphs.play), its own upload key, its own
        // FileProvider authority - so both can sit on one TV and neither ever
        // updates the other. It also drops what Play forbids a Play app to do:
        // no REQUEST_INSTALL_PACKAGES (src/play/AndroidManifest.xml), no
        // GitHub update check, and the Glyphs switch opens the twin's Play
        // listing. A build type, not a flavour, so the sideload release paths
        // CI publishes stay as they are.
        create("play") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".play"
            buildConfigField("String", "DISTRIBUTION", "\"play\"")
            buildConfigField("String", "UPDATE_MANIFEST_URL", "\"\"")
            buildConfigField("String", "UPDATE_AUTHORITY", "\"tv.corebuilds.iconpack.play.update\"")
            manifestPlaceholders["fileProviderAuthority"] = "tv.corebuilds.iconpack.play.update"
            buildConfigField("String", "GLYPHS_PACKAGE", "\"tv.corebuilds.iconpack.glyphs.play\"")
            manifestPlaceholders["glyphsPackage"] = "tv.corebuilds.iconpack.glyphs.play"
            val playKs = System.getenv("PLAY_KEYSTORE_PATH")
            signingConfig = if (playKs != null && file(playKs).exists()) {
                signingConfigs.getByName("play")
            } else {
                null
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
        // Already-compressed formats; don't re-crunch.
        noCompress += listOf("png", "webp")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
