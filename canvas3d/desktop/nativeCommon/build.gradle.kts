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
            api(project(":extensions:canvas3d:common"))
            api(project(":extensions:canvas3d:systemCommon"))
            implementation(libs.kotlinx.coroutines.core)
            api(project(":extensions:gpu:desktop:nativeCommon"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
