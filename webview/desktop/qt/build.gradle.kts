plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    mingwX64()
    linuxX64()
    linuxArm64()
    macosArm64()
    macosX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":desktop:qt"))
            api(project(":extensions:webview:desktop:nativeCommon"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}