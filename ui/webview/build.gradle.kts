import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration

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
        namespace = "com.nexustools.genericui.extensions.ui.webview"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTest {}

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()

    val desktopNativeTargets = listOf(
        mingwX64(),
        linuxX64(),
        linuxArm64(),
        macosArm64()
    )

    desktopNativeTargets.forEach { target ->
        target.binaries {
            if (target.name.startsWith("mingw")) {
                all {
                    linkerOpts(
                        "-lole32",
                        "-lcomdlg32",
                        "-lgdi32",
                        "-luser32",
                        "-lws2_32",
                        "-lsecur32",
                        "-lcrypt32",
                        "-lwinhttp",
                        "-lshell32",
                        "-luuid",
                        "-lwindowscodecs",
                        "-liphlpapi",
                        "-lcomctl32",
                        "-luxtheme",
                        "-ldwmapi",
                        "-lmsimg32"
                    )
                }
            }
            executable("webviewProcess") {
                baseName = "genericui-webview-process"
                entryPoint = "com.nexustools.genericui.ext.webview.desktop.process.main"
            }
        }
        val capName = target.name.replaceFirstChar { it.uppercaseChar() }
        tasks.matching { it.name == "${target.name}Test" }.configureEach {
            dependsOn("linkWebviewProcessDebugExecutable$capName")
        }
    }

    js {
        browser()
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":common"))
                api(libs.kotlinx.coroutines.core)
            }
        }

        val systemMain by creating {
            dependsOn(commonMain)
            dependencies {
                api(project(":system"))
            }
        }

        // Desktop-ONLY source sets: Alien + Companion Extensions + IPC + NJS (:common, :jvm, :native)
        val desktopCommonMain by creating {
            dependsOn(systemMain)
            dependencies {
                api(project(":desktop"))
                api(project(":extensions:ui:alien"))
                api(project(":extensions:system:ipc"))
                api(project(":extensions:graphics:svg"))
                api(project(":extensions:graphics:svg:alien"))
                api(project(":extensions:graphics:canvas2d"))
                api(project(":extensions:graphics:canvas2d:alien"))
                api(project(":extensions:graphics:canvas3d"))
                api(project(":extensions:graphics:canvas3d:alien"))
                api(project(":extensions:media:media"))
                api(project(":extensions:media:media:alien"))
                api("com.nexustools.njs:common:1.0")
            }
        }

        val jvmMain by getting {
            dependsOn(desktopCommonMain)
            dependencies {
                api(project(":desktop"))
                api(project(":jvm"))
                api("com.nexustools.njs:common:1.0")
                api("com.nexustools.njs:jvm:1.0")
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }

        val nativeDesktopMain by creating {
            dependsOn(desktopCommonMain)
            dependencies {
                api(project(":desktop"))
                api("com.nexustools.njs:common:1.0")
                api("com.nexustools.njs:native:1.0")
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

        // Mobile & Web source sets: NEVER depend on com.nexustools.njs:*
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

        // Test source sets
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val systemTest by creating {
            dependsOn(commonTest)
        }

        val desktopCommonTest by creating {
            dependsOn(systemTest)
        }

        val jvmTest by getting {
            dependsOn(desktopCommonTest)
            dependencies {
                implementation(libs.kotlin.testJunit)
            }
        }

        val nativeDesktopTest by creating {
            dependsOn(desktopCommonTest)
        }

        val mingwTest = maybeCreate("mingwTest").apply {
            dependsOn(nativeDesktopTest)
        }

        val mingwX64Test by getting {
            dependsOn(mingwTest)
        }

        val macosTest = maybeCreate("macosTest").apply {
            dependsOn(nativeDesktopTest)
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
            }
        }

        val iosTest = maybeCreate("iosTest").apply {
            dependsOn(mobileTest)
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

tasks.withType<org.gradle.api.tasks.testing.AbstractTestTask>().configureEach {
    timeout.set(Duration.ofMinutes(5))
}
