import org.gradle.kotlin.dsl.implementation

plugins {

    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")

}

android {
    namespace = "com.example.andapp1"
    compileSdk = 35
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    defaultConfig {
        applicationId = "com.example.andapp1"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
}
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
dependencies {
    //카카오
    implementation("com.kakao.sdk:v2-talk:2.21.1") // 친구 목록 등
    implementation("com.kakao.sdk:v2-share:2.21.1") // 카카오톡 공유
    implementation("com.kakao.sdk:v2-user:2.21.1") // 최신 버전 확인
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation ("com.google.firebase:firebase-storage-ktx")// ✅ Storage 용
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-database:21.0.0")
    implementation("com.google.firebase:firebase-analytics:22.4.0")

    // UI + 라이브러리

    implementation ("androidx.annotation:annotation:1.9.1") // 또는 최신 버전
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation ("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    ksp("androidx.room:room-compiler:2.6.1") // ✅ KSP 사용

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // 내부 프로젝트
    implementation(project(":chatkit"))
    implementation("com.rmtheis:tess-two:9.1.0")
}