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
        namespace = "com.nexustools.genericui.ext.imageprocessing.umbrella"
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
            api(project(":extensions:gpu"))
            api(project(":extensions:imageprocessing:common"))
        }
        jsMain.dependencies {
            api(project(":extensions:imageprocessing:web"))
        }
        val wasmJsMain by getting {
            dependencies {
                api(project(":extensions:imageprocessing:web"))
            }
        }
        jvmMain.dependencies {
            api(project(":extensions:imageprocessing:systemCommon"))
        }
        androidMain.dependencies {
            api(project(":extensions:imageprocessing:systemCommon"))
        }
        mingwX64Main.dependencies {
            api(project(":extensions:imageprocessing:systemCommon"))
        }
        linuxX64Main.dependencies {
            api(project(":extensions:imageprocessing:systemCommon"))
        }
        linuxArm64Main.dependencies {
            api(project(":extensions:imageprocessing:systemCommon"))
        }
        macosArm64Main.dependencies {
            api(project(":extensions:imageprocessing:systemCommon"))
        }
        macosX64Main.dependencies {
            api(project(":extensions:imageprocessing:systemCommon"))
        }
        iosArm64Main.dependencies {
            api(project(":extensions:imageprocessing:systemCommon"))
        }
        iosSimulatorArm64Main.dependencies {
            api(project(":extensions:imageprocessing:systemCommon"))
        }
    }
}
