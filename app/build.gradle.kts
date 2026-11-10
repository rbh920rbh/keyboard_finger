plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.keyboardtouchdemo"
    compileSdk = 35

    signingConfigs {
        create("demoDebug") {
            storeFile = file("demo-debug.keystore")
            storePassword = "keyboardtouch"
            keyAlias = "keyboardtouch"
            keyPassword = "keyboardtouch"
        }
    }

    defaultConfig {
        applicationId = "com.example.keyboardtouchdemo"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(1) ?: 2
        versionName = "0.2"
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("demoDebug")
        }
    }
}

kotlin {
    jvmToolchain(17)
}
