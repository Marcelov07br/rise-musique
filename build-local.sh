#!/usr/bin/env bash
# Linux equivalent of build-local.ps1. Reads signing/credentials.json (never printed).
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/jdk}" ANDROID_HOME="${ANDROID_HOME:-$HOME/android}"
eval "$(python3 - <<'PY'
import json, shlex
c = json.load(open('signing/credentials.json'))
c['RISE_KEYSTORE'] = 'signing/rise-musique-gpt.jks'  # Windows path in the file; use the local copy
for k, v in c.items(): print(f"export {k}={shlex.quote(v)}")
PY
)"
export RISE_KEYSTORE="$PWD/$RISE_KEYSTORE"
./gradlew :app:assembleRelease :app:lintRelease :app:testDebugUnitTest --console=plain -q
python3 scripts/prepare-release.py
