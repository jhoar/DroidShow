#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/preseed-gradle-wrapper.sh --zip <path-to-gradle-zip> [--gradle-user-home <dir>] [--distribution-url <url>]

Pre-seeds the Gradle wrapper cache so ./gradlew can run without downloading the
Gradle distribution from the network.

Defaults:
  --gradle-user-home: ${GRADLE_USER_HOME:-$HOME/.gradle}
  --distribution-url: value from gradle/wrapper/gradle-wrapper.properties
USAGE
}

ZIP_PATH=""
GRADLE_USER_HOME_PATH="${GRADLE_USER_HOME:-$HOME/.gradle}"
DISTRIBUTION_URL=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --zip)
      ZIP_PATH="$2"
      shift 2
      ;;
    --gradle-user-home)
      GRADLE_USER_HOME_PATH="$2"
      shift 2
      ;;
    --distribution-url)
      DISTRIBUTION_URL="$2"
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

if [[ -z "$ZIP_PATH" ]]; then
  echo "--zip is required" >&2
  usage >&2
  exit 1
fi

if [[ -z "$DISTRIBUTION_URL" ]]; then
  DISTRIBUTION_URL=$(awk -F= '/^distributionUrl=/{print $2}' gradle/wrapper/gradle-wrapper.properties | sed 's#\\:#:#g')
fi

if [[ -z "$DISTRIBUTION_URL" ]]; then
  echo "Unable to determine distributionUrl" >&2
  exit 1
fi

if [[ ! -f "$ZIP_PATH" ]]; then
  echo "Zip file not found: $ZIP_PATH" >&2
  exit 1
fi

zip_name=$(basename "$DISTRIBUTION_URL")
dist_name="${zip_name%.zip}"
url_hash=$(python - "$DISTRIBUTION_URL" <<'PY'
import hashlib, sys
url = sys.argv[1]
digest = hashlib.md5(url.encode('utf-8')).digest()
value = int.from_bytes(digest, 'big')
chars = '0123456789abcdefghijklmnopqrstuvwxyz'
out = ''
while value:
    value, rem = divmod(value, 36)
    out = chars[rem] + out
print(out or '0')
PY
)

dest_dir="$GRADLE_USER_HOME_PATH/wrapper/dists/$dist_name/$url_hash"
mkdir -p "$dest_dir"
cp "$ZIP_PATH" "$dest_dir/$zip_name"

cat <<INFO
Pre-seeded Gradle wrapper distribution:
  distributionUrl : $DISTRIBUTION_URL
  gradleUserHome  : $GRADLE_USER_HOME_PATH
  cachePath       : $dest_dir/$zip_name

Next step:
  GRADLE_USER_HOME="$GRADLE_USER_HOME_PATH" ./gradlew --version
INFO
