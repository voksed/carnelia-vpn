plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 34
    namespace = "com.carnelia.vpn"

    defaultConfig {
        applicationId = "com.carnelia.vpn"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-alpha"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.6"
    }

    packagingOptions {
        resources.excludes.add("META-INF/native-image/**")
    }
}

dependencies {
    // Kotlin & Coroutines
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    // Jetpack Compose & Material3
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.1.1")
    implementation("androidx.compose.foundation:foundation:1.6.0")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    // AndroidX Core
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.6.1")
    
    // DataStore for settings persistence
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // VPN & Networking
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    
    // Outline VPN SDK (when available)
    // implementation("org.outline:outline-android:1.0.0")
    
    // WireGuard Android
    // implementation("com.wireguard.android:tunnel:1.0.20231115")

    // JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // Logging
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Testing
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.0")
    testImplementation("junit:junit:4.13.2")
}
