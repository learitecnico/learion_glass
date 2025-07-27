import java.util.UUID

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.seudominio.app_smart_companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.seudominio.app_smart_companion"
        minSdk = 33
        targetSdk = 35
        // Auto-versioning based on timestamp to ensure fresh installs
        val timestamp = System.currentTimeMillis() / 1000
        versionCode = timestamp.toInt()
        versionName = "1.0.${timestamp}"

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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // AppCompat obrigatório para Vuzix HUD
    implementation("androidx.appcompat:appcompat:1.7.0")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Vuzix SDKs for M400
    implementation("com.vuzix:hud-actionmenu:2.8.4")
    // Note: hud-resources é incluído automaticamente pelo hud-actionmenu
    
    // Vuzix Speech SDK for voice commands (optional - may not be in maven)
    // If not available via maven, download from Vuzix developer portal
    // implementation("com.vuzix:speech-sdk:1.91")
    
    // OkHttp for WebSocket - OpenAI Realtime API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON parsing for OpenAI API
    implementation("org.json:json:20231013")
    
    // WebRTC for OpenAI Realtime API - Multiple options:
    
    // OPTION 1: Manual AAR (Recommended - most stable)
    // 1. Download libwebrtc.aar from: https://github.com/webrtc-sdk/webrtc-android/releases
    // 2. Place in app/libs/ directory
    // 3. Uncomment line below:
    // implementation(files("libs/libwebrtc.aar"))
    
    // OPTION 2: Stream WebRTC (If Option 1 not available)
    // implementation("io.getstream:stream-webrtc-android:1.1.3")
    
    // OPTION 3: WebSocket Alternative (Immediate solution)
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    
    // Required for WebRTC (if using Option 1 or 2)
    implementation("androidx.preference:preference:1.2.1")
    
    // LocalBroadcastManager for Assistant service communication
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    
    // Vosk local speech recognition
    implementation("net.java.dev.jna:jna:5.8.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.34@aar")
    
    // EventBus for transcript events
    implementation("org.greenrobot:eventbus:3.3.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Vosk model preparation tasks
tasks.register("genUUID_pt") {
    val uuid = UUID.randomUUID().toString()
    val odir = file("${layout.buildDirectory.get()}/generated/assets/model-pt-br")
    val ofile = file("$odir/uuid")
    doLast {
        mkdir(odir)
        ofile.writeText(uuid)
    }
}

tasks.register("genUUID_en") {
    val uuid = UUID.randomUUID().toString()
    val odir = file("${layout.buildDirectory.get()}/generated/assets/model-en-us")
    val ofile = file("$odir/uuid")
    doLast {
        mkdir(odir)
        ofile.writeText(uuid)
    }
}

tasks.named("preBuild") {
    dependsOn("genUUID_pt", "genUUID_en")
}