import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    // Mobile targets — uses native WKWebView (iOS) and android.webkit.WebView (Android).
    // DO NOT USE NJS ON iOS OR ANDROID.
    android {
        namespace = "com.nexustools.genericui.ext.webview.mobile"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(project(":mobile:shared"))
            api(project(":extensions:webview:common"))
            api(project(":extensions:webview:sharedSystem"))
        }
    }
}
