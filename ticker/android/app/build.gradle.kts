plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.corebuilds.line"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.corebuilds.line"
        minSdk = 24
        targetSdk = 34
        versionCode = 12
        versionName = "1.4.4"
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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null && file(ks).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // A debug APK is signed with the SDK's debug key, never the release
        // key. Under the release package name Android refuses it over an
        // installed release ("package conflicts with an existing package"),
        // so it gets its own id and installs beside the real app, the way
        // Core EQ's does. versionName stays unsuffixed: the device check
        // compares it with this file.
        debug {
            applicationIdSuffix = ".debug"
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
}

dependencies {
    // FileProvider + main-executor for the sideload updater (UpdateManager.kt).
    implementation("androidx.core:core-ktx:1.13.1")

    // JVM unit tests for the playlist/guide parsers. android.jar only stubs
    // XmlPullParser and org.json, so the tests bring real implementations.
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    testImplementation("org.json:json:20240303")
}

android.testOptions.unitTests.all {
    // The large-guide benchmark reads its size from here (MB); CI keeps the default.
    it.systemProperty("coreline.guideMb", System.getProperty("coreline.guideMb") ?: "120")
    it.maxHeapSize = "512m"
}

val webPublic = rootProject.file("../public")
val webLib = rootProject.file("../lib")
val assetsOut = layout.projectDirectory.dir("src/main/assets/www")

val syncWebAssets = tasks.register<Copy>("syncWebAssets") {
    description = "Copy Core Line web UI + parsers into Android assets"
    from(webPublic)
    from(webLib) { into("lib") }
    into(assetsOut)
    exclude("sw.js")
}

tasks.named("preBuild").configure { dependsOn(syncWebAssets) }
