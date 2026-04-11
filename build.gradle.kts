import org.gradle.api.tasks.testing.Test

plugins {
    base
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
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
