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
        namespace = "com.nexustools.genericui.ext.alien.system"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

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
            api(project(":extensions:imageprocessing:common"))
            api(project(":extensions:imageprocessing:sharedSystem"))
            api(project(":extensions:canvas2d:common"))
            api(project(":extensions:canvas2d:sharedSystem"))
            api(project(":extensions:alien:common"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
