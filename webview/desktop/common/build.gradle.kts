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

    // Desktop Native targets — uses Chromium Blink engine + NJS (:common + :native)
    macosArm64()
    mingwX64()
    linuxX64()
    linuxArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":desktop:common"))
            api(project(":extensions:webview:common"))
            api(project(":extensions:webview:systemCommon"))
            api("com.nexustools.njs:common:1.0")
        }
        nativeMain.dependencies {
            api("com.nexustools.njs:native:1.0")
        }
        jvmMain.dependencies {
            api("com.nexustools.njs:jvm:1.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
