// 多媒体 Provider 示例：全量形态（自带托管 BridgeNodeService，Bridge SDK §10.1）。
plugins { id("com.android.application") }
android {
    namespace = "com.baic.media"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.baic.media"
        minSdk = 28; targetSdk = 34; versionCode = 1; versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(project(":core:full"))      // 全量：自带 BridgeNodeService
    implementation(project(":contract:media"))
}
