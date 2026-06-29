pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Bridge"

// ══ Bridge SDK（feat/bridge-sdk 去中心化消息总线）══
include(":transport")                  // 唯一 AIDL（冻结区）：IBridgeNode + BridgeEnvelope
include(":core:lite")                  // 内核（无 Service）+ BridgeNodeHost
include(":core:full")                  // = core:lite + BridgeNodeService（托管接入）
include(":contract:usercenter")        // 用户中心/账号契约样板
include(":contract:media")             // 多媒体契约样板（RPC）
include(":contract:template")          // 契约脚手架模板（复制改名即得新 contract）
include(":contract-verify")            // 契约约束自动校验（JUnit，扫描所有 contract）
include(":samples:account-provider")   // 账号 provider（lite·挂已有 service 示例）
include(":samples:navi-consumer")      // 导航 consumer（lite·纯客户端：订阅账号 + 调媒体 RPC）
include(":samples:media-provider")     // 多媒体 provider（全量·自带 service 示例）
