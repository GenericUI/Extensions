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
        namespace = "com.nexustools.genericui.ext.alien.umbrella"
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
            api(project(":extensions:canvas2d"))
            api(project(":extensions:alien:common"))
        }
        jsMain.dependencies {
            api(project(":extensions:alien:web"))
        }
        val wasmJsMain by getting {
            dependencies {
                api(project(":extensions:alien:web"))
            }
        }
        jvmMain.dependencies {
            api(project(":extensions:alien:systemCommon"))
        }
        androidMain.dependencies {
            api(project(":extensions:alien:systemCommon"))
        }
        mingwX64Main.dependencies {
            api(project(":extensions:alien:systemCommon"))
        }
        linuxX64Main.dependencies {
            api(project(":extensions:alien:systemCommon"))
        }
        linuxArm64Main.dependencies {
            api(project(":extensions:alien:systemCommon"))
        }
        macosArm64Main.dependencies {
            api(project(":extensions:alien:systemCommon"))
        }
        macosX64Main.dependencies {
            api(project(":extensions:alien:systemCommon"))
        }
        iosArm64Main.dependencies {
            api(project(":extensions:alien:systemCommon"))
        }
        iosSimulatorArm64Main.dependencies {
            api(project(":extensions:alien:systemCommon"))
        }
    }
}
