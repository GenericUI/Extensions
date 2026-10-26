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
    api(project(":extensions:media:common"))
    api(project(":extensions:media:systemCommon"))
    api(libs.vlcj)
    implementation(libs.kotlinx.coroutinesSwing)
    testImplementation(libs.kotlin.test)
}
