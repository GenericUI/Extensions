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
            api(project(":extensions:gpu:common"))
            api(project(":extensions:gpu:systemCommon"))
            implementation(libs.kotlinx.coroutines.core)

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
