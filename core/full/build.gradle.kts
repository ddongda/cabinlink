// ═══ Bridge 内核·全量 ═══ = core:lite + 自带托管 BridgeNodeService（manifest 自动合并）。
// 全新模块依赖本库即可被别人 bind，无需自己声明 Service。
plugins { id("com.android.library") }
android {
    namespace = "com.baic.bridge.core.full"; compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    publishing { singleVariant("release") { withSourcesJar() } }
}
dependencies { api(project(":core:lite")) }
