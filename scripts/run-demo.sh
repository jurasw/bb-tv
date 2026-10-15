#!/bin/sh
# Two visible, separate Android devices. Preserves their app data between runs.
set -eu
BBTV_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$BBTV_ROOT"
BBTV_ENV="$BBTV_ROOT/scripts/android-env.sh"
BBTV_TV_PORT=${BBTV_TV_PORT:-5554}
BBTV_PHONE_PORT=${BBTV_PHONE_PORT:-5556}
BBTV_TV_SERIAL="emulator-$BBTV_TV_PORT"
BBTV_PHONE_SERIAL="emulator-$BBTV_PHONE_PORT"
mkdir -p .tools
command -v python3 >/dev/null 2>&1 || { echo "Ten skrypt wymaga Python 3." >&2; exit 1; }

start_device() {
    BBTV_AVD=$1
    BBTV_PORT=$2
    BBTV_MEMORY=$3
    BBTV_LOG=$4
    BBTV_CORES=$5
    if "$BBTV_ENV" adb -s "emulator-$BBTV_PORT" get-state >/dev/null 2>&1; then
        BBTV_RUNNING=$("$BBTV_ENV" adb -s "emulator-$BBTV_PORT" emu avd name | head -n 1 | tr -d '\r')
        if [ "$BBTV_RUNNING" != "$BBTV_AVD" ]; then
            echo "Port $BBTV_PORT zajmuje $BBTV_RUNNING. Ustaw BBTV_TV_PORT / BBTV_PHONE_PORT na wolne parzyste porty." >&2
            exit 1
        fi
        return
    fi
    if ! "$BBTV_ENV" emulator -list-avds | tr -d '\r' | grep -Fxq "$BBTV_AVD"; then
        echo "Brak AVD $BBTV_AVD. Instrukcja utworzenia jest w README (Test w Android Studio)." >&2
        exit 1
    fi
    echo "Uruchamiam ${BBTV_AVD}…"
    python3 - "$BBTV_ENV" "$BBTV_AVD" "$BBTV_PORT" "$BBTV_MEMORY" "$BBTV_LOG" "$BBTV_CORES" <<'PY'
import subprocess, sys
helper, avd, port, memory, path, cores = sys.argv[1:]
with open(path, "w") as log:
    subprocess.Popen([helper, "emulator", "-avd", avd, "-port", port, "-memory", memory, "-cores", cores, "-gpu", "host", "-feature", "-Vulkan", "-no-snapshot", "-no-boot-anim"], stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
PY
}

wait_for_boot() {
    BBTV_SERIAL=$1
    BBTV_ATTEMPT=0
    while [ "$("$BBTV_ENV" adb -s "$BBTV_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        BBTV_ATTEMPT=$((BBTV_ATTEMPT + 1))
        if [ "$BBTV_ATTEMPT" -gt 150 ]; then
            echo "Emulator $BBTV_SERIAL nie wystartował. Sprawdź .tools/demo-tv.log / demo-phone.log." >&2
            exit 1
        fi
        sleep 2
    done
    "$BBTV_ENV" adb -s "$BBTV_SERIAL" shell input keyevent 82
}

./gradlew assembleDebug
start_device BB_TV_API30 "$BBTV_TV_PORT" 2048 .tools/demo-tv.log 4
echo "Czekam na start Androida dla TV…"
wait_for_boot "$BBTV_TV_SERIAL"
start_device BB_Phone_API30 "$BBTV_PHONE_PORT" 1536 .tools/demo-phone.log 2
echo "Czekam na start Androida dla telefonu…"
wait_for_boot "$BBTV_PHONE_SERIAL"
"$BBTV_ENV" adb -s "$BBTV_TV_SERIAL" install -r tv/build/outputs/apk/debug/tv-debug.apk
"$BBTV_ENV" adb -s "$BBTV_PHONE_SERIAL" install -r phone/build/outputs/apk/debug/phone-debug.apk
"$BBTV_ENV" adb -s "$BBTV_TV_SERIAL" shell am start -n pl.bbtv.tv/.TvActivity
"$BBTV_ENV" adb -s "$BBTV_TV_SERIAL" forward tcp:18765 tcp:8765
"$BBTV_ENV" adb -s "$BBTV_PHONE_SERIAL" shell am start -n pl.bbtv.phone/.PhoneActivity
echo ""
echo "Gotowe: TV i telefon są otwarte."
echo "Telefon → IP: 10.0.2.2:18765 → PIN z TV → Sparuj."
echo "Następnie używaj touchpada, przycisków i pola adresu."
