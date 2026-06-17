// ═══ Bridge 传输层·冻结区 ═══ 全总线唯一跨进程接口（IBridgeNode + BridgeEnvelope）。
// 评审通过后 AIDL 方法签名永不修改；演进只发生在 Envelope 的 type/topic/schemaVersion/payload。
plugins { id("com.android.library") }
android {
    namespace = "com.baic.bridge.transport"; compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { aidl = true }
}
