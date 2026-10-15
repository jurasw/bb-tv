#!/bin/sh
set -eu
BBTV_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$BBTV_ROOT"
./scripts/android-env.sh ./gradlew :tv:assembleDebug
mkdir -p web/public/apk
rm -f web/public/apk/bbtv-phone.apk
cp tv/build/outputs/apk/debug/tv-debug.apk web/public/apk/bbtv-tv.apk
cd web
npx -y wrangler@4 deploy
