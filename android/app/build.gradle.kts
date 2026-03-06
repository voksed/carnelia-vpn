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
        versionCode = 24 // Incremented for v2
        versionName = "2.0"
        
        setProperty("archivesBaseName", "CarneliaVPN_v${versionName}")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "carnelia123"
            keyAlias = "carnelia"
            keyPassword = "carnelia123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.6"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes.add("META-INF/native-image/**")
            pickFirst("go/**") // Resolve Go class conflict
            pickFirst("go/Seq.class")
            pickFirst("go/Seq$*.class")
            pickFirst("go/Universe.class")
            pickFirst("go/Universe$*.class")
            pickFirst("go/error.class")
        }
    }
}

dependencies {
    // Kotlin & Coroutines
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    // Jetpack Compose & Material3
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // AndroidX Core
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")

    implementation("androidx.lifecycle:lifecycle-runtime:2.6.1")
    
    // DataStore for settings persistence
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // VPN & Networking
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    
    // Tor Android (Allows running Tor without Orbit)
    implementation("info.guardianproject:tor-android:0.4.6.10") 
    implementation("info.guardianproject:jtorctl:0.4")
    
    // I2P Android Client Helper
    implementation("net.i2p.android:helper:0.9.5")

    // Outline VPN SDK (when available)
    // implementation("org.outline:outline-android:1.0.0")
    
    // WireGuard Android
    // implementation("com.wireguard.android:tunnel:1.0.20231115")

    // JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // LibXray (Local AAR) - Disabled to avoid conflict with Outline
    // implementation(files("libs/libv2ray.aar"))
    
    // OpenVPN (ics-openvpn)
    // implementation("com.github.schwabe:ics-openvpn:v0.6.73-production")
    implementation(project(":vpnLib"))

    // Outline Tun2Socks (Must be provided in libs/)
    implementation(files("libs/tun2socks.aar"))
    // implementation("org.getoutline.client:tun2socks:0.0.1")

    // Logging
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("androidx.webkit:webkit:1.9.0")

    // Testing
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.0")
    testImplementation("junit:junit:4.13.2")

    // QR Code
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.2")
}
