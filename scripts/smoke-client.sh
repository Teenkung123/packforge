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
minecraft_version_override="${PACKFORGE_MINECRAFT_VERSION_OVERRIDE:-}"
neoforge_version_override="${PACKFORGE_NEOFORGE_VERSION_OVERRIDE:-}"
artifact_input_dir="${PACKFORGE_ARTIFACT_INPUT_DIR:-}"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

case "$platform" in
  fabric|forge|neoforge) ;;
  *) echo "unsupported platform: $platform" >&2; exit 2 ;;
esac

if [[ ! "$reload_count" =~ ^(0|[1-9][0-9]?)$ ]] || (( 10#$reload_count > 32 )); then
  echo "PACKFORGE_RELOAD_COUNT must be a decimal integer from 0 through 32" >&2
  exit 2
fi
reload_count=$((10#$reload_count))
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
if [[ -n "$minecraft_version_override" && "$platform" != "fabric" ]]; then
  echo "PACKFORGE_MINECRAFT_VERSION_OVERRIDE is valid only for Fabric smoke runs" >&2
  exit 2
fi
if [[ -n "$neoforge_version_override" && "$platform" != "neoforge" ]]; then
  echo "PACKFORGE_NEOFORGE_VERSION_OVERRIDE is valid only for NeoForge smoke runs" >&2
  exit 2
fi
for runtime_override in "$forge_version_override" "$minecraft_version_override" "$neoforge_version_override"; do
  if [[ -n "$runtime_override" && ! "$runtime_override" =~ ^[0-9A-Za-z._+-]+$ ]]; then
    echo "runtime version overrides may contain only letters, numbers, dot, underscore, plus, and hyphen" >&2
    exit 2
  fi
done
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
incompatibleResourcePacks:[]
OPTIONS

fatal_pattern='Critical injection failure|Mixin apply failed|Mixin apply for mod .* failed|InjectionError|InvalidInjectionException|could not find target|NoClassDefFoundError|ExceptionInInitializerError|Could not execute entrypoint stage|Mod resolution failed|Incompatible mods found|Minecraft has crashed|PackForge runtime smoke failure|PackForge.*(ERROR|Exception)|\[.*ERROR\].*PackForge'
gradle_pid=""
smoke_start_marker="$run_root/.packforge-smoke-start"
touch "$smoke_start_marker"

cleanup() {
  if [[ -n "$gradle_pid" ]] && kill -0 "$gradle_pid" 2>/dev/null; then
    kill "$gradle_pid" 2>/dev/null || true
    wait "$gradle_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

fatal_diagnostic_found() {
  if { [[ -f "$log_file" ]] && grep -Eiq "$fatal_pattern" "$log_file"; } \
    || { [[ -f "$gradle_log" ]] && grep -Eiq "$fatal_pattern" "$gradle_log"; }; then
    return 0
  fi
  [[ -d "$run_root/crash-reports" ]] \
    && find "$run_root/crash-reports" -type f -newer "$smoke_start_marker" -print -quit | grep -q .
}

print_fatal_diagnostics() {
  [[ -f "$log_file" ]] && grep -Ein "$fatal_pattern" "$log_file" >&2 || true
  [[ -f "$gradle_log" ]] && grep -Ein "$fatal_pattern" "$gradle_log" >&2 || true
  if [[ -d "$run_root/crash-reports" ]]; then
    find "$run_root/crash-reports" -type f -newer "$smoke_start_marker" -print >&2 || true
  fi
}

run_arguments=(-p "$platform_root" -Ppackforge_target="$target")
if [[ "$artifact_smoke" == "true" ]]; then
  run_arguments+=(-Ppackforge_artifact_smoke=true)
fi
if [[ -n "$forge_version_override" ]]; then
  run_arguments+=("-Ppackforge_forge_version_override=$forge_version_override")
fi
if [[ -n "$minecraft_version_override" ]]; then
  run_arguments+=("-Ppackforge_minecraft_version_override=$minecraft_version_override")
fi
if [[ -n "$neoforge_version_override" ]]; then
  run_arguments+=("-Ppackforge_neoforge_version_override=$neoforge_version_override")
fi
run_arguments+=(runClient --no-daemon)
smoke_java_tool_options="${JAVA_TOOL_OPTIONS:-}"
if [[ -n "$smoke_java_tool_options" ]]; then
  smoke_java_tool_options+=" "
fi
smoke_java_tool_options+="-Dpackforge.runtimeSmokeReloadCount=$reload_count"
JAVA_TOOL_OPTIONS="$smoke_java_tool_options" ./gradlew "${run_arguments[@]}" >"$gradle_log" 2>&1 &
gradle_pid=$!
deadline=$((SECONDS + timeout_seconds))
expected_reload_count=$((reload_count + 1))

while (( SECONDS < deadline )); do
  if fatal_diagnostic_found; then
    echo "fatal client or mixin diagnostic found" >&2
    print_fatal_diagnostics
    exit 1
  fi
  if [[ -f "$log_file" ]] \
    && grep -Fq 'PackForge capabilities:' "$log_file" \
    && (( $(grep -Fc 'PackForge reload complete:' "$log_file" || true) >= expected_reload_count )) \
    && { [[ "$resource_hash" == "false" ]] || (( $(grep -Fc 'PackForge resolved-resource hash:' "$log_file" || true) >= expected_reload_count )); } \
    && { [[ "$artifact_smoke" == "false" ]] || grep -Fq "$artifact_name" "$log_file"; } \
    && grep -Fq 'PackForge runtime smoke ready:' "$log_file" \
    && grep -Fq 'PackForge runtime smoke complete:' "$log_file"; then
    break
  fi
  if ! kill -0 "$gradle_pid" 2>/dev/null; then
    set +e
    wait "$gradle_pid"
    client_status=$?
    set -e
    gradle_pid=""
    echo "client exited before completing the runtime smoke controller (status $client_status)" >&2
    tail -n 200 "$gradle_log" >&2 || true
    [[ -f "$log_file" ]] && tail -n 200 "$log_file" >&2 || true
    exit 1
  fi
  sleep 2
done

if [[ ! -f "$log_file" ]] \
  || ! grep -Fq 'PackForge capabilities:' "$log_file" \
  || (( $(grep -Fc 'PackForge reload complete:' "$log_file" || true) < expected_reload_count )) \
  || { [[ "$resource_hash" == "true" ]] && (( $(grep -Fc 'PackForge resolved-resource hash:' "$log_file" || true) < expected_reload_count )); } \
  || { [[ "$artifact_smoke" == "true" ]] && ! grep -Fq "$artifact_name" "$log_file"; } \
  || ! grep -Fq 'PackForge runtime smoke ready:' "$log_file" \
  || ! grep -Fq 'PackForge runtime smoke complete:' "$log_file"; then
  has_log=false
  has_capabilities=false
  completed_reloads=0
  completed_resource_hashes=0
  has_artifact=false
  has_controller_ready=false
  has_controller_complete=false
  [[ -f "$log_file" ]] && has_log=true
  [[ -f "$log_file" ]] && grep -Fq 'PackForge capabilities:' "$log_file" && has_capabilities=true
  [[ -f "$log_file" ]] && completed_reloads="$(grep -Fc 'PackForge reload complete:' "$log_file" || true)"
  [[ -f "$log_file" ]] && completed_resource_hashes="$(grep -Fc 'PackForge resolved-resource hash:' "$log_file" || true)"
  { [[ "$artifact_smoke" == "false" ]] || { [[ -f "$log_file" ]] && grep -Fq "$artifact_name" "$log_file"; }; } \
    && has_artifact=true
  [[ -f "$log_file" ]] && grep -Fq 'PackForge runtime smoke ready:' "$log_file" && has_controller_ready=true
  [[ -f "$log_file" ]] && grep -Fq 'PackForge runtime smoke complete:' "$log_file" && has_controller_complete=true
  echo "client readiness timeout: log=$has_log capabilities=$has_capabilities reloads=$completed_reloads/$expected_reload_count resourceHashes=$completed_resource_hashes/$expected_reload_count artifact=$has_artifact controllerReady=$has_controller_ready controllerComplete=$has_controller_complete" >&2
  tail -n 200 "$gradle_log" >&2 || true
  [[ -f "$log_file" ]] && tail -n 200 "$log_file" >&2 || true
  exit 1
fi

exit_deadline=$((SECONDS + 90))
while kill -0 "$gradle_pid" 2>/dev/null && (( SECONDS < exit_deadline )); do
  sleep 2
done
if kill -0 "$gradle_pid" 2>/dev/null; then
  echo "client did not exit cleanly after the runtime smoke controller completed" >&2
  exit 1
fi

set +e
wait "$gradle_pid"
client_status=$?
set -e
gradle_pid=""
if (( client_status != 0 )); then
  echo "runClient exited with status $client_status" >&2
  exit "$client_status"
fi

if grep -Eiq "$fatal_pattern" "$log_file"; then
  echo "fatal diagnostic found after client exit" >&2
  exit 1
fi

echo "PackForge smoke passed: platform=$platform target=$target profile=$smoke_profile reloads=$reload_count optimizer=$optimizer_enabled packagedArtifact=$artifact_smoke resourceHash=$resource_hash cleanExit=true"
