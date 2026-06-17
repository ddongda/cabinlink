plugins { id("com.android.library") }
android {
    namespace = "com.baic.contract.car"; compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { api(project(":link-runtime")) }
