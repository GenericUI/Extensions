plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    linuxX64()
    linuxArm64()
    mingwX64()
    macosArm64()
    macosX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":desktop:gtk"))
            api(project(":extensions:media:desktop:nativeCommon"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}