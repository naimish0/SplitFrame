plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val testBannerAdUnitId = "ca-app-pub-3940256099942544/6300978111"
val testNativeAdUnitId = "ca-app-pub-3940256099942544/2247696110"
val testInterstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712"
val testAppOpenAdUnitId = "ca-app-pub-3940256099942544/9257395921"
val productionAdMobAppId = "ca-app-pub-7742442202074564~8952429340"
val productionBannerAdUnitId = "ca-app-pub-7742442202074564/4826170430"
val productionNativeAdUnitId = "ca-app-pub-7742442202074564/8964605851"
val productionInterstitialAdUnitId = "ca-app-pub-7742442202074564/4574200638"
val productionAppOpenAdUnitId = "ca-app-pub-7742442202074564/3863754196"

android {
    namespace = "com.example.splitframe"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.splitframe"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Use the real app ID in every variant so UMP can load this app's published
        // privacy messages. Debug builds still use Google's test ad unit IDs below.
        manifestPlaceholders["adMobAppId"] = productionAdMobAppId
        buildConfigField("String", "BANNER_AD_UNIT_ID", "\"$testBannerAdUnitId\"")
        buildConfigField("String", "NATIVE_AD_UNIT_ID", "\"$testNativeAdUnitId\"")
        buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"$testInterstitialAdUnitId\"")
        buildConfigField("String", "APP_OPEN_AD_UNIT_ID", "\"$testAppOpenAdUnitId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            manifestPlaceholders["adMobAppId"] = productionAdMobAppId
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"$productionBannerAdUnitId\"")
            buildConfigField("String", "NATIVE_AD_UNIT_ID", "\"$productionNativeAdUnitId\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"$productionInterstitialAdUnitId\"")
            buildConfigField("String", "APP_OPEN_AD_UNIT_ID", "\"$productionAppOpenAdUnitId\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)
    implementation(libs.google.ump)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
