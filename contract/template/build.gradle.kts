// ═══ 契约脚手架模板 ═══ 复制本模块改名即得一个新 :contract:<module>。
// 四件套：Schema（topic + payload 字段）/ Contract（NODE 坐标）/ Error（错误码区间，可选）/ Client（消费方门面，可选）。
// 复制后改：namespace、包名、MODULE、topic、NODE 坐标、错误码区间；并在 :contract-verify 的 CONTRACTS 清单登记一行。
plugins { id("com.android.library") }
android {
    namespace = "com.baic.bridge.contract.template"; compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { api(project(":core:lite")) }
