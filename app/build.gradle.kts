import java.io.FileInputStream
import java.util.Properties

/*
 * Bubble Blast - application module build script.
 *
 * Release signing:
 *   Create `keystore.properties` in the repo root (it is git-ignored):
 *
 *     storeFile=release-keystore.jks
 *     storePassword=******
 *     keyAlias=bubbleblast
 *     keyPassword=******
 *
 *   When the file is absent, release builds are signed with the debug key so that
 *   `./gradlew assembleRelease` still produces an installable APK for testing.
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.infinitehits.bubbleblast"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.infinitehits.bubbleblast"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Only ship the resources we actually localise.
        resourceConfigurations += listOf("en")

        vectorDrawables.useSupportLibrary = true

        // Bump these when wiring a real ad provider (see docs/ADS_INTEGRATION.md).
        buildConfigField("String", "AD_PROVIDER", "\"none\"")
        buildConfigField("boolean", "ADS_ENABLED", "false")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Falls back to the debug key when no release keystore is configured.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        // BuildConfig is needed for the ad provider flags declared above.
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/LICENSE*",
            "META-INF/*.kotlin_module",
            "DebugProbesKt.bin"
        )
    }

    bundle {
        // The game is fully offline - ship every level and asset in the base module.
        language.enableSplit = false
        density.enableSplit = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")

    // ---------------------------------------------------------------------
    // Ads: intentionally NOT wired up yet. When you are ready, uncomment the
    // dependency below and follow docs/ADS_INTEGRATION.md.
    // implementation("com.google.android.gms:play-services-ads:23.5.0")
    // ---------------------------------------------------------------------

    testImplementation("junit:junit:4.13.2")
}
