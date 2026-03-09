# Pre-seeding Gradle wrapper cache for offline/proxied builds

This repository includes the Gradle wrapper scripts (`./gradlew`, `gradlew.bat`), and by default it downloads:

> Note: `gradle/wrapper/gradle-wrapper.jar` is intentionally not committed in this repository. Add it manually before invoking `./gradlew` in environments that require the wrapper bootstrap JAR.

- `https://services.gradle.org/distributions/gradle-8.7-bin.zip`

In restricted environments (for example, Codex containers with blocked outbound access),
pre-seed the wrapper cache before running `./gradlew`.

## 1) Obtain the Gradle distribution zip

Download `gradle-8.7-bin.zip` in an environment with network access and make it available
inside your build image/container.

## 2) Seed the wrapper cache

From the repository root:

```bash
scripts/preseed-gradle-wrapper.sh --zip /path/to/gradle-8.7-bin.zip
```

This places the zip under `${GRADLE_USER_HOME:-~/.gradle}/wrapper/dists/...` in the exact
location the wrapper resolves for the configured `distributionUrl`.

## 3) Run wrapper tasks

```bash
./gradlew --version
./gradlew test
```

If your runtime uses a non-default cache directory, pass it when seeding and when running:

```bash
scripts/preseed-gradle-wrapper.sh \
  --zip /opt/cache/gradle-8.7-bin.zip \
  --gradle-user-home /opt/gradle-home

GRADLE_USER_HOME=/opt/gradle-home ./gradlew test
```
