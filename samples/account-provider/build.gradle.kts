// 账号 Provider 示例：lite·挂已有 Service（Bridge SDK §10.2 用法A）。
plugins { id("com.android.application") }
android {
    namespace = "com.baic.usercenter"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.baic.usercenter"
        minSdk = 28; targetSdk = 34; versionCode = 1; versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(project(":core:lite"))
    implementation(project(":contract:usercenter"))
}
