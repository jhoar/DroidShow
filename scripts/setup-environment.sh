#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/setup-environment.sh [--gradle-version <version>] [--zip <path>] [--install-dir <dir>] [--bin-dir <dir>] [--gradle-user-home <dir>]

Installs a Gradle distribution, exposes a `gradle` executable on PATH, and pre-seeds
that same zip into the Gradle wrapper cache location used by `./gradlew`.

Options:
  --gradle-version   Gradle version to install (default: 8.7)
  --zip              Use a local Gradle distribution zip instead of downloading
  --install-dir      Install root (default: ${HOME}/.local/gradle)
  --bin-dir          Bin dir for symlink (default: ${HOME}/.local/bin)
  --gradle-user-home Wrapper cache root (default: ${GRADLE_USER_HOME:-$HOME/.gradle})
  -h, --help         Show help

Examples:
  scripts/setup-environment.sh --zip /opt/cache/gradle-8.7-bin.zip
  scripts/setup-environment.sh --gradle-version 8.7
USAGE
}

GRADLE_VERSION="8.7"
ZIP_PATH=""
INSTALL_DIR="${HOME}/.local/gradle"
BIN_DIR="${HOME}/.local/bin"
GRADLE_USER_HOME_PATH="${GRADLE_USER_HOME:-$HOME/.gradle}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --gradle-version)
      GRADLE_VERSION="$2"
      shift 2
      ;;
    --zip)
      ZIP_PATH="$2"
      shift 2
      ;;
    --install-dir)
      INSTALL_DIR="$2"
      shift 2
      ;;
    --bin-dir)
      BIN_DIR="$2"
      shift 2
      ;;
    --gradle-user-home)
      GRADLE_USER_HOME_PATH="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

DIST_NAME="gradle-${GRADLE_VERSION}"
DIST_DIR="$INSTALL_DIR/$DIST_NAME"
GRADLE_BIN="$DIST_DIR/bin/gradle"
ZIP_NAME="${DIST_NAME}-bin.zip"

mkdir -p "$INSTALL_DIR" "$BIN_DIR" "$GRADLE_USER_HOME_PATH"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

if [[ -n "$ZIP_PATH" ]]; then
  [[ -f "$ZIP_PATH" ]] || { echo "Zip file not found: $ZIP_PATH" >&2; exit 1; }
  SOURCE_ZIP="$ZIP_PATH"
else
  SOURCE_ZIP="$TMP_DIR/$ZIP_NAME"
  URL="https://services.gradle.org/distributions/$ZIP_NAME"
  echo "Downloading $URL"
  curl -fsSL "$URL" -o "$SOURCE_ZIP"
fi

if [[ -x "$GRADLE_BIN" ]]; then
  echo "Gradle ${GRADLE_VERSION} already installed at $DIST_DIR"
else
  echo "Installing Gradle ${GRADLE_VERSION} into $INSTALL_DIR"
  unzip -q "$SOURCE_ZIP" -d "$INSTALL_DIR"
fi

ln -sf "$GRADLE_BIN" "$BIN_DIR/gradle"

echo "Gradle executable linked at: $BIN_DIR/gradle"
echo "Add to PATH if needed: export PATH=\"$BIN_DIR:\$PATH\""

scripts/preseed-gradle-wrapper.sh --zip "$SOURCE_ZIP" --gradle-user-home "$GRADLE_USER_HOME_PATH"

"$BIN_DIR/gradle" --version
