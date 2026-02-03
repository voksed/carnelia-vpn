pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.2.0"
        id("com.android.library") version "8.2.0"
        kotlin("android") version "1.9.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "carnelia-vpn"

include(":app")
include(":vpnLib")
project(":vpnLib").projectDir = file("vpnLib/main")
