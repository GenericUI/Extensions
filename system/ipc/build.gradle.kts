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
        namespace = "com.nexustools.genericui.extensions.system.ipc"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTest {
            isIncludeAndroidResources = true
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    macosArm64()

    mingwX64()

    linuxX64()
    linuxArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":common"))
                api(libs.kotlinx.coroutines.core)
            }
        }

        val desktopMain by creating {
            dependsOn(commonMain)
        }

        val jvmMain by getting {
            dependsOn(desktopMain)
            dependencies {
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }

        val nativeDesktopMain by creating {
            dependsOn(desktopMain)
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

        val androidMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.kotlinx.coroutinesAndroid)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val desktopTest by creating {
            dependsOn(commonTest)
        }

        val jvmTest by getting {
            dependsOn(desktopTest)
            dependencies {
                implementation(libs.kotlin.testJunit)
            }
        }

        val nativeDesktopTest by creating {
            dependsOn(desktopTest)
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

        val androidHostTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation("org.robolectric:robolectric:4.16.1")
            }
        }
    }
}
