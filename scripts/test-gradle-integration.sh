#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TASK="${1:-testDebugUnitTest}"
GRADLE_USER_HOME_PATH="${GRADLE_USER_HOME:-$HOME/.gradle}"

wrapper_jar_looks_valid() {
  [[ -f gradle/wrapper/gradle-wrapper.jar ]] || return 1
  jar tf gradle/wrapper/gradle-wrapper.jar 2>/dev/null | rg -q '^org/gradle/wrapper/GradleWrapperMain.class$'
}

read_distribution_url() {
  awk -F= '/^distributionUrl=/{print $2}' gradle/wrapper/gradle-wrapper.properties | sed 's#\\:#:#g'
}

wrapper_zip_cache_path() {
  local distribution_url zip_name dist_name url_hash
  distribution_url="$(read_distribution_url)"
  if [[ -z "$distribution_url" ]]; then
    echo "Unable to determine distributionUrl from gradle/wrapper/gradle-wrapper.properties" >&2
    return 1
  fi

  zip_name="$(basename "$distribution_url")"
  dist_name="${zip_name%.zip}"
  url_hash="$(python - "$distribution_url" <<'PY'
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
)"

  printf '%s\n' "$GRADLE_USER_HOME_PATH/wrapper/dists/$dist_name/$url_hash/$zip_name"
}

if ! wrapper_jar_looks_valid; then
  echo "gradle-wrapper.jar is missing or does not contain GradleWrapperMain" >&2
  exit 1
fi

CACHE_ZIP="$(wrapper_zip_cache_path)"
if [[ ! -f "$CACHE_ZIP" ]]; then
  echo "Pre-seeded wrapper distribution not found: $CACHE_ZIP" >&2
  echo "Seed it first, e.g.: scripts/preseed-gradle-wrapper.sh --zip /path/to/gradle-8.7-bin.zip" >&2
  exit 1
fi

echo "Using pre-seeded wrapper distribution from: $CACHE_ZIP"
echo "Running: ./gradlew --offline --no-daemon ${TASK}"
./gradlew --offline --no-daemon "$TASK"
