pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "CabinLink"

include(":link-pipe")       // 唯一 AIDL（数据面冻结 + 控制面）
include(":link-runtime")    // 双端运行库
include(":link-kernel")     // 控制面 APK（最小版：注册/发现/等待/死亡感知）
include(":contract-car")    // 车控契约 + 手写 Proxy/Skeleton（模式样板）
include(":contract-phone")  // 电话契约
include(":contract-navi")   // 导航契约
include(":contract-media")  // 多媒体契约
include(":sample-car")      // 提供方范例
include(":sample-voice")    // 消费方范例
