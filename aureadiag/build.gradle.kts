plugins {
    id("com.android.application") version "8.5.2"
}

android {
    namespace = "com.aurea.diagnostics"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.aurea.diagnostics"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
