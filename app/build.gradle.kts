plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.keyboardtouchdemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.keyboardtouchdemo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
}

kotlin {
    jvmToolchain(17)
}
