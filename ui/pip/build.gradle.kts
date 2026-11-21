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
        namespace = "com.nexustools.genericui.extensions.ui.pip"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTest {
            isIncludeAndroidResources = true
        }

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
                api(project(":extensions:ui:overlay"))
                api(libs.kotlinx.coroutines.core)
            }
        }

        val systemMain by creating {
            dependsOn(commonMain)
            dependencies {
                api(project(":system"))
            }
        }

        val jvmMain by getting {
            dependsOn(systemMain)
            dependencies {
                api(project(":desktop"))
                api(project(":jvm"))
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }

        val appleMain by creating {
            dependsOn(systemMain)
            dependencies {
                api(project(":apple"))
            }
        }

        val nativeDesktopMain by creating {
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
            dependsOn(appleMain)
            dependencies {
                api(project(":apple"))
            }
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

        val mobileMain by creating {
            dependsOn(systemMain)
            dependencies {
                api(project(":mobile"))
            }
        }

        val androidMain by getting {
            dependsOn(mobileMain)
            dependencies {
                api(project(":jvm"))
                api(libs.androidx.core.ktx)
                api(libs.androidx.appcompat)
                api(libs.kotlinx.coroutinesAndroid)
            }
        }

        val iosMain = maybeCreate("iosMain").apply {
            dependsOn(mobileMain)
            dependsOn(appleMain)
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

        val webMain by creating {
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

        val systemTest by creating {
            dependsOn(commonTest)
        }

        val jvmTest by getting {
            dependsOn(systemTest)
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation(project(":extensions:media:media"))
            }
        }

        val appleTest by creating {
            dependsOn(systemTest)
        }

        val nativeDesktopTest by creating {
            dependsOn(systemTest)
        }

        val mingwTest = maybeCreate("mingwTest").apply {
            dependsOn(nativeDesktopTest)
        }

        val mingwX64Test by getting {
            dependsOn(mingwTest)
        }

        val macosTest = maybeCreate("macosTest").apply {
            dependsOn(nativeDesktopTest)
            dependsOn(appleTest)
        }

        val macosArm64Test by getting {
            dependsOn(macosTest)
        }

        val linuxTest = maybeCreate("linuxTest").apply {
            dependsOn(nativeDesktopTest)
        }

        val linuxX64Test by getting {
            dependsOn(linuxTest)
        }

        val linuxArm64Test by getting {
            dependsOn(linuxTest)
        }

        val mobileTest by creating {
            dependsOn(systemTest)
        }

        val androidHostTest by getting {
            dependsOn(mobileTest)
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation("org.robolectric:robolectric:4.16.1")
            }
        }

        val iosTest = maybeCreate("iosTest").apply {
            dependsOn(mobileTest)
            dependsOn(appleTest)
        }

        val iosArm64Test by getting {
            dependsOn(iosTest)
        }

        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest)
        }

        val webTest by creating {
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
