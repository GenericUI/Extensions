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
            api(project(":desktop:shared"))
            api(project(":extensions:canvas3d:common"))
            api(project(":extensions:canvas3d:sharedSystem"))
            implementation(libs.kotlinx.coroutines.core)
            api(project(":extensions:gpu:desktop:shared"))
            api(project(":extensions:canvas2d:desktop:shared"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
