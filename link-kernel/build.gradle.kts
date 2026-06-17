plugins { id("com.android.application") }
android {
    namespace = "com.baic.cabinlink.kernel"; compileSdk = 34
    defaultConfig { applicationId = "com.baic.cabinlink.kernel"; minSdk = 28; targetSdk = 34; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation(project(":link-pipe")) }
