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

                // fileAssociation() isn't available on the pinned compose plugin version
                // (added in 1.7.0), so register CFBundleDocumentTypes via a raw Info.plist
                // key instead, backed by a custom UTI covering the supported archive extensions.
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleDocumentTypes</key>
                        <array>
                            <dict>
                                <key>CFBundleTypeName</key>
                                <string>Comic Book Archive</string>
                                <key>CFBundleTypeRole</key>
                                <string>Viewer</string>
                                <key>LSHandlerRank</key>
                                <string>Alternate</string>
                                <key>LSItemContentTypes</key>
                                <array>
                                    <string>io.github.jhoar.showlio.archive</string>
                                </array>
                            </dict>
                        </array>
                        <key>UTExportedTypeDeclarations</key>
                        <array>
                            <dict>
                                <key>UTTypeIdentifier</key>
                                <string>io.github.jhoar.showlio.archive</string>
                                <key>UTTypeDescription</key>
                                <string>Comic Book Archive</string>
                                <key>UTTypeConformsTo</key>
                                <array>
                                    <string>public.data</string>
                                    <string>public.archive</string>
                                </array>
                                <key>UTTypeTagSpecification</key>
                                <dict>
                                    <key>public.filename-extension</key>
                                    <array>
                                        <string>zip</string>
                                        <string>cbz</string>
                                        <string>rar</string>
                                        <string>cbr</string>
                                        <string>7z</string>
                                        <string>cb7</string>
                                    </array>
                                </dict>
                            </dict>
                        </array>
                    """.trimIndent()
                }
            }
        }
    }
}
