#!/usr/bin/env sh
set -eu

REAL_APP_ID="com.example.caderneta"
APP_ID="${AUDIT_APP_ID:-com.example.caderneta.audit}"
TEST_APP_ID="${APP_ID}.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
SEED_CLASS="com.example.caderneta.e2e.processdeath.ProcessDeathSeedE2ETest"
VERIFY_CLASS="com.example.caderneta.e2e.processdeath.ProcessDeathVerifyE2ETest"
APK="app/build/outputs/apk/audit/app-audit.apk"
TEST_APK="app/build/outputs/apk/androidTest/audit/app-audit-androidTest.apk"
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jbr-17.0.14}"
export JAVA_HOME

PHYSICAL_SERIAL=""
ALLOW_DIRTY="0"
SKIP_BUILD="0"
REPORT_DIR=""

usage() {
  cat <<USAGE
Usage: $0 --physical SERIAL [--allow-dirty] [--skip-build] [--report-dir DIR]

Prova morte de processo real: semeia dados via instrumentacao, mata o
processo do app audit com 'adb shell am force-stop', relanca a Activity
numa instrumentacao nova e verifica que os dados sobreviveram.

Requer dispositivo fisico (GMD fora de escopo: interceptar o ciclo de vida
gerenciado pelo Gradle no meio da instrumentacao seria mais arriscado e
nao e necessario aqui).

--skip-build reaproveita os APKs ja montados em app/build/outputs/apk.
--report-dir grava artefatos no diretorio informado em vez de criar um novo.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --physical)
      [ "$#" -ge 2 ] || { echo "Erro: --physical requer serial" >&2; exit 2; }
      PHYSICAL_SERIAL="$2"
      shift 2
      ;;
    --allow-dirty)
      ALLOW_DIRTY="1"
      shift
      ;;
    --skip-build)
      SKIP_BUILD="1"
      shift
      ;;
    --report-dir)
      [ "$#" -ge 2 ] || { echo "Erro: --report-dir requer diretorio" >&2; exit 2; }
      REPORT_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Erro: argumento desconhecido: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [ -z "$PHYSICAL_SERIAL" ]; then
  echo "Erro: --physical SERIAL e obrigatorio" >&2
  usage >&2
  exit 2
fi

case "$APP_ID" in
  "$REAL_APP_ID")
    echo "Erro: script recusou operar no pacote real $REAL_APP_ID" >&2
    exit 2
    ;;
  *.audit) ;;
  *)
    echo "Erro: pacote de auditoria deve terminar com .audit: $APP_ID" >&2
    exit 2
    ;;
esac

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Erro: comando ausente: $1" >&2
    exit 1
  fi
}

resolve_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi
  for candidate in \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
    "$HOME/Android/Sdk/platform-tools/adb" \
    "$HOME/.local/share/android-sdk/platform-tools/adb"; do
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return
    fi
  done
  echo "Erro: adb nao encontrado no PATH, ANDROID_HOME, ANDROID_SDK_ROOT ou ~/Android/Sdk" >&2
  exit 1
}

require_cmd git
cd "$ROOT_DIR"

GIT_STATUS_SHORT="$(git status --short)"
if [ -n "$GIT_STATUS_SHORT" ] && [ "$ALLOW_DIRTY" != "1" ]; then
  echo "Erro: a arvore de trabalho esta suja. Commit/stash antes, ou use --allow-dirty." >&2
  git status --short >&2
  exit 1
fi

ADB="$(resolve_adb)"

STATE="$($ADB -s "$PHYSICAL_SERIAL" get-state)"
if [ "$STATE" != "device" ]; then
  echo "Erro: device $PHYSICAL_SERIAL em estado '$STATE', esperado 'device'" >&2
  exit 1
fi

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
if [ -z "$REPORT_DIR" ]; then
  REPORT_DIR="build/reports/process-death/$TIMESTAMP"
fi
mkdir -p "$REPORT_DIR"

echo "Report dir: $REPORT_DIR"
echo "Device: $PHYSICAL_SERIAL"
echo "App ID: $APP_ID"

if [ "$SKIP_BUILD" = "0" ]; then
  ./gradlew :app:assembleAudit :app:assembleAuditAndroidTest --stacktrace --no-daemon
fi

if [ ! -f "$APK" ]; then
  echo "Erro: APK audit nao encontrado em $APK" >&2
  exit 1
fi
if [ ! -f "$TEST_APK" ]; then
  echo "Erro: APK de teste nao encontrado em $TEST_APK" >&2
  exit 1
fi

"$ADB" -s "$PHYSICAL_SERIAL" install -r "$APK"
"$ADB" -s "$PHYSICAL_SERIAL" install -r "$TEST_APK"

run_instrument() {
  class_name="$1"
  output_file="$2"
  set +e
  "$ADB" -s "$PHYSICAL_SERIAL" shell am instrument -w -e class "$class_name" \
    "$TEST_APP_ID/$RUNNER" > "$output_file" 2>&1
  set -e
  cat "$output_file"
}

SEED_OUTPUT="$REPORT_DIR/seed-instrument.txt"
echo "--- Fase seed: $SEED_CLASS ---"
run_instrument "$SEED_CLASS" "$SEED_OUTPUT"
if ! grep -q "OK (1 test)" "$SEED_OUTPUT"; then
  echo "Erro: fase seed nao reportou sucesso. Veja $SEED_OUTPUT" >&2
  exit 1
fi

echo "--- am force-stop $APP_ID ---"
"$ADB" -s "$PHYSICAL_SERIAL" shell am force-stop "$APP_ID"

FORCE_STOP_CONFIRMED="false"
attempt=0
while [ "$attempt" -lt 5 ]; do
  PID_OUTPUT="$($ADB -s "$PHYSICAL_SERIAL" shell pidof "$APP_ID" || true)"
  if [ -z "$(printf '%s' "$PID_OUTPUT" | tr -d '[:space:]')" ]; then
    FORCE_STOP_CONFIRMED="true"
    break
  fi
  attempt=$((attempt + 1))
  sleep 1
done
echo "forceStopConfirmed=$FORCE_STOP_CONFIRMED" | tee "$REPORT_DIR/force-stop-confirmation.txt"
if [ "$FORCE_STOP_CONFIRMED" != "true" ]; then
  echo "Erro: processo $APP_ID continua vivo apos force-stop" >&2
  exit 1
fi

VERIFY_OUTPUT="$REPORT_DIR/verify-instrument.txt"
echo "--- Fase verify: $VERIFY_CLASS ---"
run_instrument "$VERIFY_CLASS" "$VERIFY_OUTPUT"
if ! grep -q "OK (1 test)" "$VERIFY_OUTPUT"; then
  echo "Erro: fase verify nao reportou sucesso. Veja $VERIFY_OUTPUT" >&2
  exit 1
fi

cat > "$REPORT_DIR/summary.json" <<JSON
{
  "status": "passed",
  "scenario": "process_death_real",
  "device": "$PHYSICAL_SERIAL",
  "appId": "$APP_ID",
  "timestamp": "$TIMESTAMP",
  "seedResult": "OK",
  "forceStopConfirmed": $FORCE_STOP_CONFIRMED,
  "verifyResult": "OK"
}
JSON

echo "Processo morto e recriado com sucesso; dados sobreviveram."
echo "Summary: $REPORT_DIR/summary.json"
