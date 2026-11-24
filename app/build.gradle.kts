import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

val apiBaseUrl = providers.gradleProperty("unwalked.apiBaseUrl").get()
val localProperties = Properties().apply { rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) } }
val mapillaryToken = providers.gradleProperty("unwalked.mapillaryToken").orElse(localProperties.getProperty("MAPILLARY_TOKEN", "")).get()

android {
    namespace = "lt.ehu.unwalked"
    compileSdk = 36

    defaultConfig {
        applicationId = "lt.ehu.unwalked"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "MAPILLARY_TOKEN", "\"$mapillaryToken\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(file("build/h3jni"))
        }
    }

    packaging {
        resources {
            excludes += listOf("android-*/**", "darwin-*/**", "linux-*/**", "windows-*/**", "META-INF/{AL2.0,LGPL2.1}")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.systemProperty("roborazzi.test.record", providers.gradleProperty("screenshots").isPresent.toString())
            }
        }
    }
}

val h3Natives by configurations.creating

val extractH3Natives by tasks.registering(Copy::class) {
    from({ h3Natives.map { zipTree(it) } }) {
        include("android-arm64/*.so", "android-arm/*.so")
        eachFile {
            path = path.replace("android-arm64/", "arm64-v8a/").replace("android-arm/", "armeabi-v7a/")
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("h3jni"))
}

tasks.named("preBuild") { dependsOn(extractH3Natives) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.navigation.compose)
    implementation(libs.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.play.location)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play)
    implementation(libs.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.okhttp.logging)
    implementation(libs.maplibre)
    implementation(libs.h3)
    h3Natives(libs.h3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
}
