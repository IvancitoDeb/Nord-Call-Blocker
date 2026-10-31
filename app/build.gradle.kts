plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.nordcallblocker"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.example.nordcallblocker"
        minSdk = 29
        targetSdk = 36
        versionCode = 7
        versionName = "1.6"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
