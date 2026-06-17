// ═══ 用户中心/账号契约样板 ═══ topic + payload schema + 特殊错误码 + 消费方门面。
// 由用户中心模块团队自治维护（Bridge SDK §9.1）；SDK 仅提供 core 内核。
plugins { id("com.android.library") }
android {
    namespace = "com.baic.bridge.contract.usercenter"; compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { api(project(":core:lite")) }
