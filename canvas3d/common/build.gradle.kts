import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    android {
        namespace = "com.nexustools.genericui.ext.canvas3d"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    mingwX64()
    linuxX64()
    linuxArm64()
    macosArm64()
    macosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            implementation(libs.kotlinx.coroutines.core)
            api(project(":extensions:gpu:common"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
