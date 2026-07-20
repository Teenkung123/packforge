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

platform_root="platform/$platform"
run_root="$platform_root/run/$target"
log_file="$run_root/logs/latest.log"
gradle_log="$run_root/logs/packforge-smoke-gradle.log"
fixture_root="$platform_root/build/$target/benchmark"
fixture="$fixture_root/deterministic-large-pack.zip"
artifact_name=""

mkdir -p "$run_root/logs" "$run_root/config" "$run_root/resourcepacks"
rm -f "$log_file" "$gradle_log" "$run_root/logs/packforge-timings.csv"

./gradlew -p "$platform_root" -Ppackforge_target="$target" benchmarkPackIndex --no-daemon
cp "$fixture" "$run_root/resourcepacks/deterministic-large-pack.zip"

if [[ "$artifact_smoke" == "true" ]]; then
  ./gradlew -p "$platform_root" -Ppackforge_target="$target" build --no-daemon
  artifact_directory="$platform_root/build/$target/libs"
  mapfile -t artifacts < <(find "$artifact_directory" -maxdepth 1 -type f \
    -name "packforge-$platform-*.jar" \
    ! -name '*-sources.jar' ! -name '*-slim.jar' ! -name '*-named.jar' | sort)
  if [[ "${#artifacts[@]}" -ne 1 ]]; then
    echo "expected exactly one packaged artifact in $artifact_directory, found ${#artifacts[@]}" >&2
    printf '%s\n' "${artifacts[@]}" >&2
    exit 1
  fi
  artifact_name="$(basename "${artifacts[0]}")"
  mkdir -p "$run_root/mods"
  find "$run_root/mods" -maxdepth 1 -type f -name 'packforge-*.jar' -delete
  cp "${artifacts[0]}" "$run_root/mods/$artifact_name"
fi

cat > "$run_root/config/packforge.json" <<JSON
{
  "configVersion": 12,
  "reloadOptimizerEnabled": $optimizer_enabled,
  "loaderIndexEnabled": true,
  "loaderZipPoolEnabled": true,
  "loaderTimingsEnabled": true
}
JSON

cat > "$run_root/options.txt" <<'OPTIONS'
resourcePacks:["vanilla","file/deterministic-large-pack.zip"]
incompatibleResourcePacks:["file/deterministic-large-pack.zip"]
OPTIONS

fatal_pattern='Critical injection failure|Mixin apply failed|InjectionError|Minecraft has crashed|PackForge.*(ERROR|Exception)|\[.*ERROR\].*PackForge'
gradle_pid=""
wm_pid=""

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

if command -v openbox >/dev/null 2>&1; then
  openbox --sm-disable >"$run_root/logs/packforge-smoke-window-manager.log" 2>&1 &
  wm_pid=$!
  sleep 1
fi

run_arguments=(-p "$platform_root" -Ppackforge_target="$target")
if [[ "$artifact_smoke" == "true" ]]; then
  run_arguments+=(-Ppackforge_artifact_smoke=true)
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
  window_id="$(xdotool search --onlyvisible --name 'Minecraft' 2>/dev/null | head -n 1 || true)"
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
  echo "client did not reach a visible Minecraft window before timeout" >&2
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

echo "PackForge smoke passed: platform=$platform target=$target reloads=$reload_count optimizer=$optimizer_enabled packagedArtifact=$artifact_smoke resourceHash=$resource_hash cleanExit=true"
