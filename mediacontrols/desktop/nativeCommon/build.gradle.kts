plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate()

    mingwX64()
    linuxX64()
    linuxArm64()
    macosArm64()
    macosX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(project(":extensions:mediacontrols:common"))
            api(project(":extensions:mediacontrols:systemCommon"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
