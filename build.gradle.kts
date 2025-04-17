plugins {
        id("com.android.library") version "8.3.2" apply false
        id("com.android.application") version "8.3.2" apply false
        kotlin("android") version "2.1.20" apply false
        kotlin("jvm") version "2.1.20" apply false
        id("com.google.gms.google-services") version "4.4.2" apply false
        id("com.google.devtools.ksp") version "2.1.20-1.0.32" apply false
        }

buildscript {
    repositories {
        google()
        mavenCentral()
        jcenter()
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.9.0") // ✅ 최신 AGP 8.4.0으로 업그레이드
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()

    }
}
