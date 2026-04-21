plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":desktopApp-viewer"))
    implementation(project(":desktopApp-policy"))
    implementation(project(":desktopApp-archive"))
}
