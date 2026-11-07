plugins { id("com.android.application") version "8.5.2" }
android {
    namespace = "com.aurea.diagnostics"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.aurea.diagnostics"
        minSdk = 26
        targetSdk = 28
        versionCode = 2
        versionName = "2.0-native"
        ndk { abiFilters += "armeabi-v7a" }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
            assets.srcDir("src/main/assets")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
