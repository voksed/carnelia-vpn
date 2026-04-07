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
        versionCode = 27
        versionName = "2.3.0"
        setProperty("archivesBaseName", "CarneliaVPN_v2.3.0")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "carnelia123"
            keyAlias = "carnelia"
            keyPassword = "carnelia123"
        }
    }

    flavorDimensions += "edition"

    productFlavors {
        create("vanilla") {
            dimension = "edition"
            buildConfigField("boolean", "WALLET_ENABLED", "false")
        }
        create("wallet") {
            dimension = "edition"
            buildConfigField("boolean", "WALLET_ENABLED", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Only package arm64-v8a — covers 99% of modern Android devices
    // Reduces APK size by eliminating armeabi-v7a/x86/x86_64 from AAR libs (vpnLib, tun2socks)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
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
            excludes.add("META-INF/*.kotlin_module")
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE*")
            excludes.add("META-INF/NOTICE*")
            excludes.add("DebugProbesKt.bin")
            excludes.add("kotlin-tooling-metadata.json")
            // Resolve Go class conflict
            pickFirsts.add("go/**")
            pickFirsts.add("go/Seq.class")
            pickFirsts.add("go/Seq$*.class")
            pickFirsts.add("go/Universe.class")
            pickFirsts.add("go/Universe$*.class")
            pickFirsts.add("go/error.class")
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
    
    // DataStore for settings persistence — removed (VpnConfigRepository unused, using SharedPreferences)

    // VPN & Networking
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

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

    // Testing
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.0")
    testImplementation("junit:junit:4.13.2")

    // QR Code
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.2")

    // TON Wallet — TweetNaCl bundled as source (com.iwebpp.crypto.TweetNaclFast)
    implementation("androidx.security:security-crypto:1.1.0-alpha06") // EncryptedSharedPreferences
    // Image loading for NFT / Jetton icons
    implementation("io.coil-kt:coil-compose:2.5.0")

    // OSM tile map
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
