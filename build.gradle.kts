import org.gradle.api.tasks.testing.Test

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    }
}

plugins {
    base
}

tasks.register<Test>("test") {
    description = "Compatibility root test task for --tests filtering in CI/container."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    testClassesDirs = files()
    classpath = files()
    filter {
        isFailOnNoMatchingTests = false
    }
}
