plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":desktopApp-ui"))
}

val disableDesktopProguard =
    (findProperty("desktop.disableProguard") as String?)?.toBoolean() == true

compose.desktop {
    application {
        mainClass = "desktopApp.DesktopMainKt"

        buildTypes.release.proguard {
            isEnabled.set(!disableDesktopProguard)
            if (!disableDesktopProguard) {
                configurationFiles.from(project.file("proguard-rules.pro"))
            }
        }

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Pkg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "showlio"
            packageVersion = "1.0.0"
            description = "Desktop archive viewer for Showlio"
            vendor = "Showlio"
            iconFile.set(project.file("src/main/resources/showlio-icon.svg"))

            macOS {
                bundleID = "io.github.jhoar.showlio.desktop"
            }
        }
    }
}
