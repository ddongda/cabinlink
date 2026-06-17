// ═══ 多媒体契约样板 ═══ topic + payload schema + 特殊错误码 + 消费方门面。
// 由多媒体模块团队自治维护（Bridge SDK §9.1）。错误码区间 1000–1999。
plugins { id("com.android.library") }
android {
    namespace = "com.baic.bridge.contract.media"; compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { api(project(":core:lite")) }
