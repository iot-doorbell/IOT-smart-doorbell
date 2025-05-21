plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace = "com.example.doorbell_mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.doorbell_mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // hoặc bản tương thích với Compose bạn dùng
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("org.jitsi.react:jitsi-meet-sdk:+") {
        isTransitive = true
        exclude(group = "com.google.android.exoplayer")
        exclude(group = "com.facebook.react", module = "react-native-video")
    }
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material:material:1.5.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.compose.runtime:runtime:1.5.4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.4")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    kapt("com.github.bumptech.glide:compiler:4.15.1")
}