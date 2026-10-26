plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":mobile:ios"))
            api(project(":extensions:canvas3d:mobile:common"))
            api(project(":extensions:gpu:mobile:ios"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}