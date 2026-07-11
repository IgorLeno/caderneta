#!/usr/bin/env sh
set -eu

REAL_APP_ID="com.example.caderneta"
APP_ID="${AUDIT_APP_ID:-com.example.caderneta.audit}"
APK="app/build/outputs/apk/audit/app-audit.apk"
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jbr-17.0.14}"
export JAVA_HOME

SUITE="full"
KEEP_DEVICE="0"
PHYSICAL_SERIAL=""
WIPE="0"
MODE="gmd"
ALLOW_DIRTY="0"
DRY_RUN="0"

usage() {
  cat <<USAGE
Usage: $0 [--suite smoke|full] [--keep-device] [--physical SERIAL] [--wipe] [--allow-dirty] [--dry-run]

Default mode runs Gradle Managed Device pixelApi35 on the audit buildType.
Physical mode requires --physical SERIAL and only operates on com.example.caderneta.audit.
The script refuses a dirty worktree unless --allow-dirty is present.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --suite)
      [ "$#" -ge 2 ] || { echo "Erro: --suite requer valor" >&2; exit 2; }
      SUITE="$2"
      shift 2
      ;;
    --keep-device)
      KEEP_DEVICE="1"
      shift
      ;;
    --physical)
      [ "$#" -ge 2 ] || { echo "Erro: --physical requer serial" >&2; exit 2; }
      PHYSICAL_SERIAL="$2"
      MODE="physical"
      shift 2
      ;;
    --wipe)
      WIPE="1"
      shift
      ;;
    --allow-dirty)
      ALLOW_DIRTY="1"
      shift
      ;;
    --dry-run)
      DRY_RUN="1"
      shift
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

case "$SUITE" in
  smoke|full) ;;
  *) echo "Erro: suite invalida: $SUITE" >&2; exit 2 ;;
esac

case "$APP_ID" in
  "$REAL_APP_ID")
    echo "Erro: auditoria recusou operar no pacote real $REAL_APP_ID" >&2
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

copy_if_exists() {
  src="$1"
  dest="$2"
  if [ -e "$src" ]; then
    mkdir -p "$dest"
    cp -R "$src"/. "$dest"/
  fi
}

copy_matching_dirs() {
  pattern="$1"
  dest="$2"
  mkdir -p "$dest"
  find app/build/outputs app/build/reports -type d -name "$pattern" -print | while IFS= read -r dir; do
    cp -R "$dir"/. "$dest"/
  done
}

flatten_test_artifacts() {
  for name in database-summary screenshots ui-hierarchy failures metadata; do
    mkdir -p "$REPORT_DIR/$name"
    find "$REPORT_DIR" -mindepth 2 -type d -name "$name" -print | while IFS= read -r dir; do
      cp -R "$dir"/. "$REPORT_DIR/$name"/
    done
  done
}

require_cmd git

cd "$ROOT_DIR"

GIT_STATUS_SHORT="$(git status --short)"
if [ -n "$GIT_STATUS_SHORT" ] && [ "$ALLOW_DIRTY" != "1" ]; then
  echo "Erro: a arvore de trabalho esta suja. Commit/stash antes da auditoria." >&2
  echo "Use --allow-dirty somente para diagnostico nao-reprodutivel." >&2
  git status --short >&2
  exit 1
fi

if [ "$DRY_RUN" = "0" ]; then
  require_cmd sha256sum
  require_cmd wc
  require_cmd python3
  require_cmd zip
  ADB="$(resolve_adb)"
else
  ADB=""
fi

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
REPORT_DIR="build/reports/app-audit/$TIMESTAMP"
mkdir -p \
  "$REPORT_DIR/database-summary" \
  "$REPORT_DIR/screenshots" \
  "$REPORT_DIR/ui-hierarchy" \
  "$REPORT_DIR/logcat" \
  "$REPORT_DIR/test-results" \
  "$REPORT_DIR/failures" \
  "$REPORT_DIR/metadata"

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
HEAD_SHA="$(git rev-parse HEAD)"
HEAD_SHORT="$(git rev-parse --short HEAD)"
REPRODUCIBLE="true"
if [ -n "$GIT_STATUS_SHORT" ]; then
  REPRODUCIBLE="false"
fi
ALLOW_DIRTY_JSON="false"
if [ "$ALLOW_DIRTY" = "1" ]; then
  ALLOW_DIRTY_JSON="true"
fi
DRY_RUN_JSON="false"
if [ "$DRY_RUN" = "1" ]; then
  DRY_RUN_JSON="true"
fi

GIT_STATUS_FILE="$REPORT_DIR/git-status.txt"
git status --short --branch > "$GIT_STATUS_FILE"
if [ "$REPRODUCIBLE" = "false" ]; then
  git diff --binary > "$REPORT_DIR/git-diff.patch"
fi

cat > "$REPORT_DIR/run-context.json" <<JSON
{
  "branch": "$BRANCH",
  "head": "$HEAD_SHA",
  "headShort": "$HEAD_SHORT",
  "mode": "$MODE",
  "suite": "$SUITE",
  "timestamp": "$TIMESTAMP",
  "appId": "$APP_ID",
  "realAppId": "$REAL_APP_ID",
  "buildType": "audit",
  "reproducible": $REPRODUCIBLE,
  "allowDirty": $ALLOW_DIRTY_JSON,
  "dryRun": $DRY_RUN_JSON
}
JSON

if [ "$REPRODUCIBLE" = "false" ]; then
  cat > "$REPORT_DIR/NON_REPRODUCIBLE.txt" <<TXT
Auditoria executada com arvore suja por --allow-dirty.
Este resultado nao deve ser usado como baseline reprodutivel.
Veja git-status.txt e git-diff.patch.
TXT
fi

echo "Audit dir: $REPORT_DIR"
echo "Branch: $BRANCH"
echo "Commit: $HEAD_SHA"
echo "Mode: $MODE"
echo "Suite: $SUITE"
echo "App ID: $APP_ID"
echo "Reproducible: $REPRODUCIBLE"

if [ "$DRY_RUN" = "1" ]; then
  echo "Dry-run: auditoria usaria :app:assembleAudit e pacote $APP_ID"
  exit 0
fi

./gradlew :app:assembleAudit --stacktrace --no-daemon

if [ ! -f "$APK" ]; then
  echo "Erro: APK audit nao encontrado em $APK" >&2
  exit 1
fi
APK_SIZE="$(wc -c < "$APK" | tr -d ' ')"
APK_SHA256="$(sha256sum "$APK" | awk '{print $1}')"
cat > "$REPORT_DIR/app-build.json" <<JSON
{
  "apk": "$APK",
  "sizeBytes": $APK_SIZE,
  "sha256": "$APK_SHA256",
  "gitSha": "$HEAD_SHA",
  "appId": "$APP_ID",
  "buildType": "audit",
  "reproducible": $REPRODUCIBLE
}
JSON

echo "APK: $APK"
echo "APK SHA-256: $APK_SHA256"

TEST_ARGS=""
if [ "$SUITE" = "smoke" ]; then
  TEST_ARGS="-Pandroid.testInstrumentationRunnerArguments.class=com.example.caderneta.e2e.SmokeNavigationE2ETest"
fi

if [ "$MODE" = "physical" ]; then
  if [ -z "$PHYSICAL_SERIAL" ]; then
    echo "Erro: modo fisico requer --physical SERIAL" >&2
    exit 2
  fi
  STATE="$($ADB -s "$PHYSICAL_SERIAL" get-state)"
  if [ "$STATE" != "device" ]; then
    echo "Erro: device $PHYSICAL_SERIAL em estado '$STATE', esperado 'device'" >&2
    exit 1
  fi
  "$ADB" -s "$PHYSICAL_SERIAL" install -r "$APK"
  if [ "$WIPE" = "1" ]; then
    "$ADB" -s "$PHYSICAL_SERIAL" shell pm clear "$APP_ID"
  fi
  "$ADB" -s "$PHYSICAL_SERIAL" logcat -c
  "$ADB" -s "$PHYSICAL_SERIAL" shell getprop > "$REPORT_DIR/device-info.txt"
  "$ADB" -s "$PHYSICAL_SERIAL" shell dumpsys package "$APP_ID" > "$REPORT_DIR/installed-package.txt"
  if ! grep "Package \[$APP_ID\]" "$REPORT_DIR/installed-package.txt" >/dev/null 2>&1; then
    echo "Erro: pacote audit $APP_ID nao confirmado no device" >&2
    exit 1
  fi
  GRADLE_TASK=":app:connectedAuditAndroidTest"
  set +e
  ANDROID_SERIAL="$PHYSICAL_SERIAL" ./gradlew $GRADLE_TASK $TEST_ARGS --stacktrace --no-daemon
  TEST_EXIT_CODE="$?"
  set -e
  if "$ADB" -s "$PHYSICAL_SERIAL" logcat -d > "$REPORT_DIR/logcat/full-logcat.txt"; then
    :
  else
    echo "Aviso: falha ao coletar logcat" > "$REPORT_DIR/logcat/logcat-error.txt"
  fi
else
  GRADLE_TASK=":app:pixelApi35AuditAndroidTest"
  set +e
  ./gradlew $GRADLE_TASK $TEST_ARGS --stacktrace --no-daemon
  TEST_EXIT_CODE="$?"
  set -e
fi

copy_if_exists "app/build/outputs/androidTest-results" "$REPORT_DIR/test-results"
copy_if_exists "app/build/reports/androidTests" "$REPORT_DIR/android-test-reports"
copy_matching_dirs "connected_android_test_additional_output" "$REPORT_DIR"
copy_matching_dirs "managed_device_android_test_additional_output" "$REPORT_DIR"
copy_matching_dirs "additional_test_output" "$REPORT_DIR"
flatten_test_artifacts

python3 scripts/audit/gen_report.py \
  --root "$REPORT_DIR" \
  --mode "$MODE" \
  --suite "$SUITE" \
  --gradle-task "$GRADLE_TASK" \
  --test-exit-code "$TEST_EXIT_CODE" \
  --apk-path "$APK" \
  --apk-sha256 "$APK_SHA256" \
  --apk-size "$APK_SIZE"

ZIP_PATH="$REPORT_DIR/app-audit-$TIMESTAMP-$HEAD_SHORT.zip"
(
  cd "$REPORT_DIR"
  zip -qr "app-audit-$TIMESTAMP-$HEAD_SHORT.zip" .
)

echo "Report: $REPORT_DIR/report.md"
echo "HTML: $REPORT_DIR/report.html"
echo "Visual review: $REPORT_DIR/visual-review.md"
echo "Action plan: $REPORT_DIR/action-plan.md"
echo "Zip: $ZIP_PATH"

if [ "$MODE" = "gmd" ] && [ "$KEEP_DEVICE" = "0" ]; then
  echo "GMD lifecycle handled by Gradle task."
fi

exit "$TEST_EXIT_CODE"
