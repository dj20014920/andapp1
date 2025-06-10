import org.gradle.kotlin.dsl.implementation

plugins {
    id("com.google.devtools.ksp")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
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
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
dependencies {
    //카카오
    implementation("com.kakao.sdk:v2-talk:2.21.1")
    implementation("com.kakao.sdk:v2-share:2.21.1")
    implementation("com.kakao.sdk:v2-user:2.21.1")
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation ("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-database:21.0.0")
    implementation("com.google.firebase:firebase-analytics:22.4.0")

    // UI + 라이브러리

    implementation ("androidx.annotation:annotation:1.7.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation ("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    ksp("androidx.room:room-compiler:2.6.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // 내부 프로젝트
    implementation(project(":chatkit"))
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")
    // OpenCV Maven Central 의존성 (4.9.0+)
    implementation("org.opencv:opencv:4.10.0")
    implementation ("com.airbnb.android:lottie:6.4.0")

    //채팅 메시지 알림
    implementation ("com.google.firebase:firebase-messaging")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // CameraX 의존성
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

}