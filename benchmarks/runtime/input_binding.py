"""Bind a full-modpack run to frozen and explicitly reviewed input manifests.

This module only reads the small JSON manifests and hashes the files named by
their bindings.  It never traverses profiles or opens archive payloads.
"""
import hashlib
import json
from pathlib import Path
import re

SHA256 = re.compile(r"[0-9a-f]{64}\Z")
MODES = {"vanilla", "saved", "configuration", "candidate", "quickpack", "quickpack_default", "combined"}
OPTIMIZERS = {"packforge", "quickpack"}
DEVIATIONS = {
    "config/packforge.json": "packforge-settings",
    "config/quick-pack.json": "quickpack-fade",
    "config/dynamic_fps.json": "benchmark-throttle",
    "options.txt": "benchmark-options",
    "config/iris.properties": "runtime-metadata",
    "shaderpacks/ComplementaryUnbound_r5.7.1.zip.txt": "runtime-metadata",
    "config/sodium-fingerprint.json": "runtime-metadata",
    "config/sodium-options.json": "runtime-metadata",
}


def _fail(message):
    raise ValueError(message)


def _hash(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def _json_bytes(raw):
    try:
        def pairs(items):
            result = {}
            for key, value in items:
                if key in result:
                    _fail("Duplicate JSON key in input manifest: " + key)
                result[key] = value
            return result
        return json.loads(raw.decode("utf-8-sig"), object_pairs_hook=pairs)
    except (UnicodeError, json.JSONDecodeError) as exc:
        _fail("Cannot read input manifest JSON: " + str(exc))


def _path(value, label):
    if (not isinstance(value, str) or not value or Path(value).is_absolute() or "\\" in value
            or ":" in value):
        _fail("Invalid relative " + label + " path")
    parts = value.split("/")
    if any(part in ("", ".", "..") for part in parts):
        _fail("Invalid relative " + label + " path")
    return value


def _sha(value, label):
    if not isinstance(value, str) or not SHA256.fullmatch(value):
        _fail("Invalid SHA-256 for " + label)
    return value


def _items(value, label):
    if not isinstance(value, list):
        _fail(label + " must be a list")
    result = []
    ids = set()
    for item in value:
        if not isinstance(item, dict) or not {"id", "path", "sha256"}.issubset(item) or set(item) - {"id", "path", "sha256", "filename"}:
            _fail("Invalid " + label + " item")
        ident = _path(item["id"], label + " ID")
        if ident in ids:
            _fail("Duplicate " + label + " ID: " + ident)
        ids.add(ident)
        if "filename" in item and (not isinstance(item["filename"], str) or not item["filename"]
                                    or Path(item["filename"]).name != item["filename"] or "\\" in item["filename"]):
            _fail("Invalid " + label + " filename")
        result.append((ident, _sha(item["sha256"], ident)))
    filenames = [item.get("filename") for item in value if "filename" in item]
    if len(filenames) != len(set(filenames)):
        _fail("Duplicate " + label + " filename")
    return result


def _binding(value, label):
    if not isinstance(value, dict) or set(value) != {"path", "sha256"}:
        _fail(label + " must be a {path, sha256} binding")
    path = value["path"]
    digest = _sha(value["sha256"], label)
    try:
        raw = Path(path).read_bytes()
    except OSError as exc:
        _fail(label + " cannot be read: " + str(exc))
    actual = hashlib.sha256(raw).hexdigest()
    if actual != digest:
        _fail(label + " checksum mismatch")
    manifest = _json_bytes(raw)
    if not isinstance(manifest, dict):
        _fail(label + " must contain a JSON object")
    return manifest


def _frozen_files(manifest):
    files = manifest.get("files")
    if not isinstance(files, list):
        _fail("Frozen manifest files must be a list")
    result = {}
    for item in files:
        if not isinstance(item, dict) or set(item) != {"id", "path", "sha256", "bytes"}:
            _fail("Invalid frozen file entry")
        ident = _path(item["id"], "frozen file")
        if ident in result:
            _fail("Duplicate frozen file ID: " + ident)
        if type(item["bytes"]) is not int or item["bytes"] < 0:
            _fail("Invalid byte count for frozen file: " + ident)
        result[ident] = _sha(item["sha256"], ident)
    return result


def _check_deviations(reviewed, frozen, spec_hashes):
    deviations = reviewed.get("deviations", [])
    if not isinstance(deviations, list):
        _fail("Reviewed deviations must be a list")
    seen = set()
    for item in deviations:
        if not isinstance(item, dict) or set(item) != {"id", "sourceSha256", "resultSha256", "kind", "reason"}:
            _fail("Invalid reviewed deviation")
        ident = _path(item["id"], "deviation")
        if ident in seen or ident not in DEVIATIONS or item["kind"] != DEVIATIONS[ident]:
            _fail("Unapproved reviewed deviation: " + ident)
        if not isinstance(item["reason"], str) or not item["reason"].strip():
            _fail("Reviewed deviation requires a reason")
        source = _sha(item["sourceSha256"], ident + " source")
        result = _sha(item["resultSha256"], ident + " result")
        if frozen.get(ident) != source or spec_hashes.get(ident) != result:
            _fail("Reviewed deviation hash mismatch: " + ident)
        seen.add(ident)
    return seen


def validate(spec):
    """Return validated manifest bindings, or ``None`` for pilot specs."""
    if not isinstance(spec, dict):
        _fail("Run spec must be an object")
    present = [key for key in ("frozenProfileManifest", "reviewedInputManifest") if key in spec]
    if not present:
        return None
    if len(present) != 2:
        _fail("Frozen and reviewed input manifests must be supplied together")
    frozen = _binding(spec[present[0]], "Frozen profile manifest")
    reviewed = _binding(spec[present[1]], "Reviewed input manifest")
    if type(frozen.get("schema")) is not int or frozen.get("schema") != 1 or frozen.get("suite") != "full-modpack" or frozen.get("private") is not True:
        _fail("Invalid private full-modpack frozen manifest")
    if type(reviewed.get("schema")) is not int or reviewed.get("schema") != 1 or reviewed.get("suite") != "full-modpack":
        _fail("Invalid reviewed full-modpack manifest")
    if reviewed.get("sourceFrozenProfileSha256") != spec["frozenProfileManifest"]["sha256"]:
        _fail("Reviewed source frozen profile checksum mismatch")
    for key in ("mode", "scenario", "workload"):
        if spec.get(key) != reviewed.get(key):
            _fail("Reviewed manifest mismatch: " + key)
    if spec.get("mode") not in MODES:
        _fail("Invalid full-modpack mode")
    frozen_files = _frozen_files(frozen)
    reviewed_ids = {"mods", "resourcePacks", "shaderPacks", "configs"}
    for key in reviewed_ids:
        if key not in reviewed:
            _fail("Reviewed manifest missing " + key)
    spec_mods = _items(spec.get("mods"), "mods")
    spec_resources = _items(spec.get("resourcePacks"), "resourcePacks")
    spec_shaders = _items(spec.get("shaderPacks"), "shaderPacks")
    spec_configs = _items(spec.get("configs"), "configs")
    reviewed_mods = dict(_items(reviewed["mods"], "reviewed mods"))
    reviewed_resources = dict(_items(reviewed["resourcePacks"], "reviewed resourcePacks"))
    reviewed_shaders = dict(_items(reviewed["shaderPacks"], "reviewed shaderPacks"))
    reviewed_configs = dict(_items(reviewed["configs"], "reviewed configs"))
    if dict(spec_mods) != reviewed_mods or dict(spec_resources) != reviewed_resources or dict(spec_shaders) != reviewed_shaders or dict(spec_configs) != reviewed_configs:
        _fail("Spec inputs do not exactly match reviewed inputs")
    options = spec.get("optionsSnapshot")
    if not isinstance(options, dict) or set(options) != {"path", "sha256"}:
        _fail("Invalid optionsSnapshot")
    option_hash = _sha(options["sha256"], "optionsSnapshot")
    reviewed_options = reviewed.get("optionsSnapshot")
    if (not isinstance(reviewed_options, dict) or set(reviewed_options) != {"path", "sha256"}
            or _sha(reviewed_options["sha256"], "reviewed optionsSnapshot") != option_hash):
        _fail("Reviewed optionsSnapshot mismatch")
    spec_hashes = dict(spec_configs)
    spec_hashes = {"config/" + ident if not ident.startswith("config/") else ident: digest
                   for ident, digest in spec_hashes.items()}
    spec_hashes.update({"resourcepacks/" + ident: digest for ident, digest in spec_resources})
    spec_hashes.update({"shaderpacks/" + ident: digest for ident, digest in spec_shaders})
    spec_hashes["options.txt"] = option_hash
    deviations = _check_deviations(reviewed, frozen_files, spec_hashes)
    frozen_configs = {ident[len("config/"):]: digest for ident, digest in frozen_files.items() if ident.startswith("config/")}
    if set(dict(spec_configs)) != set(frozen_configs):
        _fail("Config IDs differ from frozen manifest")
    for ident, digest in spec_configs + [("options.txt", option_hash)]:
        frozen_id = ident if ident == "options.txt" or ident.startswith("config/") else "config/" + ident
        expected = frozen_files.get(frozen_id)
        if expected is None and ident != "options.txt":
            _fail("Input is absent from frozen manifest: " + ident)
        canonical = frozen_id
        if canonical not in deviations and expected != digest:
            _fail("Frozen config input changed: " + ident)
    frozen_resources = {ident[len("resourcepacks/"):]: digest for ident, digest in frozen_files.items() if ident.startswith("resourcepacks/")}
    if set(dict(spec_resources)) != set(frozen_resources):
        _fail("Resource pack IDs differ from frozen manifest")
    frozen_shaders = {ident[len("shaderpacks/"):]: digest for ident, digest in frozen_files.items() if ident.startswith("shaderpacks/")}
    if set(dict(spec_shaders)) != set(frozen_shaders):
        _fail("Shader pack IDs differ from frozen manifest")
    for ident, digest in spec_resources:
        if frozen_files.get("resourcepacks/" + ident) != digest:
            _fail("Resource pack differs from frozen manifest: " + ident)
    for ident, digest in spec_shaders:
        if "shaderpacks/" + ident not in deviations and frozen_files.get("shaderpacks/" + ident) != digest:
            _fail("Shader pack differs from frozen manifest: " + ident)
    optimizer_files = frozen.get("optimizerFiles")
    if not isinstance(optimizer_files, dict) or set(optimizer_files) != OPTIMIZERS:
        _fail("Frozen optimizer file map is invalid")
    companions = {"fo/" + Path(ident).name: digest for ident, digest in frozen_files.items() if ident.startswith("mods/") and ident.endswith(".jar") and ident not in optimizer_files.values()}
    actual_companions = {ident: digest for ident, digest in spec_mods if ident.startswith("fo/")}
    if actual_companions != companions:
        _fail("Frozen companion mods were omitted, added, or changed")
    names = {ident for ident, _ in spec_mods}
    if "observer" not in names:
        _fail("Observer mod is required")
    expected_extra = {"saved": {"packforge"}, "configuration": {"packforge"}, "candidate": {"packforge"}, "quickpack": {"quickpack"}, "quickpack_default": {"quickpack"}, "defaultQP": {"quickpack"}, "combined": OPTIMIZERS}.get(spec["mode"], set())
    if names - set(companions) - {"observer"} != expected_extra:
        _fail("Optimizer mods do not match selected mode")
    return {"frozenProfileManifest": spec["frozenProfileManifest"], "reviewedInputManifest": spec["reviewedInputManifest"]}
