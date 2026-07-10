#!/usr/bin/env sh
set -eu

APP_ID="com.example.caderneta"
APK="app/build/outputs/apk/debug/app-debug.apk"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Erro: comando ausente: $1" >&2
    exit 1
  fi
}

require_cmd git
require_cmd adb
require_cmd sha256sum

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
HEAD_SHA="$(git rev-parse HEAD)"
HEAD_SHORT="$(git rev-parse --short HEAD)"

echo "Branch: $BRANCH"
echo "Commit: $HEAD_SHA"

if [ "${ALLOW_DIRTY:-0}" != "1" ] && [ -n "$(git status --porcelain)" ]; then
  echo "Erro: a arvore de trabalho esta suja. Commit/stash antes de instalar." >&2
  echo "Use ALLOW_DIRTY=1 somente para diagnostico local." >&2
  git status --short >&2
  exit 1
fi

git fetch origin "$BRANCH" >/dev/null 2>&1 || true
if git rev-parse --verify "origin/$BRANCH" >/dev/null 2>&1; then
  ORIGIN_SHA="$(git rev-parse "origin/$BRANCH")"
  if [ "$HEAD_SHA" != "$ORIGIN_SHA" ]; then
    echo "Erro: HEAD difere de origin/$BRANCH." >&2
    echo "HEAD:   $HEAD_SHA" >&2
    echo "origin: $ORIGIN_SHA" >&2
    exit 1
  fi
fi

echo "Compilando commit $HEAD_SHORT"
JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jbr-17.0.14}" ./gradlew clean :app:assembleDebug

if [ ! -f "$APK" ]; then
  echo "Erro: APK nao encontrado em $APK" >&2
  exit 1
fi

APK_SIZE="$(wc -c < "$APK" | tr -d ' ')"
APK_SHA256="$(sha256sum "$APK" | awk '{print $1}')"
echo "APK: $APK"
echo "Tamanho: $APK_SIZE bytes"
echo "SHA-256: $APK_SHA256"

adb devices
adb uninstall "$APP_ID" >/dev/null 2>&1 || true
adb install "$APK"

PACKAGE_INFO="$(adb shell dumpsys package "$APP_ID")"
echo "$PACKAGE_INFO" | grep -E "versionCode|versionName|firstInstallTime|lastUpdateTime" || {
  echo "Erro: nao foi possivel confirmar versao instalada via dumpsys." >&2
  exit 1
}

EXPECTED_VERSION_CODE="2"
EXPECTED_VERSION_NAME="1.1.0-dev"

echo "$PACKAGE_INFO" | grep "versionCode=$EXPECTED_VERSION_CODE" >/dev/null || {
  echo "Erro: versionCode instalado nao bate com $EXPECTED_VERSION_CODE" >&2
  exit 1
}

echo "$PACKAGE_INFO" | grep "versionName=$EXPECTED_VERSION_NAME" >/dev/null || {
  echo "Erro: versionName instalado nao bate com $EXPECTED_VERSION_NAME" >&2
  exit 1
}

adb shell monkey -p "$APP_ID" 1 >/dev/null

echo "Instalado: $APP_ID $EXPECTED_VERSION_NAME ($EXPECTED_VERSION_CODE), commit $HEAD_SHORT"
