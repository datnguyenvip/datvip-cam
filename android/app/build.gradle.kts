plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.datvip.cam"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.datvip.cam"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "1.1.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".kiemthu"
            versionNameSuffix = "-kiemthu"
            resValue("string", "app_name", "Camera HT Thử")
            matchingFallbacks += listOf("release")
        }
        create("leveltest") {
            initWith(getByName("preview"))
            applicationIdSuffix = ".leveltest"
            versionNameSuffix = "-leveltest"
            resValue("string", "app_name", "Camera HT Thước thử")
            matchingFallbacks += listOf("preview", "release")
        }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    val cx = "1.5.3"
    implementation("androidx.camera:camera-core:$cx")
    implementation("androidx.camera:camera-camera2:$cx")
    implementation("androidx.camera:camera-lifecycle:$cx")
    implementation("androidx.camera:camera-view:$cx")
    implementation("androidx.camera:camera-video:$cx")
    implementation("androidx.media3:media3-transformer:1.8.0")
    implementation("androidx.media3:media3-effect:1.8.0")
    implementation("androidx.media3:media3-common:1.8.0")
    implementation("com.google.guava:guava:32.1.3-android")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
