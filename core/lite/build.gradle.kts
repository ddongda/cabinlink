// ═══ Bridge 内核·lite ═══ 无 Android Service 组件，提供发现/连接/重连/编解码/RPC/分发/鉴权，
// 统一静态门面 Bridge（宿主可在已有 Service.onBind 返回 Bridge.nodeBinder() 外挂）。全量 core 在此基础上加托管 Service。
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

plugins { id("com.android.library") }

// 构建时取 git 短 hash；无 git / 失败兜底 "unknown"，不让构建中断。
fun gitShortSha(): String = try {
    val out = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = out
        isIgnoreExitValue = true
    }
    out.toString().trim().ifEmpty { "unknown" }
} catch (e: Exception) { "unknown" }

val sdkVersion = "1.0.0"
val gitSha = gitShortSha()
val buildTime: String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

android {
    namespace = "com.baic.bridge.core"; compileSdk = 34
    defaultConfig {
        minSdk = 28
        buildConfigField("String", "SDK_VERSION", "\"$sdkVersion\"")
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    publishing { singleVariant("release") { withSourcesJar() } }
}
dependencies {
    api(project(":transport"))
    testImplementation("junit:junit:4.13.2")
}
