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
        namespace = "com.nexustools.genericui.extensions.graphics.svg"
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
        }

        val nativeDesktopMain = maybeCreate("nativeDesktopMain").apply {
            dependsOn(systemMain)
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
        }

        val androidMain by getting {
            dependsOn(mobileMain)
        }

        val iosMain = maybeCreate("iosMain").apply {
            dependsOn(mobileMain)
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
