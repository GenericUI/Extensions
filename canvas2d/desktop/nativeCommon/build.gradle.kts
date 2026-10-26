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
            api(project(":desktop:nativeCommon"))
            api(project(":extensions:gpu:desktop:nativeCommon"))
            api(project(":extensions:imageprocessing:systemCommon"))
            api(project(":extensions:canvas2d:common"))
            api(project(":extensions:canvas2d:systemCommon"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
