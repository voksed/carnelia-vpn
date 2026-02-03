plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.blinkt.openvpn"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 21
        
        vectorDrawables.useSupportLibrary = true

        buildConfigField("boolean", "openvpn3", "false")
        buildConfigField("String", "FLAVOR", "\"ovpn2\"")
        
        externalNativeBuild {
            cmake {
                // arguments("-DANDROID_STL=c++_shared")
                abiFilters("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        viewBinding = true 
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "build/ovpnassets")
            aidl.srcDirs("src/main/aidl")
            res.srcDirs("src/main/res", "src/ui/res")
            java.srcDirs("src/main/java", "src/ui/java")
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}
