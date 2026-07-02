// 导航 Consumer 示例：lite·纯客户端（Bridge SDK §10.2 用法B）。只订阅账号 + 主动拉取，不提供能力。
plugins { id("com.android.application") }
android {
    namespace = "com.baic.navi"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.baic.navi"
        minSdk = 28; targetSdk = 34; versionCode = 1; versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(project(":core:lite"))
    implementation(project(":contract:usercenter"))
    implementation(project(":contract:media"))
}
