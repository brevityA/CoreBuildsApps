plugins {
    id("com.android.application")
}

// Core Builds Glyphs — the icon pack's square twin, for launcher auto-apply.
//
// Launchers read an icon pack's appfilter out of the pack's own APK, so one
// package can only ever auto-apply one art style. The icon pack (:app) maps
// every component to its 16:9 banner, the default since 1.9.5; this package
// maps the same components to the same app's square glyph. The
// Banners/Glyphs toggle in :app chooses which package the launcher is told
// to apply, and installs this one from the matching GitHub release the first
// time Glyphs is picked.
//
// Deliberately thin:
//   * No Kotlin, no library dependencies. One Java activity exists only
//     because launchers discover packs through activity intent filters.
//   * No art in git. The glyphs and fallback furniture are copied from
//     :app's generated drawable-nodpi at build time (see copyGlyphArt), so
//     there is exactly one committed copy of each art file and the two
//     packages cannot drift apart.
//   * No version of its own. versionCode/versionName are read from
//     app/build.gradle.kts: the companion is released with the icon pack,
//     attached to the same v* release, and :app asks for exactly its own
//     version, so the two always ship and install as a pair.

val appGradle = rootProject.file("app/build.gradle.kts").readText()
val packVersionCode = Regex("""versionCode\s*=\s*(\d+)""").find(appGradle)
    ?.groupValues?.get(1)?.toInt()
    ?: error("versionCode not found in app/build.gradle.kts")
val packVersionName = Regex("""versionName\s*=\s*"([^"]+)"""").find(appGradle)
    ?.groupValues?.get(1)
    ?: error("versionName not found in app/build.gradle.kts")

val generatedRes = layout.buildDirectory.dir("generated/glyph-art/res")

// The art this package ships, copied rather than committed twice. Glyphs
// are what the appfilter maps to; cb_back_*/cb_mask/cb_upon are the fallback
// furniture the appfilter names for apps the catalog does not cover; the
// launcher/TV branding is the icon pack's own.
val copyGlyphArt = tasks.register<Sync>("copyGlyphArt") {
    from(rootProject.file("app/src/main/res/drawable-nodpi")) {
        include("*.webp", "cb_banner.png")
        // The square set only; banners are the icon pack's own art.
        exclude("*_banner.webp")
        into("drawable-nodpi")
    }
    // Identical glyphs ship once via aliases; without this file the
    // companion names drawables that resolve nowhere.
    from(rootProject.file("app/src/main/res/values")) {
        include("aliases.xml")
        into("values")
    }
    from(rootProject.file("app/src/main/res")) {
        include("mipmap-*/**")
    }
    into(generatedRes)
}

android {
    namespace = "tv.corebuilds.iconpack.glyphs"
    compileSdk = 34

    defaultConfig {
        applicationId = "tv.corebuilds.iconpack.glyphs"
        minSdk = 21
        targetSdk = 34
        versionCode = packVersionCode
        versionName = packVersionName
    }

    signingConfigs {
        create("release") {
            // Same keystore as :app. :app refuses to install a companion whose
            // signing certificate differs from its own.
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
            // Every drawable is resolved by name at runtime by a launcher.
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
            res.srcDir(generatedRes)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += listOf("png")
    }
}

tasks.named("preBuild") { dependsOn(copyGlyphArt) }
