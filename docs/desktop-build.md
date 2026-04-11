# Desktop build & packaging

This project uses JetBrains Compose Desktop (`:desktopApp`) and Gradle 8.7.

## Prerequisites

- JDK 17 installed and available on `PATH`.
- Gradle 8.7 available (locally via `gradle`, or through GitHub Actions via `gradle/actions/setup-gradle`).
- Platform packaging toolchain:
  - **macOS (.pkg)**: Xcode Command Line Tools (`xcode-select --install`) and Apple packaging tools.
  - **Windows (.msi)**: WiX Toolset available on `PATH`.

## Run locally (desktop app)

From repository root:

```bash
gradle --no-daemon :desktopApp:run
```

## Build distributables locally

Build all configured formats for your current OS:

```bash
gradle --no-daemon :desktopApp:packageReleaseDistributionForCurrentOS
```

Build specific packages:

```bash
# macOS PKG
gradle --no-daemon :desktopApp:packageReleasePkg

# Windows MSI
gradle --no-daemon :desktopApp:packageReleaseMsi
```

## Artifact output locations

Generated packages are written under:

- `desktopApp/build/compose/binaries/main-release/pkg/` for macOS `.pkg`
- `desktopApp/build/compose/binaries/main-release/msi/` for Windows `.msi`

## Signing placeholders

Signing is intentionally left as placeholders for CI secret wiring.

- **macOS** (to be configured):
  - `APPLE_SIGNING_IDENTITY`
  - `APPLE_TEAM_ID`
  - `APPLE_NOTARY_APPLE_ID`
  - `APPLE_NOTARY_PASSWORD`
  - `APPLE_NOTARY_TEAM_ID`
- **Windows** (to be configured):
  - `WINDOWS_SIGNING_CERT_BASE64`
  - `WINDOWS_SIGNING_CERT_PASSWORD`
  - `WINDOWS_SIGNING_TIMESTAMP_URL`

When enabling signing, inject secrets in CI and add the corresponding signing flags/properties to packaging tasks.

## CI coverage

`.github/workflows/desktop-ci.yml` includes:

- `shared-tests`: runs `:desktopApp-policy:test` and `:desktopApp-archive:test`
- `package-desktop` matrix:
  - `macos-latest` builds and uploads `.pkg`
  - `windows-latest` builds and uploads `.msi`
