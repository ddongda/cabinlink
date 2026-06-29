// ═══ 契约约束自动校验 ═══ 扫描所有 :contract:* 校验命名/错误码/NODE 一致性与全局冲突。
// 叶子测试模块（无人依赖它，不破坏依赖方向）。新 contract 接入时：① 这里加一行 testImplementation；
// ② ContractVerifyTest 的 CONTRACTS 清单加一行。CI 跑 :contract-verify:testDebugUnitTest，违规即红。
plugins { id("com.android.library") }
android {
    namespace = "com.baic.bridge.contractverify"; compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    testImplementation(project(":contract:media"))
    testImplementation(project(":contract:usercenter"))
    testImplementation(project(":contract:template"))
    testImplementation("junit:junit:4.13.2")
}
