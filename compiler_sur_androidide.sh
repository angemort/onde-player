#!/bin/sh
# A lancer depuis le dossier du projet: sh compiler_sur_androidide.sh
set -eu
cd "$(dirname "$0")"
JDK="/home/test/.local/jdk-17"
if [ ! -x "$JDK/bin/java" ]; then
  echo "Java 17 introuvable: $JDK"
  exit 1
fi
export JAVA_HOME="$JDK"
export PATH="$JAVA_HOME/bin:$PATH"
echo "===== JAVA UTILISE POUR COMPILER ====="
"$JAVA_HOME/bin/java" -version
echo "===== GRADLE ====="
sh ./gradlew --version
echo "===== COMPILATION APK DEBUG ====="
sh ./gradlew --no-daemon --stacktrace assembleDebug
echo "===== TERMINE ====="
echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
