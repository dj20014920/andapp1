import org.gradle.kotlin.dsl.maven

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("com.google.devtools.ksp") version "2.1.20-1.0.32" // ✅ 올바르게 하나만 선언
        id("com.android.application") version "8.5.2" apply false
        id("com.android.library")    version "8.5.2" apply false
        id("org.jetbrains.kotlin.android") version "1.8.21" apply false
        // KSP 등
        id("com.google.devtools.ksp") version "2.1.20-1.0.32" apply false
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
include(":openCVLibrary")
project(":openCVLibrary").projectDir = File("openCVLibrary")