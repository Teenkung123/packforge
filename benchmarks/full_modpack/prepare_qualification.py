"""Materialize the reviewed, fully bound FO 112-process qualification matrix.

This is a preparation tool only.  It reads existing small manifests and hashes
named files; it never opens archives, launches a client, provisions downloads,
or edits the frozen profile.  The output is intentionally private.
"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT = Path(__file__).parents[2].resolve()
PERF = ROOT / ".gradle/performance-rework"
DEST = PERF / "fo-qualification-2026-09-12"
FROZEN = PERF / "fo-frozen-profile/manifest.json"
SEED_INPUT = PERF / "fo-prepared-v2/preflight-candidate-world-03.json"
SCENE_ROOT = PERF / "observer-v3-scene-fixed"
STARTING = PERF / "fo-starting-build/packforge-fabric-1.4-mc26.1-26.2.jar"
CANDIDATE = ROOT / "build/libs/packforge-fabric-1.4-mc26.1-26.2.jar"
QUICKPACK = PERF / "fo-frozen-profile/inputs/mods/quick-pack-fabric-1.5.0+26.1.2.jar.disabled"
CONTROL = PERF / "fo-prepared-v2/configuration-control.json"
CANDIDATE_CONFIG = PERF / "fo-candidate-01/packforge-recommended.json"
MATCHED_QP_CONFIG = PERF / "fo-matched-config/quick-pack.json"


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def binding(path):
    return {"path": str(path), "sha256": sha(path)}


def item(identifier, path, filename=None):
    result = {"id": identifier, "path": str(path), "sha256": sha(path)}
    if filename:
        result["filename"] = filename
    return result


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def main():
    if DEST.exists():
        raise ValueError("qualification destination already exists; refusing overwrite")
    frozen = read(FROZEN)
    seed = read(SEED_INPUT)
    frozen_sha = sha(FROZEN)
    by_id = {entry["id"]: entry for entry in frozen["files"]}
    for required in ("options.txt", "config/packforge.json", "config/quick-pack.json"):
        if required not in by_id:
            raise ValueError("frozen manifest missing " + required)
    DEST.mkdir(parents=True)
    (DEST / "specs").mkdir()
    (DEST / "reviewed").mkdir()

    companions = [item("fo/" + Path(e["id"]).name, Path(e["path"]), Path(e["id"]).name)
                  for e in frozen["files"] if e["id"].startswith("mods/") and e["id"].endswith(".jar")
                  and e["id"] not in frozen["optimizerFiles"].values()]
    observer_path = SCENE_ROOT / "benchmark-observer-fabric-26.1.2.jar"
    observer = item("observer", observer_path, observer_path.name)
    resources = [item(e["id"][len("resourcepacks/"):], Path(e["path"])) for e in frozen["files"] if e["id"].startswith("resourcepacks/")]
    shaders = [item(e["id"][len("shaderpacks/"):], Path(e["path"])) for e in frozen["files"] if e["id"].startswith("shaderpacks/")]
    configs = [item(e["id"][len("config/"):], Path(e["path"])) for e in frozen["files"] if e["id"].startswith("config/")]
    options = binding(Path(seed["optionsSnapshot"]["path"]))
    ordered = frozen["orderedSelectedPackIds"]
    base = {key: copy.deepcopy(seed[key]) for key in (
        "minecraft", "loader", "loaderVersion", "framebuffer", "java", "jvmOptions",
        "versionJson", "clientJar", "librariesRoot", "assetsRoot", "nativesRoot", "loggingConfig",
        "context", "normalizationPolicy")}
    # The current display proof supersedes the earlier 2736x1824 exploratory
    # run.  Keep the old artifact untouched and bind every new process to the
    # observed 3440x1440 fullscreen dimensions.
    base["framebuffer"] = {"width": 3440, "height": 1440}
    base["context"]["framebufferEvidence"] = {
        "kind": "observer-ready", "path": str(PERF / "runs/fo-world-current-2026-09-12-01/events.jsonl"),
        "sha256": "0fd94ffd156ca4f2995f588874d4f0091e5747b3bc8412ff4d594ba1266d135c",
        "sessionId": "1a5ede85-80fd-4043-a535-8b057bbfa519", "framebufferWidth": 3440, "framebufferHeight": 1440}
    base.update({"benchmarkSuite": "full-modpack", "workload": "fabulously_optimized",
                 "frozenProfileSha256": frozen_sha, "orderedPackIds": ordered,
                 "resourcePacks": resources, "shaderPacks": shaders, "optionsSnapshot": options,
                 "reloadCount": 6, "timeoutSeconds": 900, "profilingEnabled": False,
                 "resourceHashingEnabled": False, "flightRecording": False, "discoveryOnly": False,
                 "jvmInitialHeap": "default",
                 "pack": next(p for p in resources if p["id"] == "DevelopRP-Full-1.9.5.zip"),
                 "qualityContract": {"matched": False, "reason": "Runtime GPU/render/fade equivalence remains pending; qualification must not infer a pass."}})
    base["jvmOptions"] = ["-Xmx6144M"]
    base["mods"] = companions + [observer]
    base["context"] = {key: copy.deepcopy(seed["context"][key]) for key in ("hardware", "framebufferEvidence", "launcherRuntimeEvidence")}
    base["context"]["framebufferEvidence"] = {
        "kind": "observer-ready", "path": str(PERF / "runs/fo-world-current-2026-09-12-01/events.jsonl"),
        "sha256": "0fd94ffd156ca4f2995f588874d4f0091e5747b3bc8412ff4d594ba1266d135c",
        "sessionId": "1a5ede85-80fd-4043-a535-8b057bbfa519", "framebufferWidth": 3440, "framebufferHeight": 1440}
    base["generatedScene"] = seed["generatedScene"]

    frozen_binding = binding(FROZEN)
    templates = {}
    for scenario in ("menu", "inworld"):
        for mode in ("vanilla", "saved", "configuration", "candidate", "quickpack", "combined", "quickpack_default"):
            spec = copy.deepcopy(base)
            spec.update({"scenario": scenario, "mode": mode,
                         "runId": f"fo-qualification-{scenario}-{mode}-template",
                         "cellId": f"26.1.2-fabric-fabulously_optimized-{scenario}", "block": 0})
            if scenario == "menu":
                spec.pop("generatedScene")
            selected_mods = copy.deepcopy(companions) + [copy.deepcopy(observer)]
            if mode in ("saved", "configuration"):
                selected_mods.append(item("packforge", STARTING, STARTING.name))
            elif mode == "candidate":
                selected_mods.append(item("packforge", CANDIDATE, CANDIDATE.name))
            elif mode in ("quickpack", "quickpack_default"):
                selected_mods.append(item("quickpack", QUICKPACK, QUICKPACK.name))
            elif mode == "combined":
                selected_mods += [item("packforge", CANDIDATE, CANDIDATE.name), item("quickpack", QUICKPACK, QUICKPACK.name)]
            spec["mods"] = selected_mods
            spec["configs"] = copy.deepcopy(configs)
            deviations = []
            deviations.append({"id": "options.txt", "sourceSha256": by_id["options.txt"]["sha256"],
                               "resultSha256": options["sha256"], "kind": "benchmark-options",
                               "reason": "Reviewed benchmark throttle settings pauseOnLostFocus=false and inactivityFpsLimit=minimized."})
            if mode == "configuration":
                replacement = item("packforge.json", CONTROL)
                spec["configs"] = [replacement if x["id"] == "packforge.json" else x for x in spec["configs"]]
                deviations.append({"id": "config/packforge.json", "sourceSha256": by_id["config/packforge.json"]["sha256"], "resultSha256": replacement["sha256"], "kind": "packforge-settings", "reason": "Reviewed configuration-only control from saved config; no hidden settings."})
            if mode in ("candidate", "combined"):
                replacement = item("packforge.json", CANDIDATE_CONFIG)
                spec["configs"] = [replacement if x["id"] == "packforge.json" else x for x in spec["configs"]]
                deviations.append({"id": "config/packforge.json", "sourceSha256": by_id["config/packforge.json"]["sha256"], "resultSha256": replacement["sha256"], "kind": "packforge-settings", "reason": "Reviewed production recommended settings; key set checked against current config model."})
            if mode in ("quickpack", "combined"):
                replacement = item("quick-pack.json", MATCHED_QP_CONFIG)
                spec["configs"] = [replacement if x["id"] == "quick-pack.json" else x for x in spec["configs"]]
                deviations.append({"id": "config/quick-pack.json", "sourceSha256": by_id["config/quick-pack.json"]["sha256"], "resultSha256": replacement["sha256"], "kind": "quickpack-fade", "reason": "User-approved one-field equal-fade benchmark copy; default reference retains original true."})
            reviewed = {"schema": 1, "suite": "full-modpack", "sourceFrozenProfileSha256": frozen_binding["sha256"],
                        "mode": mode, "scenario": scenario, "workload": "fabulously_optimized",
                        "mods": [dict(x) for x in spec["mods"]], "resourcePacks": [dict(x) for x in resources],
                        "shaderPacks": [dict(x) for x in shaders], "configs": [dict(x) for x in spec["configs"]],
                        "optionsSnapshot": dict(options), "deviations": deviations}
            reviewed_path = DEST / "reviewed" / f"{scenario}-{mode}.json"
            write_json(reviewed_path, reviewed)
            spec["frozenProfileManifest"] = frozen_binding
            spec["reviewedInputManifest"] = binding(reviewed_path)
            templates[(scenario, mode)] = spec
            write_json(DEST / "specs" / f"{scenario}-{mode}-template.json", spec)
    schedule = read(Path(__file__).with_name("analysis.py")) if False else None
    # Importing analysis is safe and deterministic; it does not launch or hash assets.
    module_spec = importlib.util.spec_from_file_location("fo_analysis", Path(__file__).with_name("analysis.py"))
    analysis = importlib.util.module_from_spec(module_spec); module_spec.loader.exec_module(analysis)
    schedule = analysis.make_schedule()
    for run in schedule["runs"]:
        spec = copy.deepcopy(templates[(run["scenario"], run["mode"])])
        spec.update({key: run[key] for key in ("runId", "cellId", "mode", "block", "workload")})
        write_json(DEST / "specs" / (run["runId"] + ".json"), spec)
    write_json(DEST / "schedule.json", schedule)
    write_json(DEST / "provenance.json", {"schema": 1, "status": "prepared; runtime pending", "frozenProfile": frozen_binding,
        "candidateJar": item("packforge-fabric-1.4-mc26.1-26.2.jar", CANDIDATE), "candidateJarExpectedSha256": "eb8aa5319420c4cd9ba33113b112fcf872ce2103e15efe625c27e0128781f624",
        "java": base["java"], "jvmOptions": base["jvmOptions"], "framebuffer": base["framebuffer"],
        "scene": seed.get("generatedScene"), "qualityContract": base["qualityContract"],
        "runtimeGates": ["GPU/render/fade equivalence", "112 fresh client processes", "six reload generations each", "observer protocol 3 input acknowledgements"]})
    print(json.dumps({"destination": str(DEST), "scheduleRuns": len(schedule["runs"]), "templates": len(templates), "status": "prepared; no clients launched"}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(type(exc).__name__ + ": " + str(exc), file=sys.stderr)
        raise SystemExit(2)
