import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "tv.corebuilds.eq"
    compileSdk = 37

    defaultConfig {
        applicationId = "tv.corebuilds.eq"
        minSdk = 30
        targetSdk = 37
        versionCode = 7
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read by the updater: the release feed it polls and the FileProvider
        // authority that hands the downloaded APK to the system installer.
        // Every other app in the suite uses the same shape; the URL is the
        // floating `coreeq` release's manifest on main, which is written by
        // the tag build after the APK is published, never by a version bump.
        buildConfigField(
            "String",
            "UPDATE_AUTHORITY",
            "\"tv.corebuilds.eq.update\"",
        )
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://raw.githubusercontent.com/brevityA/CoreBuildsApps/" +
                "main/Latestrelease/coreeq-version.json\"",
        )
        manifestPlaceholders["fileProviderAuthority"] = "tv.corebuilds.eq.update"
    }

    buildFeatures {
        // AGP 8+ generates no BuildConfig unless asked; the updater reads
        // VERSION_CODE, VERSION_NAME and the two fields above.
        buildConfig = true
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
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null && file(ks).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            // Public main-branch test APKs use the same stable certificate as
            // production so they can be upgraded in place. PR artifacts still
            // use the ephemeral Android debug key because the release key is
            // never decoded outside main pushes and release tags.
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null && file(ks).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // No update feed in the test package. Its id is
            // `tv.corebuilds.eq.debug`, so the production APK could never
            // install over it; advertising an update it cannot install is
            // worse than saying nothing. The Capability screen names the gap.
            buildConfigField("String", "UPDATE_MANIFEST_URL", "\"\"")
            buildConfigField("String", "UPDATE_AUTHORITY", "\"tv.corebuilds.eq.debug.update\"")
            manifestPlaceholders["fileProviderAuthority"] = "tv.corebuilds.eq.debug.update"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 9's built-in Kotlin replaces the external kotlin-android plugin.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json is a stub on the JVM ("not mocked"), so the
    // profile JSON round trip (ProfileJsonTest) brings the real one, as Core
    // Line's tests do.
    testImplementation("org.json:json:20240303")
}
