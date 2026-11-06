plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate()

    mingwX64()
    linuxX64()
    linuxArm64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":desktop:nativeCommon"))
            api(project(":extensions:gpu:common"))
            api(project(":extensions:gpu:systemCommon"))
        }
    }
}
