"""Strict, dependency-free scheduling and process-level benchmark admission."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import re
import statistics
import sys

VERSIONS = ("26.1.2", "26.2")
LOADERS = ("fabric", "forge", "neoforge")
MODES = ("vanilla", "current", "corrected", "candidate")
REFERENCE_MODES = ("vanilla", "quickpack")
SEED = 260102
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
RUNTIME_TEMP_ROOT = Path(__file__).resolve().parents[2] / ".gradle" / "modernization-temp"
OPTIMIZER_PACK_IDS = {
    "packforge": {"packforge", "mod/packforge", "mod:packforge"},
    "quickpack": {name for stem in ("quickpack", "quick-pack", "quick_pack") for name in (stem, "mod/" + stem, "mod:" + stem)},
}


class Invalid(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise Invalid(message)


def integer(value):
    return type(value) is int


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False)


def parse_json(text):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, f"duplicate JSON key: {key}")
            result[key] = value
        return result
    return json.loads(text, object_pairs_hook=pairs,
                      parse_constant=lambda value: (_ for _ in ()).throw(Invalid(f"invalid number: {value}")))


def read_json(path):
    return parse_json(Path(path).read_text(encoding="utf-8-sig"))


def make_schedule(seed=SEED, quickpack_cells=(), companion_workloads=(), reference_blocks=3):
    require(integer(reference_blocks) and 1 <= reference_blocks <= 10, "reference blocks must be an integer in 1..10")
    reference_indexes = ([4] if reference_blocks == 1 else
                         [round(index * 9 / (reference_blocks - 1)) for index in range(reference_blocks)])
    available = {f"{version}-{loader}" for version in VERSIONS for loader in LOADERS}
    require(set(quickpack_cells) <= available, "unknown Quick Pack cell")
    workloads = ["developrp"] + list(companion_workloads)
    require(len(workloads) == len(set(workloads)), "duplicate workload")
    require(all(re.fullmatch(r"[a-z0-9_]+", x) for x in workloads), "invalid workload ID")
    rng = random.Random(seed)
    cells = []
    for workload in workloads:
        for version in VERSIONS:
            for loader in LOADERS:
                base = f"{version}-{loader}"
                modes = list(MODES) + (["quickpack"] if base in quickpack_cells else [])
                rng.shuffle(modes)
                cells.append({"cellId": f"{base}-{workload}", "minecraft": version,
                              "loader": loader, "workload": workload, "modes": modes,
                              "processesPerMode": {mode: reference_blocks if mode in REFERENCE_MODES else 10 for mode in modes}})
    runs = []
    for block in range(10):
        order = cells[:]
        rng.shuffle(order)
        for cell in order:
            modes = cell["modes"]
            if reference_blocks == 10:
                # Original equal-count design balances every full-order position.
                shift = block % len(modes)
                ordered_modes = modes[shift:] + modes[:shift]
            else:
                # Balance primary order independently; dropping reference runs from
                # a four/five-mode Latin rotation would bias primary positions.
                primary = [mode for mode in modes if mode not in REFERENCE_MODES]
                shift = block % len(primary)
                ordered_modes = primary[shift:] + primary[:shift]
                if block in reference_indexes:
                    references = [mode for mode in modes if mode in REFERENCE_MODES]
                    expanded = [None] * len(modes)
                    occurrence = reference_indexes.index(block)
                    offset = modes.index(references[0])
                    for index, mode in enumerate(references):
                        expanded[(offset + occurrence + 2 * index) % len(expanded)] = mode
                    primary_order = iter(ordered_modes)
                    ordered_modes = [next(primary_order) if mode is None else mode for mode in expanded]
            for mode in ordered_modes:
                runs.append({"runId": f"{cell['cellId']}-{mode}-{block:02d}",
                             "cellId": cell["cellId"], "mode": mode, "block": block,
                             "workload": cell["workload"], "minecraft": cell["minecraft"],
                             "loader": cell["loader"], "primingReloads": 1, "measuredReloads": 5})
    return {"schema": 1, "seed": seed, "primaryBlocks": 10, "referenceBlocks": reference_blocks,
            "referenceBlockIndices": reference_indexes,
            "filesystemCache": "uncontrolled", "cells": cells, "runs": runs}


def validate_schedule(schedule):
    require(isinstance(schedule, dict) and integer(schedule.get("schema")) and schedule["schema"] == 1, "invalid schedule schema")
    require(integer(schedule.get("seed")), "schedule seed must be integer")
    cells = schedule.get("cells", [])
    require(isinstance(cells, list) and cells, "missing schedule cells")
    companions = list(dict.fromkeys(c["workload"] for c in cells if c["workload"] != "developrp"))
    quick = {f"{c['minecraft']}-{c['loader']}" for c in cells if "quickpack" in c["modes"]}
    expected = make_schedule(schedule["seed"], quick, companions, schedule.get("referenceBlocks"))
    require(schedule == expected, "schedule differs from its deterministic complete design")


def hash_list(value, name, nonempty=False):
    require(isinstance(value, list) and (value or not nonempty), f"invalid {name}")
    ids = set()
    for entry in value:
        require(isinstance(entry, dict) and isinstance(entry.get("id"), str) and entry["id"], f"invalid {name} identity")
        require(entry["id"] not in ids, f"duplicate {name} identity")
        require(isinstance(entry.get("sha256"), str) and SHA256.fullmatch(entry["sha256"]), f"invalid {name} hash")
        ids.add(entry["id"])


def validate_context(context, run):
    require(isinstance(context, dict), "missing context")
    for key in ("minecraft", "loader", "loaderVersion"):
        require(isinstance(context.get(key), str) and context[key], f"missing {key}")
    require(context["minecraft"] == run["minecraft"] and context["loader"] == run["loader"], "wrong game/loader")
    jdk = context.get("jdk", {})
    require(isinstance(jdk, dict) and all(isinstance(jdk.get(k), str) and jdk[k] for k in ("version", "vendor", "executableSha256")), "missing exact JDK")
    require(SHA256.fullmatch(jdk["executableSha256"]), "invalid JDK hash")
    hardware = context.get("hardware", {})
    require(isinstance(hardware, dict) and all(isinstance(hardware.get(k), str) and hardware[k] for k in ("cpu", "gpu", "os")), "missing hardware context")
    require(integer(hardware.get("ramBytes")) and hardware["ramBytes"] > 0, "invalid RAM context")
    require(isinstance(context.get("jvmArgs"), list) and all(isinstance(x, str) for x in context["jvmArgs"]), "missing JVM arguments")
    environment = context.get("runtimeEnvironment")
    require(isinstance(environment, dict) and set(environment) == {"TEMP", "TMP"}, "runtime environment must record only controlled TEMP and TMP")
    for key in ("TEMP", "TMP"):
        value = environment[key]
        require(isinstance(value, str) and value and Path(value).is_absolute(), f"missing absolute controlled {key}")
        require(Path(value).resolve() == RUNTIME_TEMP_ROOT.resolve(), f"{key} must use private repository .gradle/modernization-temp")
    require(environment["TEMP"] == environment["TMP"], "TEMP and TMP must name the same controlled directory")
    require(isinstance(context.get("graphicsSettings"), dict) and context["graphicsSettings"], "missing graphics settings")
    for dimension in ("width", "height"):
        require(integer(context["graphicsSettings"].get(dimension)) and context["graphicsSettings"][dimension] > 0, f"invalid expected framebuffer {dimension}")
    require(context.get("optionsSnapshotPinned") is True, "complete options snapshot was not pinned")
    options = context.get("effectiveOptions")
    require(isinstance(options, dict) and options and all(isinstance(k, str) and k and isinstance(v, str) for k, v in options.items()), "effective options must be a nonempty complete string map")
    for key in ("lang", "forceUnicodeFont", "japaneseGlyphVariants", "textureFiltering", "mipmapLevels",
                "graphicsPreset", "resourcePacks", "inactivityFpsLimit", "enableVsync", "maxFps"):
        require(key in options and options[key].strip(), f"missing critical effective option: {key}")
    for key in ("forceUnicodeFont", "japaneseGlyphVariants", "enableVsync"):
        require(options[key] in ("true", "false"), f"invalid effective boolean option: {key}")
    for key in ("textureFiltering", "mipmapLevels", "maxFps"):
        require(re.fullmatch(r"[0-9]+", options[key]), f"invalid effective numeric option: {key}")
    require(context.get("filesystemCache") == "uncontrolled", "fresh-process schedule requires explicit uncontrolled filesystem cache")
    require(context.get("profilingEnabled") is False and context.get("resourceHashingEnabled") is False, "instrumented run is not a headline measurement")
    for name in ("orderedPacks", "artifacts", "configs"):
        hash_list(context.get(name), name, nonempty=name != "configs")
    require(any(item["id"] == "options.txt" for item in context["configs"]), "missing full options.txt hash")
    ids = {item["id"] for item in context["artifacts"]}
    require("texture_correctness" not in ids, "correctness capture artifact present in headline run")
    require("observer" in ids, "missing observer artifact")
    if run["mode"] in ("current", "corrected", "candidate"):
        require("packforge" in ids and "quickpack" not in ids, "wrong optimization artifacts")
    elif run["mode"] == "quickpack":
        require("quickpack" in ids and "packforge" not in ids, "wrong Quick Pack comparison artifacts")
    else:
        require("quickpack" not in ids and "packforge" not in ids, "vanilla comparison contains optimization mod")


def validate_process(manifest, events, run):
    require(isinstance(manifest, dict) and integer(manifest.get("schema")) and manifest["schema"] == 1, "invalid manifest schema")
    for key in ("runId", "cellId", "mode", "block", "workload"):
        require(manifest.get(key) == run[key], f"manifest mismatch: {key}")
    require(integer(manifest.get("block")), "invalid block")
    require(isinstance(manifest.get("sessionId"), str) and manifest["sessionId"], "missing session ID")
    require(integer(manifest.get("pid")) and manifest["pid"] > 0, "missing actual PID")
    require(integer(manifest.get("processStartedEpochMillis")) and manifest["processStartedEpochMillis"] > 0, "missing process creation timestamp")
    require(integer(manifest.get("exitCode")) and manifest["exitCode"] == 0, "process did not exit cleanly")
    require(manifest.get("valid") is True, "runner did not validate process")
    require(manifest.get("inputsUnchanged") is True, "benchmark inputs changed or were not verified")
    require(manifest.get("timeout") is False, "process timed out or timeout state is unknown")
    validate_context(manifest.get("context"), run)
    expected = [("ready", -1)]
    for index in range(6):
        expected.extend((event, index) for event in ("reload_requested", "reload_complete", "frame_ready"))
    expected.append(("complete", 5))
    require(isinstance(events, list) and len(events) == len(expected), "incomplete or extra observer events")
    previous = None
    active_packs = None
    expected_packs = [p["id"] if p["id"].startswith("file/") else "file/" + p["id"] for p in manifest["context"]["orderedPacks"]]
    for event, (kind, index) in zip(events, expected):
        require(isinstance(event, dict) and integer(event.get("schema")) and event["schema"] == 1, "invalid event schema")
        require(event.get("sessionId") == manifest["sessionId"], "mixed observer sessions")
        require(event.get("event") == kind and integer(event.get("reloadIndex")) and event["reloadIndex"] == index, f"out-of-order/error/cancelled event; expected {kind}:{index}")
        require(event.get("windowActive") is True, "observer window inactive or activity state missing/invalid")
        for dimension in ("Width", "Height"):
            value = event.get("framebuffer" + dimension)
            require(integer(value) and value == manifest["context"]["graphicsSettings"][dimension.lower()], f"missing/invalid or changed framebuffer {dimension.lower()}")
        if kind in ("ready", "frame_ready"):
            packs = event.get("activePackIds")
            require(isinstance(packs, list) and packs and all(isinstance(p, str) and p for p in packs), "missing/invalid active pack IDs")
            require(len(packs) == len(set(packs)), "duplicate active pack IDs")
            require([p for p in packs if p in expected_packs] == expected_packs, "primary packs missing or precedence changed")
            require(active_packs is None or active_packs == packs, "active pack stack changed within process")
            active_packs = packs
        for clock in ("epochMillis", "nanoTime", "jvmUptimeMillis"):
            require(integer(event.get(clock)), f"missing event clock {clock}")
            require(previous is None or event[clock] >= previous[clock], f"nonmonotonic {clock}")
        require(event["jvmUptimeMillis"] >= 0, "negative JVM uptime")
        previous = event
    startup = events[0]["epochMillis"] - manifest["processStartedEpochMillis"]
    require(startup > 0, "ready precedes actual process start")
    reloads, completions = [], []
    for index in range(6):
        requested, completed, frame = events[1 + index * 3:4 + index * 3]
        elapsed = (frame["nanoTime"] - requested["nanoTime"]) / 1_000_000
        completion = (completed["nanoTime"] - requested["nanoTime"]) / 1_000_000
        require(elapsed > 0 and completion > 0, "nonpositive reload duration")
        if index:
            reloads.append(elapsed)
            completions.append(completion)
    return {"startupMs": startup, "reloadMs": statistics.median(reloads),
            "activePackIds": active_packs,
            "reloadCompleteMs": statistics.median(completions), "measuredReloadsMs": reloads,
            "measuredReloadCompletionsMs": completions}


def percentile(values, fraction):
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    low, high = math.floor(position), math.ceil(position)
    return ordered[low] + (ordered[high] - ordered[low]) * (position - low)


def comparison(baseline, candidate, seed, iterations=10000):
    require(len(baseline) == len(candidate) == 10, "comparison requires ten paired processes")
    require(integer(iterations) and iterations > 0, "bootstrap iterations must be positive")
    require(all(isinstance(x, (int, float)) and not isinstance(x, bool) and math.isfinite(x) and x > 0 for x in baseline + candidate), "samples must be finite and positive")
    base_median = statistics.median(baseline)
    candidate_median = statistics.median(candidate)
    improvement = lambda a, b: 100 * (1 - statistics.median(b) / statistics.median(a))
    rng = random.Random(seed)
    boot = []
    for _ in range(iterations):
        indexes = [rng.randrange(10) for _ in range(10)]
        boot.append(improvement([baseline[i] for i in indexes], [candidate[i] for i in indexes]))
    return {"samplesPerMode": 10, "baselineMedianMs": base_median, "candidateMedianMs": candidate_median,
            "medianSavedMs": base_median - candidate_median, "improvementPercent": improvement(baseline, candidate),
            "improvementCi95Percent": [percentile(boot, .025), percentile(boot, .975)],
            "baselineP95Ms": percentile(baseline, .95), "candidateP95Ms": percentile(candidate, .95),
            "p95RegressionPercent": 100 * (percentile(candidate, .95) / percentile(baseline, .95) - 1)}


def analyze(schedule, manifest_paths, iterations=10000):
    validate_schedule(schedule)
    expected = {run["runId"]: run for run in schedule["runs"]}
    records, failures, sessions = {}, [], set()
    for path in sorted(map(Path, manifest_paths)):
        run_id = None
        try:
            manifest = read_json(path)
            run_id = manifest.get("runId")
            require(run_id in expected, "unexpected run ID")
            require(run_id not in records, "duplicate run manifest")
            # Reserve the run even on failure so a later duplicate cannot replace it.
            records[run_id] = {"path": str(path), "valid": False}
            require(manifest.get("sessionId") not in sessions, "session reused across processes")
            sessions.add(manifest.get("sessionId"))
            event_path = manifest.get("eventsFile")
            require(isinstance(event_path, str) and event_path, "missing eventsFile")
            events = [parse_json(line) for line in (path.parent / event_path).read_text(encoding="utf-8-sig").splitlines() if line.strip()]
            samples = validate_process(manifest, events, expected[run_id])
            records[run_id].update(valid=True, samples=samples, context=manifest["context"])
        except (ValueError, OSError, KeyError, TypeError, AttributeError) as error:
            failures.append({"path": str(path), "runId": run_id, "error": str(error)})
            if isinstance(run_id, str) and run_id in records:
                records[run_id]["valid"] = False
    cells = []
    for cell in schedule["cells"]:
        cell_runs = [run for run in schedule["runs"] if run["cellId"] == cell["cellId"]]
        problems, common_context, per_mode_context, observer = [], None, {}, None
        stacks_by_mode, normalized_stacks, primary_stack = {}, {}, None
        mode_samples = {mode: [] for mode in cell["modes"]}
        for run in sorted(cell_runs, key=lambda x: (x["block"], x["mode"])):
            record = records.get(run["runId"])
            if not record or not record["valid"]:
                problems.append(f"missing/invalid process: {run['runId']}")
                continue
            context = record["context"]
            stack = record["samples"]["activePackIds"]
            mode = run["mode"]
            if mode in stacks_by_mode and stacks_by_mode[mode] != stack:
                problems.append(f"active pack stack changed across processes: {run['runId']}")
            stacks_by_mode[mode] = stack
            optimizer = "packforge" if mode in ("current", "corrected", "candidate") else "quickpack" if mode == "quickpack" else None
            permitted = OPTIMIZER_PACK_IDS.get(optimizer, set())
            primary_ids = [p["id"] if p["id"].startswith("file/") else "file/" + p["id"] for p in context["orderedPacks"]]
            primary_position = min(stack.index(p) for p in primary_ids)
            if any(stack.index(p) >= primary_position for p in stack if p in permitted):
                problems.append(f"optimizer built-in pack overrides primary precedence: {run['runId']}")
            normalized_stacks[mode] = [p for p in stack if p not in permitted]
            if optimizer == "packforge":
                if primary_stack is not None and primary_stack != stack:
                    problems.append(f"PackForge comparison changes built-in resource stack: {run['runId']}")
                primary_stack = stack
            common = canonical({k: v for k, v in context.items() if k not in ("artifacts", "configs")})
            own = canonical({k: context[k] for k in ("artifacts", "configs")})
            observer_hash = next(a["sha256"] for a in context["artifacts"] if a["id"] == "observer")
            if common_context is not None and common_context != common:
                problems.append(f"mixed comparison context/pack hashes: {run['runId']}")
            if run["mode"] in per_mode_context and per_mode_context[run["mode"]] != own:
                problems.append(f"mixed mode artifact/config hashes: {run['runId']}")
            if observer is not None and observer_hash != observer:
                problems.append(f"mixed observer binaries: {run['runId']}")
            common_context, observer = common, observer_hash
            per_mode_context[run["mode"]] = own
            mode_samples[run["mode"]].append({"block": run["block"], **record["samples"]})
        if len({canonical(stack) for stack in normalized_stacks.values()}) > 1:
            problems.append("cross-mode active pack stack differs beyond known optimizer built-in IDs; loader resources and primary precedence are not pinned")
        vanilla_stack = stacks_by_mode.get("vanilla", [])
        stack_differences = {mode: {"added": [p for p in stack if p not in vanilla_stack],
                                   "removed": [p for p in vanilla_stack if p not in stack],
                                   "orderedStackDiffers": stack != vanilla_stack}
                             for mode, stack in stacks_by_mode.items() if mode != "vanilla"}
        mode_statistics = {}
        for mode, samples in mode_samples.items():
            count = cell["processesPerMode"][mode]
            mode_statistics[mode] = {"validProcessCount": len(samples), "expectedProcessCount": count,
                                     "role": "reference" if mode in REFERENCE_MODES else "primary",
                                     "complete": len(samples) == count}
            if samples:
                for metric in ("startupMs", "reloadMs", "reloadCompleteMs"):
                    values = [x[metric] for x in samples]
                    mode_statistics[mode][metric] = {"medianMs": statistics.median(values)}
                    if mode not in REFERENCE_MODES:
                        mode_statistics[mode][metric]["p95Ms"] = percentile(values, .95)
        result = {"cellId": cell["cellId"], "workload": cell["workload"], "problems": problems,
                  "processSamples": mode_samples, "modeStatistics": mode_statistics,
                  "activePackComparison": {"stacksByMode": stacks_by_mode, "normalizedStacksByMode": normalized_stacks,
                                           "differencesFromVanilla": stack_differences},
                  "comparisons": {}, "passed": False}
        if not problems:
            for metric in ("startupMs", "reloadMs"):
                base = [x[metric] for x in mode_samples["current"]]
                candidate = [x[metric] for x in mode_samples["candidate"]]
                salt = int.from_bytes(hashlib.sha256(f"{cell['cellId']}:{metric}".encode()).digest()[:8], "big")
                stats = comparison(base, candidate, schedule["seed"] ^ salt, iterations)
                stats["passed"] = (stats["improvementPercent"] >= (15 if cell["workload"] == "developrp" else -5)
                                   and stats["p95RegressionPercent"] <= 5
                                   and (cell["workload"] != "developrp" or stats["improvementCi95Percent"][0] > 0))
                result["comparisons"][metric] = stats
            result["passed"] = all(x["passed"] for x in result["comparisons"].values())
        cells.append(result)
    return {"schema": 1, "seed": schedule["seed"], "bootstrapIterations": iterations,
            "primaryBlocks": schedule["primaryBlocks"], "referenceBlocks": schedule["referenceBlocks"],
            "filesystemCache": "uncontrolled; fresh process does not imply cold filesystem cache",
            "expectedProcesses": len(expected), "receivedManifestCount": len(manifest_paths),
            "failures": failures, "cells": cells, "passed": not failures and all(c["passed"] for c in cells)}


def markdown(report):
    lines = ["# PackForge benchmark admission", "", f"**{'PASS' if report['passed'] else 'NOT ADMITTED'}**",
             "", report["filesystemCache"], "", "| Cell | Startup improvement | Reload improvement | Result |",
             "| --- | ---: | ---: | --- |"]
    for cell in report["cells"]:
        values = []
        for metric in ("startupMs", "reloadMs"):
            stat = cell["comparisons"].get(metric)
            values.append(f"{stat['improvementPercent']:.2f}% (95% CI {stat['improvementCi95Percent'][0]:.2f}–{stat['improvementCi95Percent'][1]:.2f}%)" if stat else "incomplete")
        lines.append(f"| {cell['cellId']} | {' | '.join(values)} | {'PASS' if cell['passed'] else 'FAIL'} |")
    lines.extend(["", "Admission compares ten paired current/candidate process-level samples; each reload sample is the median of five warm reloads. No failures or outliers are discarded.",
                  "", f"Reference modes use {report['referenceBlocks']} processes per cell. Their medians are descriptive only; no reference p95 or statistical-significance claim is made.",
                  "", "| Cell | Reference mode | Valid / required processes | Startup median (ms) | Reload median (ms) |",
                  "| --- | --- | ---: | ---: | ---: |"])
    for cell in report["cells"]:
        for mode, stats in cell["modeStatistics"].items():
            if stats["role"] == "reference":
                startup = f"{stats['startupMs']['medianMs']:.2f}" if "startupMs" in stats else "missing"
                reload = f"{stats['reloadMs']['medianMs']:.2f}" if "reloadMs" in stats else "missing"
                lines.append(f"| {cell['cellId']} | {mode} | {stats['validProcessCount']} / {stats['expectedProcessCount']} | {startup} | {reload} |")
    lines.append("")
    for cell in report["cells"]:
        for mode, difference in cell["activePackComparison"]["differencesFromVanilla"].items():
            if difference["orderedStackDiffers"]:
                lines.append(f"- {cell['cellId']} {mode} active-pack difference from vanilla: added {difference['added']}; removed {difference['removed']}. Full ordered stacks are retained in JSON.")
    for failure in report["failures"]:
        lines.append(f"- {failure['path']}: {failure['error']}")
    for cell in report["cells"]:
        if cell["problems"]:
            lines.append(f"- {cell['cellId']}: " + "; ".join(cell["problems"]))
    return "\n".join(lines) + "\n"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    schedule = sub.add_parser("schedule")
    schedule.add_argument("--seed", type=int, default=SEED)
    schedule.add_argument("--quickpack-cell", action="append", default=[])
    schedule.add_argument("--companion-workload", action="append", default=[])
    schedule.add_argument("--reference-blocks", type=int, default=3, help="fresh vanilla/Quick Pack processes per cell (1..10, default 3)")
    schedule.add_argument("--output", type=Path, required=True)
    analysis = sub.add_parser("analyze")
    analysis.add_argument("--schedule", type=Path, required=True)
    analysis.add_argument("--manifests", type=Path, required=True)
    analysis.add_argument("--output", type=Path, required=True)
    analysis.add_argument("--markdown", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "schedule":
            value = make_schedule(args.seed, args.quickpack_cell, args.companion_workload, args.reference_blocks)
        else:
            paths = list(args.manifests.glob("*.json"))
            value = analyze(read_json(args.schedule), paths)
            args.markdown.write_text(markdown(value), encoding="utf-8")
        args.output.write_text(json.dumps(value, indent=2, allow_nan=False) + "\n", encoding="utf-8")
        return 0 if args.command == "schedule" or value["passed"] else 1
    except (ValueError, OSError, KeyError, TypeError, AttributeError) as error:
        print(f"Invalid benchmark input: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
