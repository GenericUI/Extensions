plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":mobile:ios"))
            api(project(":extensions:gpu:mobile:common"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}