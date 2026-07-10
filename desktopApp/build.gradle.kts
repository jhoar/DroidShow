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
    implementation("net.java.dev.jna:jna:5.19.1")
    testImplementation("junit:junit:4.13.2")
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

            macOS {
                bundleID = "io.github.jhoar.showlio.desktop"
            }

            fileAssociation("application/zip", "zip", "ZIP Archive")
            fileAssociation("application/vnd.comicbook+zip", "cbz", "Comic Book Archive (ZIP)")
            fileAssociation("application/x-rar-compressed", "rar", "RAR Archive")
            fileAssociation("application/vnd.comicbook-rar", "cbr", "Comic Book Archive (RAR)")
            fileAssociation("application/x-7z-compressed", "7z", "7-Zip Archive")
            fileAssociation("application/x-cb7", "cb7", "Comic Book Archive (7z)")
        }
    }
}
