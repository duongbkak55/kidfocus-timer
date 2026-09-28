import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Load keystore.properties if it exists (local dev); CI uses env vars via signingConfig below
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

fun configValue(name: String): String = (System.getenv(name)
    ?: localProps.getProperty(name, ""))
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val revenueCatDebugKey = configValue("REVENUECAT_TEST_API_KEY")
    .ifBlank { configValue("REVENUECAT_ANDROID_API_KEY") }
val firebaseReleaseAppId = configValue("FIREBASE_APP_ID")
val firebaseDebugAppId = configValue("FIREBASE_DEBUG_APP_ID")
    .ifBlank { firebaseReleaseAppId }

android {
    namespace = "com.kidfocus.timer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kidfocusstudio.timer"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.0-rc.2"
        resourceConfigurations += listOf("vi", "en")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            keyAlias     = (keystoreProperties["keyAlias"]     ?: System.getenv("KEY_ALIAS")      ?: "") as String
            keyPassword  = (keystoreProperties["keyPassword"]  ?: System.getenv("KEY_PASSWORD")   ?: "") as String
            storePassword = (keystoreProperties["storePassword"] ?: System.getenv("STORE_PASSWORD") ?: "") as String
            val storePath = (keystoreProperties["storeFile"] ?: System.getenv("KEYSTORE_PATH")) as String?
            if (!storePath.isNullOrBlank()) storeFile = file(storePath)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "REVENUECAT_ANDROID_API_KEY", "\"${configValue("REVENUECAT_ANDROID_API_KEY")}\"")
            buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseReleaseAppId\"")
            buildConfigField("boolean", "ENABLE_AI_CHAT", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            buildConfigField("String", "REVENUECAT_ANDROID_API_KEY", "\"$revenueCatDebugKey\"")
            buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseDebugAppId\"")
            buildConfigField("boolean", "ENABLE_AI_CHAT", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    sourceSets {
        getByName("main").assets.srcDir(rootProject.file("shared/learning/www"))
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    // Firebase values are safe client identifiers. OpenRouter secrets live only in Cloud Functions.
    defaultConfig {
        buildConfigField("String", "FIREBASE_API_KEY", "\"${configValue("FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${configValue("FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_WEB_CLIENT_ID", "\"${configValue("FIREBASE_WEB_CLIENT_ID")}\"")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.lifecycle:lifecycle-process:${libs.versions.lifecycleRuntimeKtx.get()}")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Google Sign-In + Gemini AI
    implementation(libs.play.services.auth)
    implementation(libs.okhttp)

    // Optional cloud sync. Firebase is initialized only when local config is present.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.coil.compose)
    implementation(libs.revenuecat.purchases)
    implementation(libs.androidx.webkit)

    // Testing
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation("androidx.navigation:navigation-testing:${libs.versions.navigationCompose.get()}")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
