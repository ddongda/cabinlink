plugins { id("com.android.library") }
android {
    namespace = "com.baic.cabinlink.pipe"; compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { aidl = true }
}
