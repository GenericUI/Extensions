plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    macosArm64()
    macosX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":desktop:macos"))
            api(project(":extensions:canvas3d:desktop:shared"))
            api(project(":extensions:canvas2d:desktop:macos"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}