import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    android {
        namespace = "com.nexustools.genericui.extensions.graphics.canvas2d"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()

    macosArm64()

    mingwX64()

    linuxX64()
    linuxArm64()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":common"))
                api(project(":extensions:graphics:gpu"))
                api(project(":extensions:graphics:imageprocessing"))
                api(project(":extensions:ui:overlay"))
                api(libs.kotlinx.coroutines.core)
            }
        }

        val systemMain = maybeCreate("systemMain").apply {
            dependsOn(commonMain)
            dependencies {
                api(project(":system"))
            }
        }

        val jvmMain by getting {
            dependsOn(systemMain)
            dependencies {
                api(project(":desktop"))
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }

        val nativeDesktopMain = maybeCreate("nativeDesktopMain").apply {
            dependsOn(systemMain)
            dependencies {
                api(project(":desktop"))
            }
        }

        val mingwMain = maybeCreate("mingwMain").apply {
            dependsOn(nativeDesktopMain)
        }
        val mingwX64Main by getting {
            dependsOn(mingwMain)
        }

        val macosMain = maybeCreate("macosMain").apply {
            dependsOn(nativeDesktopMain)
        }
        val macosArm64Main by getting {
            dependsOn(macosMain)
        }

        val linuxMain = maybeCreate("linuxMain").apply {
            dependsOn(nativeDesktopMain)
        }
        val linuxX64Main by getting {
            dependsOn(linuxMain)
        }
        val linuxArm64Main by getting {
            dependsOn(linuxMain)
        }

        val mobileMain = maybeCreate("mobileMain").apply {
            dependsOn(systemMain)
            dependencies {
                api(project(":mobile"))
            }
        }

        val androidMain by getting {
            dependsOn(mobileMain)
            dependencies {
                api(project(":jvm"))
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.recyclerview)
                implementation(libs.google.material)
                implementation(libs.kotlinx.coroutinesAndroid)
            }
        }

        val iosMain = maybeCreate("iosMain").apply {
            dependsOn(mobileMain)
            dependencies {
                api(project(":apple"))
            }
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }

        val webMain = maybeCreate("webMain").apply {
            dependsOn(commonMain)
            dependencies {
                api(project(":web"))
            }
        }
        val jsMain by getting {
            dependsOn(webMain)
        }
        val wasmJsMain by getting {
            dependsOn(webMain)
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jvmTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.kotlin.testJunit)
            }
        }

        val webTest = maybeCreate("webTest").apply {
            dependsOn(commonTest)
        }
        val jsTest by getting {
            dependsOn(webTest)
        }
        val wasmJsTest by getting {
            dependsOn(webTest)
        }
    }
}
