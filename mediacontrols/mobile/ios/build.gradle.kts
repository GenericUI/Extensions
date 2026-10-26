plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(project(":extensions:mediacontrols:mobile:common"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
