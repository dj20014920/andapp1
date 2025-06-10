import org.gradle.kotlin.dsl.maven

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("com.google.devtools.ksp") version "1.8.21-1.0.11"
        id("com.android.application") version "8.5.2" apply false
        id("com.android.library")    version "8.5.2" apply false
        id("org.jetbrains.kotlin.android") version "1.8.21" apply false
    }
}

dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
    }
}

rootProject.name = "AndApp1"
include(":app")
include(":app", ":chatkit")
project(":chatkit").projectDir = File(rootDir, "ChatKit-master/chatkit")
// OpenCV는 이제 Maven Central에서 가져옴
// include(":openCVLibrary")
// project(":openCVLibrary").projectDir = File("openCVLibrary")