// ═══ Bridge 内核·lite ═══ 无 Android Service 组件，提供发现/连接/重连/编解码/RPC/分发/鉴权，
// 统一静态门面 Bridge（宿主可在已有 Service.onBind 返回 Bridge.nodeBinder() 外挂）。全量 core 在此基础上加托管 Service。
plugins { id("com.android.library") }
android {
    namespace = "com.baic.bridge.core"; compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { api(project(":transport")) }
