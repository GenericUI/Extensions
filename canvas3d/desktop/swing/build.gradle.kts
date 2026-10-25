import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    api(project(":desktop:swing"))
    api(project(":extensions:canvas3d:common"))
    api(project(":extensions:canvas3d:sharedSystem"))
    implementation(libs.kotlinx.coroutinesSwing)
    api(project(":extensions:gpu:desktop:swing"))
    testImplementation(libs.kotlin.test)
}
