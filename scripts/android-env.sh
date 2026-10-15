#!/bin/sh
# Optional, local CLI toolchain; otherwise use your existing JAVA_HOME / ANDROID_HOME.
BBTV_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -d "$BBTV_ROOT/.tools/android-sdk" ]; then export ANDROID_HOME="$BBTV_ROOT/.tools/android-sdk"; fi
for BBTV_JDK in "$BBTV_ROOT"/.tools/*/Contents/Home "$BBTV_ROOT"/.tools/*/*.jdk/Contents/Home; do
    if [ -x "$BBTV_JDK/bin/java" ]; then export JAVA_HOME="$BBTV_JDK"; break; fi
done
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.net.preferIPv4Stack=true"
exec "$@"
