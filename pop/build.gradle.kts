plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Core Builds Pop — the pop-art cartoon icon pack.
//
// A second module rather than a second flavour of :app, and a second APK
// rather than a second style inside one:
//
//   * Launchers identify an icon pack by package. Two packages means a user
//     can install both and switch in Projectivy's own list, which is how every
//     other pack on the platform behaves.
//   * One APK carrying both styles would ship ~3,700 drawables and roughly
//     double in size for everyone, including the majority who only want one
//     look, and Projectivy's icon browser would list every app twice.
//   * :app keeps its own build path, CI, APK names and release tags untouched,
//     so nothing about the shipping product changes to make room for this one.
//
// What is deliberately NOT duplicated: the Kotlin. Both modules compile the
// same source tree. The per-pack differences are two BuildConfig fields and
// the generated resources.
android {
    namespace = "tv.corebuilds.iconpack"
    compileSdk = 34

    defaultConfig {
        applicationId = "tv.corebuilds.iconpack.pop"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // FileProvider authorities must be unique across installed packages,
        // or the second install fails with INSTALL_FAILED_CONFLICTING_PROVIDER
        // and the user is told nothing useful. Shared code reads this rather
        // than a hardcoded constant.
        buildConfigField(
            "String",
            "UPDATE_AUTHORITY",
            "\"tv.corebuilds.iconpack.pop.update\"",
        )
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://raw.githubusercontent.com/brevityA/CoreBuildsApps/" +
                "main/Latestrelease/pop-version.json\"",
        )
    }

    signingConfigs {
        create("release") {
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

    sourceSets {
        getByName("main") {
            // One copy of the app, two packs. tools/build_pop.py mirrors the
            // non-generated resources this code needs (layouts, shape
            // drawables, theme, colours) into pop/src/main/res and CI diffs
            // the result, so the mirror cannot silently drift.
            java.srcDirs("../app/src/main/java")
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
        // PNGs are already indexed and optimized by tools/build_pop.py.
        noCompress += listOf("png")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
