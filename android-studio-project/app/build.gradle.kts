plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nexora.fit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nexora.fit"
        minSdk = 23
        targetSdk = 35
        versionCode = 28
        versionName = "1.2.8"
    }
}
