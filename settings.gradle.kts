pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("com.google.devtools.ksp") version "2.1.20-1.0.32" // ✅ 올바르게 하나만 선언
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
    }
}

rootProject.name = "AndApp1"
include(":app")
include(":app", ":chatkit")
project(":chatkit").projectDir = File(rootDir, "ChatKit-master/chatkit")
