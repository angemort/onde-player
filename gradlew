#!/bin/sh
# Onde Player: AndroidIDE (local JDK 17) and portable CI launcher.
set -eu
APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cd "$APP_HOME"
if [ -x /home/test/.local/jdk-17/bin/java ]; then
  JAVA_HOME=/home/test/.local/jdk-17
  export JAVA_HOME
fi
if [ -n "${JAVA_HOME:-}" ]; then
  exec "$JAVA_HOME/bin/java" -cp "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
else
  exec java -cp "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
