#!/bin/sh
# TV emulator only; the phone uses its own browser. Preserves app data between runs.
set -eu
BBTV_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$BBTV_ROOT"
BBTV_ENV="$BBTV_ROOT/scripts/android-env.sh"
BBTV_TV_PORT=${BBTV_TV_PORT:-5554}
BBTV_TV_SERIAL="emulator-$BBTV_TV_PORT"
mkdir -p .tools
command -v python3 >/dev/null 2>&1 || { echo "Ten skrypt wymaga Python 3." >&2; exit 1; }
BBTV_RELAY=${BBTV_RELAY:-prod}
BBTV_RELAY=${BBTV_RELAY%/}
# The emulator reaches this Mac through 10.0.2.2; a phone needs its LAN address.
BBTV_SITE=$(python3 - "$BBTV_RELAY" "${BBTV_SITE:-}" <<'PY'
import ipaddress, re, socket, subprocess, sys
from urllib.parse import urlsplit

def origin(value):
    if not re.fullmatch(r"https?://(?:[a-zA-Z0-9.-]+|\[[0-9a-fA-F:]+\])(?::[0-9]{1,5})?/?", value):
        sys.exit("BBTV_RELAY i BBTV_SITE muszą być adresami HTTP/HTTPS bez ścieżki, hasła i parametrów.")
    url = urlsplit(value)
    if url.port is not None and not 1 <= url.port <= 65535:
        sys.exit("Port musi być w zakresie 1–65535.")
    return value.rstrip("/")

def lan_ip():
    for interface in ("en0", "en1"):
        try:
            address = subprocess.check_output(["/usr/sbin/ipconfig", "getifaddr", interface], stderr=subprocess.DEVNULL, text=True).strip()
            if ipaddress.ip_address(address).is_private and not ipaddress.ip_address(address).is_loopback:
                return address
        except (OSError, ValueError, subprocess.CalledProcessError):
            pass
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            return sock.getsockname()[0]
    except OSError:
        sys.exit("Nie wykryto adresu LAN. Ustaw BBTV_SITE=http://ADRES_KOMPUTERA:8799.")

relay = origin("https://bbtv.juraswis.workers.dev" if sys.argv[1] == "prod" else sys.argv[1])
site = sys.argv[2]
url = urlsplit(relay)
if not site and url.hostname in ("10.0.2.2", "localhost", "127.0.0.1", "::1"):
    site = f"{url.scheme}://{lan_ip()}" + (f":{url.port}" if url.port else "")
print(origin(site or relay))
PY
)

start_device() {
    BBTV_AVD=$1
    BBTV_PORT=$2
    BBTV_MEMORY=$3
    BBTV_LOG=$4
    BBTV_CORES=$5
    if "$BBTV_ENV" adb -s "emulator-$BBTV_PORT" get-state >/dev/null 2>&1; then
        BBTV_RUNNING=$("$BBTV_ENV" adb -s "emulator-$BBTV_PORT" emu avd name | head -n 1 | tr -d '\r')
        if [ "$BBTV_RUNNING" != "$BBTV_AVD" ]; then
            echo "Port $BBTV_PORT zajmuje $BBTV_RUNNING. Ustaw BBTV_TV_PORT na wolny parzysty port." >&2
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
    subprocess.Popen([helper, "emulator", "-avd", avd, "-port", port, "-memory", memory, "-cores", cores, "-gpu", "swiftshader_indirect", "-no-snapshot", "-no-boot-anim", "-no-metrics"], stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
PY
}

wait_for_boot() {
    BBTV_SERIAL=$1
    BBTV_ATTEMPT=0
    while [ "$("$BBTV_ENV" adb -s "$BBTV_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        BBTV_ATTEMPT=$((BBTV_ATTEMPT + 1))
        if [ "$BBTV_ATTEMPT" -gt 150 ]; then
            echo "Emulator $BBTV_SERIAL nie wystartował. Sprawdź .tools/demo-tv.log." >&2
            exit 1
        fi
        sleep 2
    done
    "$BBTV_ENV" adb -s "$BBTV_SERIAL" shell input keyevent 82
}

"$BBTV_ENV" ./gradlew :tv:assembleDebug
start_device BB_TV_API30 "$BBTV_TV_PORT" 2048 .tools/demo-tv.log 4
echo "Czekam na start Androida dla TV…"
wait_for_boot "$BBTV_TV_SERIAL"
"$BBTV_ENV" adb -s "$BBTV_TV_SERIAL" install -r tv/build/outputs/apk/debug/tv-debug.apk
"$BBTV_ENV" adb -s "$BBTV_TV_SERIAL" shell am start --activity-single-top -n pl.bbtv.tv/.TvActivity --es relay "$BBTV_RELAY" --es relay_site "$BBTV_SITE"
"$BBTV_ENV" adb -s "$BBTV_TV_SERIAL" forward tcp:18765 tcp:8765
echo ""
echo "Gotowe: TV jest otwarty."
echo "Pilot: $BBTV_SITE → zeskanuj QR albo wpisz kod z ekranu TV."
