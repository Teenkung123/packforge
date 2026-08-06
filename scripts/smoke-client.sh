#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: $0 <fabric|forge|neoforge> <target-key> [timeout-seconds]" >&2
  exit 2
fi

platform="$1"
target="$2"
timeout_seconds="${3:-600}"
reload_count="${PACKFORGE_RELOAD_COUNT:-2}"
optimizer_enabled="${PACKFORGE_RELOAD_OPTIMIZER:-true}"
artifact_smoke="${PACKFORGE_ARTIFACT_SMOKE:-true}"
resource_hash="${PACKFORGE_RUNTIME_RESOURCE_HASH:-false}"
smoke_profile="${PACKFORGE_SMOKE_PROFILE:-default}"
forge_version_override="${PACKFORGE_FORGE_VERSION_OVERRIDE:-}"
artifact_input_dir="${PACKFORGE_ARTIFACT_INPUT_DIR:-}"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

case "$platform" in
  fabric|forge|neoforge) ;;
  *) echo "unsupported platform: $platform" >&2; exit 2 ;;
esac

if [[ ! "$reload_count" =~ ^[0-9]+$ ]]; then
  echo "PACKFORGE_RELOAD_COUNT must be a non-negative integer" >&2
  exit 2
fi
if [[ "$optimizer_enabled" != "true" && "$optimizer_enabled" != "false" ]]; then
  echo "PACKFORGE_RELOAD_OPTIMIZER must be true or false" >&2
  exit 2
fi
if [[ "$artifact_smoke" != "true" && "$artifact_smoke" != "false" ]]; then
  echo "PACKFORGE_ARTIFACT_SMOKE must be true or false" >&2
  exit 2
fi
if [[ "$resource_hash" != "true" && "$resource_hash" != "false" ]]; then
  echo "PACKFORGE_RUNTIME_RESOURCE_HASH must be true or false" >&2
  exit 2
fi
if [[ -n "$forge_version_override" && "$platform" != "forge" ]]; then
  echo "PACKFORGE_FORGE_VERSION_OVERRIDE is valid only for Forge smoke runs" >&2
  exit 2
fi
if [[ "$platform" == "forge" && "$artifact_smoke" == "true" ]]; then
  echo "Forge final SRG JARs cannot be tested in ForgeGradle's Mojmap userdev runtime; use source mode here and scripts/Smoke-Forge-Production.ps1 for final-JAR acceptance" >&2
  exit 2
fi

case "$smoke_profile" in
  core)
    loading_status=false; fade_disabled=false; reload_toast=false
    diagnostics=false
    ;;
  default)
    loading_status=true; fade_disabled=false; reload_toast=true
    diagnostics=false
    ;;
  diagnostics)
    loading_status=true; fade_disabled=false; reload_toast=true
    diagnostics=true
    ;;
  ui)
    loading_status=true; fade_disabled=true; reload_toast=true
    diagnostics=false
    ;;
  *)
    echo "PACKFORGE_SMOKE_PROFILE must be core, default, diagnostics, or ui" >&2
    exit 2
    ;;
esac

if [[ -z "${DISPLAY:-}" ]]; then
  if ! command -v xvfb-run >/dev/null 2>&1; then
    echo "DISPLAY is unset and xvfb-run is unavailable" >&2
    exit 2
  fi
  exec xvfb-run -a -s "-screen 0 1280x720x24" "$0" "$platform" "$target" "$timeout_seconds"
fi

if ! command -v xdotool >/dev/null 2>&1; then
  echo "xdotool is required for clean client reload/exit automation" >&2
  exit 2
fi

cd "$repository_root"
chmod +x gradlew

artifact_name="$(python - "$platform" "$target" <<'PY'
import json
import pathlib
import sys

platform, target_key = sys.argv[1:]
root = pathlib.Path.cwd()
registry = json.loads((root / "gradle/minecraft-targets.json").read_text(encoding="utf-8"))
target = next((item for item in registry["targets"] if item["key"] == target_key), None)
if target is None or platform not in target["platforms"]:
    raise SystemExit(f"unsupported target/platform: {target_key}/{platform}")
properties = {}
for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
version = properties["mod_version"] + target["platforms"][platform]["versionSuffix"]
print(f"packforge-{platform}-{version}-mc{target['artifactMinecraft']}.jar")
PY
)"

platform_root="platform/$platform"
run_root="$platform_root/run/$target"
log_file="$run_root/logs/latest.log"
gradle_log="$run_root/logs/packforge-smoke-gradle.log"
fixture_root="$platform_root/build/$target/benchmark"
fixture="$fixture_root/deterministic-large-pack.zip"

mkdir -p "$run_root/logs" "$run_root/config" "$run_root/resourcepacks"
rm -f "$log_file" "$gradle_log" "$run_root/logs/packforge-timings.csv" \
  "$run_root/logs/packforge-font-timings.csv" "$run_root/logs/packforge-atlas-timings.csv"

./gradlew -p "$platform_root" -Ppackforge_target="$target" benchmarkPackIndex --no-daemon
cp "$fixture" "$run_root/resourcepacks/deterministic-large-pack.zip"

mkdir -p "$run_root/mods"
find "$run_root/mods" -maxdepth 1 -type f -name 'packforge-*.jar' -delete

if [[ "$artifact_smoke" == "true" ]]; then
  if [[ -n "$artifact_input_dir" ]]; then
    artifact_path="$artifact_input_dir/$artifact_name"
  else
    ./gradlew -p "$platform_root" -Ppackforge_target="$target" build --no-daemon
    artifact_path="$platform_root/build/$target/libs/$artifact_name"
  fi
  if [[ ! -f "$artifact_path" ]]; then
    echo "expected packaged artifact not found: $artifact_path" >&2
    exit 1
  fi
  cp "$artifact_path" "$run_root/mods/$artifact_name"
fi

cat > "$run_root/config/packforge.json" <<JSON
{
  "configVersion": 12,
  "reloadOptimizerEnabled": $optimizer_enabled,
  "loaderIndexEnabled": true,
  "loaderZipPoolEnabled": true,
  "loaderTimingsEnabled": true,
  "reloadListenerTimingsEnabled": $diagnostics,
  "shaderApplyStallDiagnosticsEnabled": $diagnostics,
  "immediatelyFastFontAtlasCompatEnabled": true,
  "loadingStatusOverlayEnabled": $loading_status,
  "loadingScreenFadeOutDisabled": $fade_disabled,
  "reloadSummaryToastEnabled": $reload_toast,
  "fontReloadDiagnosticsEnabled": $diagnostics,
  "fontPrepareProviderSelectionEnabled": true,
  "fontBitmapProviderCacheEnabled": true,
  "modelParseBatchingEnabled": true,
  "modelParseTimingEnabled": $diagnostics,
  "modelAdaptiveBatchingEnabled": true,
  "modelDuplicateParseCacheEnabled": true,
  "atlasPhaseTimingsEnabled": $diagnostics,
  "atlasDecodeBatchingEnabled": true
}
JSON

cat > "$run_root/options.txt" <<'OPTIONS'
resourcePacks:["vanilla","file/deterministic-large-pack.zip"]
incompatibleResourcePacks:["file/deterministic-large-pack.zip"]
OPTIONS

fatal_pattern='Critical injection failure|Mixin apply failed|InjectionError|Minecraft has crashed|PackForge.*(ERROR|Exception)|\[.*ERROR\].*PackForge'
gradle_pid=""
wm_pid=""
preexisting_minecraft_windows=""

cleanup() {
  if [[ -n "$gradle_pid" ]] && kill -0 "$gradle_pid" 2>/dev/null; then
    kill "$gradle_pid" 2>/dev/null || true
    wait "$gradle_pid" 2>/dev/null || true
  fi
  if [[ -n "$wm_pid" ]] && kill -0 "$wm_pid" 2>/dev/null; then
    kill "$wm_pid" 2>/dev/null || true
    wait "$wm_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

is_descendant_process() {
  local child_pid="$1"
  while [[ "$child_pid" =~ ^[0-9]+$ ]] && (( child_pid > 1 )); do
    if [[ "$child_pid" == "$gradle_pid" ]]; then
      return 0
    fi
    child_pid="$(ps -o ppid= -p "$child_pid" 2>/dev/null | tr -d '[:space:]')"
  done
  return 1
}

find_owned_minecraft_window() {
  local candidate window_pid window_cwd expected_cwd
  expected_cwd="$(readlink -f "$run_root" 2>/dev/null || true)"
  while read -r candidate; do
    [[ -n "$candidate" ]] || continue
    if ! grep -Fxq "$candidate" <<<"$preexisting_minecraft_windows"; then
      echo "$candidate"
      return 0
    fi
    window_pid="$(xdotool getwindowpid "$candidate" 2>/dev/null || true)"
    [[ -n "$window_pid" ]] || continue
    window_cwd="$(readlink -f "/proc/$window_pid/cwd" 2>/dev/null || true)"
    if is_descendant_process "$window_pid" \
      || { [[ -n "$expected_cwd" ]] && [[ "$window_cwd" == "$expected_cwd" ]]; }; then
      echo "$candidate"
      return 0
    fi
  done < <(xdotool search --name 'Minecraft' 2>/dev/null || true)
  return 1
}

if command -v openbox >/dev/null 2>&1; then
  openbox --sm-disable >"$run_root/logs/packforge-smoke-window-manager.log" 2>&1 &
  wm_pid=$!
  sleep 1
fi
# Xvfb windows may be unmapped or omit _NET_WM_PID, so ownership also uses a
# pre-launch snapshot and windowactivate maps the newly created client window.
preexisting_minecraft_windows="$(xdotool search --name 'Minecraft' 2>/dev/null || true)"

run_arguments=(-p "$platform_root" -Ppackforge_target="$target")
if [[ "$artifact_smoke" == "true" ]]; then
  run_arguments+=(-Ppackforge_artifact_smoke=true)
fi
if [[ -n "$forge_version_override" ]]; then
  run_arguments+=("-Ppackforge_forge_version_override=$forge_version_override")
fi
run_arguments+=(runClient --no-daemon)
./gradlew "${run_arguments[@]}" >"$gradle_log" 2>&1 &
gradle_pid=$!
deadline=$((SECONDS + timeout_seconds))
window_id=""

while (( SECONDS < deadline )); do
  if [[ -f "$log_file" ]] && grep -Eiq "$fatal_pattern" "$log_file"; then
    echo "fatal client or mixin diagnostic found" >&2
    grep -Ein "$fatal_pattern" "$log_file" >&2 || true
    exit 1
  fi
  window_id="$(find_owned_minecraft_window || true)"
  if [[ -n "$window_id" && -f "$log_file" ]] \
    && grep -Fq 'PackForge capabilities:' "$log_file" \
    && grep -Fq 'PackForge reload complete:' "$log_file" \
    && { [[ "$resource_hash" == "false" ]] || grep -Fq 'PackForge resolved-resource hash:' "$log_file"; } \
    && { [[ "$artifact_smoke" == "false" ]] || grep -Fq "$artifact_name" "$log_file"; }; then
    break
  fi
  if ! kill -0 "$gradle_pid" 2>/dev/null; then
    wait "$gradle_pid" || true
    echo "client exited before reaching the title window" >&2
    tail -n 200 "$gradle_log" >&2 || true
    [[ -f "$log_file" ]] && tail -n 200 "$log_file" >&2 || true
    exit 1
  fi
  sleep 2
done

if [[ -z "$window_id" || ! -f "$log_file" ]] \
  || ! grep -Fq 'PackForge capabilities:' "$log_file" \
  || ! grep -Fq 'PackForge reload complete:' "$log_file" \
  || { [[ "$resource_hash" == "true" ]] && ! grep -Fq 'PackForge resolved-resource hash:' "$log_file"; } \
  || { [[ "$artifact_smoke" == "true" ]] && ! grep -Fq "$artifact_name" "$log_file"; }; then
  has_log=false
  has_capabilities=false
  has_reload=false
  has_resource_hash=false
  has_artifact=false
  [[ -f "$log_file" ]] && has_log=true
  [[ -f "$log_file" ]] && grep -Fq 'PackForge capabilities:' "$log_file" && has_capabilities=true
  [[ -f "$log_file" ]] && grep -Fq 'PackForge reload complete:' "$log_file" && has_reload=true
  { [[ "$resource_hash" == "false" ]] || { [[ -f "$log_file" ]] && grep -Fq 'PackForge resolved-resource hash:' "$log_file"; }; } \
    && has_resource_hash=true
  { [[ "$artifact_smoke" == "false" ]] || { [[ -f "$log_file" ]] && grep -Fq "$artifact_name" "$log_file"; }; } \
    && has_artifact=true
  echo "client readiness timeout: window=$([[ -n "$window_id" ]] && echo true || echo false) log=$has_log capabilities=$has_capabilities reload=$has_reload resourceHash=$has_resource_hash artifact=$has_artifact" >&2
  tail -n 200 "$gradle_log" >&2 || true
  [[ -f "$log_file" ]] && tail -n 200 "$log_file" >&2 || true
  exit 1
fi

for ((reload = 1; reload <= reload_count; reload++)); do
  previous_count="$(grep -Fc 'PackForge reload complete:' "$log_file" || true)"
  previous_hash_count="$(grep -Fc 'PackForge resolved-resource hash:' "$log_file" || true)"
  xdotool windowactivate --sync "$window_id"
  xdotool keydown --window "$window_id" F3
  xdotool key --window "$window_id" t
  xdotool keyup --window "$window_id" F3

  reload_deadline=$((SECONDS + 180))
  while (( SECONDS < reload_deadline )); do
    if grep -Eiq "$fatal_pattern" "$log_file"; then
      echo "fatal diagnostic found during reload $reload" >&2
      exit 1
    fi
    current_count="$(grep -Fc 'PackForge reload complete:' "$log_file" || true)"
    current_hash_count="$(grep -Fc 'PackForge resolved-resource hash:' "$log_file" || true)"
    if (( current_count > previous_count )) \
      && { [[ "$resource_hash" == "false" ]] || (( current_hash_count > previous_hash_count )); }; then
      break
    fi
    sleep 2
  done
  current_count="$(grep -Fc 'PackForge reload complete:' "$log_file" || true)"
  current_hash_count="$(grep -Fc 'PackForge resolved-resource hash:' "$log_file" || true)"
  if (( current_count <= previous_count )) \
    || { [[ "$resource_hash" == "true" ]] && (( current_hash_count <= previous_hash_count )); }; then
    echo "resource reload $reload did not complete" >&2
    exit 1
  fi
done

xdotool windowclose "$window_id"
exit_deadline=$((SECONDS + 90))
while kill -0 "$gradle_pid" 2>/dev/null && (( SECONDS < exit_deadline )); do
  sleep 2
done
if kill -0 "$gradle_pid" 2>/dev/null; then
  echo "client did not exit cleanly after closing its window" >&2
  exit 1
fi

set +e
wait "$gradle_pid"
client_status=$?
set -e
gradle_pid=""
if [[ -n "$wm_pid" ]] && kill -0 "$wm_pid" 2>/dev/null; then
  kill "$wm_pid" 2>/dev/null || true
  wait "$wm_pid" 2>/dev/null || true
fi
wm_pid=""
if (( client_status != 0 )); then
  echo "runClient exited with status $client_status" >&2
  exit "$client_status"
fi

if grep -Eiq "$fatal_pattern" "$log_file"; then
  echo "fatal diagnostic found after client exit" >&2
  exit 1
fi

echo "PackForge smoke passed: platform=$platform target=$target profile=$smoke_profile reloads=$reload_count optimizer=$optimizer_enabled packagedArtifact=$artifact_smoke resourceHash=$resource_hash cleanExit=true"
