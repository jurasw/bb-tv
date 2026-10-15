#!/bin/sh
BBTV_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "$BBTV_ROOT/scripts/android-env.sh" java -Dorg.gradle.appname=gradlew -classpath "$BBTV_ROOT/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
