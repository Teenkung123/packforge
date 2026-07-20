#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 4 ]]; then
  echo "usage: $0 <fabric|forge|neoforge> <target-key> [output-directory] [timeout-seconds]" >&2
  exit 2
fi

platform="$1"
target="$2"
output_directory="${3:-build/runtime-benchmark/$target-$platform}"
timeout_seconds="${4:-900}"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
run_root="$repository_root/platform/$platform/run/$target"
timings_file="$run_root/logs/packforge-timings.csv"

cd "$repository_root"
mkdir -p "$output_directory"
output_directory="$(cd "$output_directory" && pwd)"

extract_warm_samples() {
  local source_file="$1"
  local destination_file="$2"
  if [[ ! -f "$source_file" ]]; then
    echo "missing PackForge timing output: $source_file" >&2
    exit 1
  fi
  {
    head -n 1 "$source_file"
    tail -n 6 "$source_file"
  } > "$destination_file"
  if [[ "$(wc -l < "$destination_file")" -ne 7 ]]; then
    echo "warm benchmark did not produce one priming plus five measured reloads" >&2
    exit 1
  fi
}

run_mode() {
  local mode="$1"
  local optimizer="$2"
  local warm_output="$output_directory/$mode-warm.csv"
  local cold_output="$output_directory/$mode-cold.csv"
  local resource_hash_output="$output_directory/$mode-resource-hash.txt"
  local resource_hash

  PACKFORGE_RELOAD_COUNT=6 PACKFORGE_RELOAD_OPTIMIZER="$optimizer" PACKFORGE_RUNTIME_RESOURCE_HASH=true \
    "$repository_root/scripts/smoke-client.sh" "$platform" "$target" "$timeout_seconds"
  extract_warm_samples "$timings_file" "$warm_output"
  resource_hash="$(sed -n 's/.*PackForge resolved-resource hash:.*sha256=\([0-9a-f][0-9a-f]*\).*/\1/p' "$run_root/logs/latest.log" | tail -n 1)"
  if [[ -z "$resource_hash" ]]; then
    echo "warm benchmark did not emit a resolved-resource hash" >&2
    exit 1
  fi
  printf '%s\n' "$resource_hash" > "$resource_hash_output"

  head -n 1 "$warm_output" > "$cold_output"
  for process_number in 1 2 3; do
    PACKFORGE_RELOAD_COUNT=0 PACKFORGE_RELOAD_OPTIMIZER="$optimizer" \
      "$repository_root/scripts/smoke-client.sh" "$platform" "$target" "$timeout_seconds"
    if [[ ! -f "$timings_file" ]]; then
      echo "cold process $process_number did not produce timing output" >&2
      exit 1
    fi
    sed -n '2p' "$timings_file" >> "$cold_output"
  done
  if [[ "$(wc -l < "$cold_output")" -ne 4 ]]; then
    echo "cold benchmark did not produce three fresh-process samples" >&2
    exit 1
  fi
}

run_mode baseline false
run_mode optimized true

benchmark_log="$output_directory/pack-index-benchmark.log"
./gradlew -p "platform/$platform" -Ppackforge_target="$target" benchmarkPackIndex --no-daemon | tee "$benchmark_log"
pwsh -NoProfile -File "$repository_root/scripts/Test-ReloadBenchmark.ps1" \
  -BaselineWarmCsv "$output_directory/baseline-warm.csv" \
  -OptimizedWarmCsv "$output_directory/optimized-warm.csv" \
  -BaselineColdCsv "$output_directory/baseline-cold.csv" \
  -OptimizedColdCsv "$output_directory/optimized-cold.csv" \
  -BaselineHashFile "$output_directory/baseline-resource-hash.txt" \
  -OptimizedHashFile "$output_directory/optimized-resource-hash.txt" \
  | tee "$output_directory/result.json"
