plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":desktopApp-policy"))
    implementation(project(":desktopApp-archive"))
}
