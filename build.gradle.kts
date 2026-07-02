import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

// Root build file — configuration shared by all sub-projects/modules.
plugins {
    id("com.android.application")    version "8.1.4" apply false
    id("com.android.library")        version "8.1.4" apply false
}

// ════════════ SDK Maven 发布（内网 snapshots）════════════
// 坐标：com.baic.bridgesdk:<artifactId>:1.0.0-<gitShortSha>-<yyyyMMdd>-SNAPSHOT
// 仓库地址 / 凭据从 local.properties 读（不入库、不泄密）。请在 local.properties 填：
//   maven.publish.url=http://10.68.14.178:8081/repository/maven-snapshots/
//   maven.publish.username=baic-cicd
//   maven.publish.password=********
// 发布：./gradlew publish          （发全部已配置库到内网 snapshots）
//      ./gradlew :transport:publish（单模块）
//      ./gradlew publishToMavenLocal（发到本地 ~/.m2 验证）
// 各发布模块的 android{} 已声明 publishing { singleVariant("release") { withSourcesJar() } }。
val gitShortSha: String = try {
    val out = ByteArrayOutputStream()
    exec { commandLine("git", "rev-parse", "--short", "HEAD"); standardOutput = out; isIgnoreExitValue = true }
    out.toString().trim().ifEmpty { "nogit" }
} catch (e: Exception) { "nogit" }

val publishProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val sdkGroup = "com.baic.bridgesdk"
val sdkVersion = "1.0.0-$gitShortSha-${SimpleDateFormat("yyyyMMdd").format(Date())}-SNAPSHOT"
val snapshotUrl = publishProps.getProperty("maven.publish.url")
    ?: "http://10.68.14.178:8081/repository/maven-snapshots/"
val mavenUser = publishProps.getProperty("maven.publish.username") ?: ""
val mavenPwd = publishProps.getProperty("maven.publish.password") ?: ""

// 要发布的库模块 → artifactId（samples / contract-verify / contract:media 不发布）
val publishTargets = mapOf(
    ":transport" to "bridge-transport",
    ":core:lite" to "bridge-core-lite",
    ":core:full" to "bridge-core",
    ":contract:template" to "bridge-contract-template",
    ":contract:usercenter" to "bridge-contract-usercenter"
)

publishTargets.forEach { (path, artifact) ->
    project(path) {
        apply(plugin = "maven-publish")
        // afterEvaluate 注册放进 withId 回调——确保晚于 AGP 自身的 afterEvaluate，此时 release 组件才已生成。
        plugins.withId("com.android.library") {
            afterEvaluate {
                extensions.configure<PublishingExtension>("publishing") {
                    publications {
                        create<MavenPublication>("release") {
                            from(components["release"])
                            groupId = sdkGroup
                            artifactId = artifact
                            version = sdkVersion
                        }
                    }
                    repositories {
                        maven {
                            name = "baicSnapshots"
                            isAllowInsecureProtocol = true   // 内网 http
                            url = uri(snapshotUrl)
                            credentials { username = mavenUser; password = mavenPwd }
                        }
                    }
                }
            }
        }
    }
}
