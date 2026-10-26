import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    // Desktop Native targets — uses Chromium Blink engine + NJS (:common)
    macosArm64()
    macosX64()
    mingwX64()
    linuxX64()
    linuxArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":desktop:common"))
            api(project(":extensions:webview:common"))
            api(project(":extensions:webview:systemCommon"))
            api("com.nexustools.njs:common:0.1.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
