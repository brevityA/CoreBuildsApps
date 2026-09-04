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
        versionCode = 5
        versionName = "1.2.0"
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
