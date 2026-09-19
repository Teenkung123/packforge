#!/usr/bin/env python3
"""Prepare a deterministic, secret-free Modrinth release manifest.

The canonical target registry is the source of truth for artifact names,
version suffixes, game-version coverage, maturity, loader classifiers, and
size ceilings.  This command never contacts Modrinth and never publishes
anything.  ``--check`` is suitable for CI; ``--write-preview`` creates a
reviewable release plan; ``--matrix`` emits a GitHub Actions matrix; and
``--write-payload`` writes the exact JSON metadata for one artifact.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import sys
import zipfile
from typing import Any

try:
    import tomllib
except ModuleNotFoundError:  # pragma: no cover - GitHub's Python is 3.11+
    tomllib = None  # type: ignore[assignment]


PROJECT_ID = "NimDX7iQ"
PROJECT_NAME = "PackForge - Optimized Resource Pack Loading Time"
PROJECT_LICENSE = "MIT"
PROJECT_SOURCE_URL = "https://github.com/Teenkung123/packforge"
PROJECT_ISSUES_URL = f"{PROJECT_SOURCE_URL}/issues"
DEFAULT_CHANGELOG = """PackForge 1.4

- Keeps background font preparation bounded and corrects fallback and ownership decisions.
- Reports clearer resource-loading progress and completion feedback.
- Assigns overlapping stages automatically when Quick Pack or RRLS is present.
- Protects duplicate ZIP entries while preserving the resolved resource behavior.
- Extends the maintenance port across the supported legacy targets.
- Adds the registry-supported Minecraft 26.3 Fabric and NeoForge builds.
"""
FABRIC_DEPENDENCY_PROJECTS = {
    "fabric-api": "P7dR8mSH",
    "modmenu": "mOgUt4GM",
}
INTRINSIC_FABRIC_DEPENDENCIES = {"fabricloader", "minecraft", "java"}
VALID_MATURITIES = {"release", "beta", "alpha"}


def load_registry(path: pathlib.Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as stream:
        registry = json.load(stream)
    if registry.get("schemaVersion") != 1 or not registry.get("targets"):
        raise ValueError(f"unsupported or empty registry: {path}")
    if not isinstance(registry["targets"], list):
        raise ValueError(f"registry targets must be a list: {path}")
    keys: set[str] = set()
    for target in registry["targets"]:
        if not isinstance(target, dict) or not isinstance(target.get("key"), str):
            raise ValueError(f"registry target is missing a string key: {path}")
        if target["key"] in keys:
            raise ValueError(f"duplicate registry target: {target['key']}")
        keys.add(target["key"])
        if not isinstance(target.get("platforms"), dict) or not target["platforms"]:
            raise ValueError(f"target has no platforms: {target['key']}")
    return registry


def _version_tuple(version: str) -> tuple[int, ...]:
    match = re.fullmatch(r"(\d+(?:\.\d+)+)(?:[-+].*)?", version)
    if not match:
        raise ValueError(f"invalid Minecraft version: {version}")
    return tuple(int(part) for part in match.group(1).split("."))


def _compare_versions(left: tuple[int, ...], right: tuple[int, ...]) -> int:
    width = max(len(left), len(right))
    left_padded = left + (0,) * (width - len(left))
    right_padded = right + (0,) * (width - len(right))
    return (left_padded > right_padded) - (left_padded < right_padded)


def _version_in_artifact_range(version: str, artifact_minecraft: str) -> bool:
    if "-" not in artifact_minecraft:
        return version == artifact_minecraft
    lower, upper = artifact_minecraft.split("-", 1)
    actual = _version_tuple(version)
    return (
        _compare_versions(actual, _version_tuple(lower)) >= 0
        and _compare_versions(actual, _version_tuple(upper)) <= 0
    )


def game_versions_for_target(target: dict[str, Any]) -> list[str]:
    """Return registry-declared Modrinth versions for a target.

    Newer registry entries may provide ``gameVersions`` (or the compatible
    ``minecraftVersions`` spelling) to enumerate patch releases in a range.
    Existing entries only declare the artifact range, so their two range
    endpoints are used until the registry supplies a more precise list.
    """

    explicit = None
    for key in ("gameVersions", "minecraftVersions"):
        if key in target:
            explicit = target[key]
            break
    artifact_minecraft = target.get("artifactMinecraft")
    if not isinstance(artifact_minecraft, str) or not artifact_minecraft:
        raise ValueError(f"target has no artifactMinecraft: {target.get('key')}")

    if explicit is not None:
        if not isinstance(explicit, list) or not explicit or not all(
            isinstance(value, str) and value for value in explicit
        ):
            raise ValueError(f"target gameVersions must be a non-empty string list: {target['key']}")
        versions = list(dict.fromkeys(explicit))
    elif "-" in artifact_minecraft:
        lower, upper = artifact_minecraft.split("-", 1)
        start = target.get("minecraftVersion", lower)
        if not isinstance(start, str) or not start:
            start = lower
        versions = list(dict.fromkeys([start, upper]))
    else:
        versions = [artifact_minecraft]

    invalid = [
        version
        for version in versions
        if not _version_in_artifact_range(version, artifact_minecraft)
    ]
    if invalid:
        raise ValueError(
            f"target {target['key']} lists game versions outside {artifact_minecraft}: "
            + ", ".join(invalid)
        )
    for version in versions:
        _version_tuple(version)
    return versions


def _registry_classifiers(
    target: dict[str, Any], loader: str, platform: dict[str, Any]
) -> list[dict[str, Any]]:
    if loader == "fabric":
        classifiers = [
            ("fabricloader", platform.get("loaderDependency")),
            ("minecraft", platform.get("minecraftDependency")),
            ("java", f">={target.get('javaVersion')}" if target.get("javaVersion") else None),
        ]
    elif loader in {"forge", "neoforge"}:
        classifiers = [
            (loader, platform.get("versionRange") or platform.get("version")),
            ("minecraft", platform.get("minecraftVersionRange")),
        ]
    else:
        raise ValueError(f"unsupported loader in registry: {loader}")
    if any(version is None for _, version in classifiers):
        missing = ", ".join(identifier for identifier, version in classifiers if version is None)
        raise ValueError(f"target {target['key']} / {loader} lacks classifier data: {missing}")
    return [
        {"id": identifier, "version": str(version), "required": True}
        for identifier, version in classifiers
    ]


def _version_type(maturity: Any, target_key: str, loader: str) -> str:
    value = "beta" if maturity is None else str(maturity)
    if value == "stable":
        return "release"
    if value not in VALID_MATURITIES:
        raise ValueError(f"unsupported maturity {value!r} for {target_key}/{loader}")
    return value


def expected_artifacts(registry: dict[str, Any], version: str) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for target in registry["targets"]:
        game_versions = game_versions_for_target(target)
        for loader, platform in target.get("platforms", {}).items():
            if not isinstance(platform, dict):
                raise ValueError(f"platform data must be an object: {target['key']}/{loader}")
            suffix = str(platform.get("versionSuffix", ""))
            release_version = version + suffix
            artifact_minecraft = str(target["artifactMinecraft"])
            name = f"packforge-{loader}-{release_version}-mc{artifact_minecraft}.jar"
            maturity = platform.get("maturity", "beta")
            version_type = _version_type(maturity, target["key"], loader)
            featured = platform.get("featured", maturity == "stable")
            if not isinstance(featured, bool):
                raise ValueError(f"featured must be boolean for {target['key']}/{loader}")
            max_bytes = platform.get("maxArtifactBytes")
            if max_bytes is not None and (
                not isinstance(max_bytes, int) or isinstance(max_bytes, bool) or max_bytes <= 0
            ):
                raise ValueError(f"invalid maxArtifactBytes for {target['key']}/{loader}")
            result.append(
                {
                    "target": target["key"],
                    "loader": loader,
                    "minecraft": artifact_minecraft,
                    "name": name,
                    "release_version": release_version,
                    "version_suffix": suffix,
                    "maturity": str(maturity),
                    "version_type": version_type,
                    "featured": featured,
                    "game_versions": game_versions,
                    "max_artifact_bytes": max_bytes,
                    "required_loader_classifiers": _registry_classifiers(target, loader, platform),
                    "dependencies": [],
                    "license": PROJECT_LICENSE,
                }
            )
    names = [item["name"] for item in result]
    if len(names) != len(set(names)):
        raise ValueError("registry produces duplicate release artifact names")
    return result


def _hashes(path: pathlib.Path) -> dict[str, str]:
    sha1 = hashlib.sha1()
    sha512 = hashlib.sha512()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            sha1.update(chunk)
            sha512.update(chunk)
    return {"sha1": sha1.hexdigest(), "sha512": sha512.hexdigest()}


def _fabric_metadata(
    metadata: dict[str, Any], item: dict[str, Any]
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], str]:
    depends = metadata.get("depends", {})
    suggests = metadata.get("suggests", {})
    if not isinstance(depends, dict) or not isinstance(suggests, dict):
        raise ValueError("Fabric depends and suggests must be objects")
    classifiers = [
        {"id": expected["id"], "version": str(depends[expected["id"]]), "required": True}
        for expected in item["required_loader_classifiers"]
        if expected["id"] in depends
    ]
    expected_ids = {entry["id"] for entry in item["required_loader_classifiers"]}
    actual_ids = {entry["id"] for entry in classifiers}
    if actual_ids != expected_ids:
        raise ValueError(
            "required Fabric classifiers differ: expected "
            + ", ".join(sorted(expected_ids))
            + "; found "
            + ", ".join(sorted(actual_ids))
        )
    for expected in item["required_loader_classifiers"]:
        actual = next(entry for entry in classifiers if entry["id"] == expected["id"])
        if actual["version"] != expected["version"]:
            raise ValueError(
                f"Fabric classifier {expected['id']} is {actual['version']!r}; "
                f"registry requires {expected['version']!r}"
            )

    dependencies: list[dict[str, Any]] = []
    for dependency_type, values in (("required", depends), ("optional", suggests)):
        unknown = set(values) - INTRINSIC_FABRIC_DEPENDENCIES - set(FABRIC_DEPENDENCY_PROJECTS)
        if unknown:
            raise ValueError(
                f"unmapped {dependency_type} Fabric dependencies: "
                + ", ".join(sorted(unknown))
            )
        for identifier in sorted(set(values) & set(FABRIC_DEPENDENCY_PROJECTS)):
            dependencies.append(
                {
                    "project_id": FABRIC_DEPENDENCY_PROJECTS[identifier],
                    "dependency_type": dependency_type,
                }
            )
    license_value = metadata.get("license")
    if isinstance(license_value, list):
        license_value = ", ".join(str(value) for value in license_value)
    if not isinstance(license_value, str) or license_value != PROJECT_LICENSE:
        raise ValueError(f"artifact license must be {PROJECT_LICENSE}")
    contact = metadata.get("contact")
    if not isinstance(contact, dict) or contact.get("homepage") != PROJECT_SOURCE_URL or contact.get("sources") != PROJECT_SOURCE_URL:
        raise ValueError(
            f"artifact source URL must be {PROJECT_SOURCE_URL} "
            f"(found homepage={contact.get('homepage') if isinstance(contact, dict) else None}, "
            f"sources={contact.get('sources') if isinstance(contact, dict) else None})"
        )
    return classifiers, dependencies, license_value


def _loader_toml_metadata(
    text: str, item: dict[str, Any]
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], str]:
    if tomllib is None:  # pragma: no cover - all supported runtimes have tomllib
        raise ValueError("Python 3.11 or newer is required to inspect Forge metadata")
    parsed = tomllib.loads(text)
    license_value = parsed.get("license")
    if license_value != PROJECT_LICENSE:
        raise ValueError(f"artifact license must be {PROJECT_LICENSE}")
    mods = parsed.get("mods", [])
    if isinstance(mods, dict):
        mods = [mods]
    display_url = mods[0].get("displayURL") if mods and isinstance(mods[0], dict) else None
    if display_url != PROJECT_SOURCE_URL or parsed.get("issueTrackerURL") != PROJECT_ISSUES_URL:
        raise ValueError(
            f"artifact source URL must be {PROJECT_SOURCE_URL} "
            f"(found displayURL={display_url}, issueTrackerURL={parsed.get('issueTrackerURL')})"
        )
    dependencies_root = parsed.get("dependencies", {})
    mod_dependencies = dependencies_root.get("packforge", [])
    if isinstance(mod_dependencies, dict):
        mod_dependencies = [mod_dependencies]
    if not isinstance(mod_dependencies, list):
        raise ValueError("loader dependencies.packforge must be a table list")
    classifiers: list[dict[str, Any]] = []
    for dependency in mod_dependencies:
        if not isinstance(dependency, dict):
            raise ValueError("loader dependency entry must be a table")
        identifier = dependency.get("modId")
        required = dependency.get("mandatory", dependency.get("type") == "required")
        if identifier in {item["loader"], "minecraft"} and required:
            version = dependency.get("versionRange")
            if not isinstance(version, str):
                raise ValueError(f"missing versionRange for required {identifier}")
            classifiers.append({"id": identifier, "version": version, "required": True})
        elif identifier not in {item["loader"], "minecraft"} and required:
            raise ValueError(f"unmapped required loader dependency: {identifier}")
    expected = item["required_loader_classifiers"]
    if classifiers != expected:
        raise ValueError(
            "required loader classifiers differ: expected "
            + json.dumps(expected, sort_keys=True)
            + "; found "
            + json.dumps(classifiers, sort_keys=True)
        )
    return classifiers, [], str(license_value)


def inspect_artifact(path: pathlib.Path, item: dict[str, Any]) -> dict[str, Any]:
    try:
        with zipfile.ZipFile(path) as archive:
            if item["loader"] == "fabric":
                metadata = json.loads(archive.read("fabric.mod.json"))
                classifiers, dependencies, license_value = _fabric_metadata(metadata, item)
            else:
                metadata_name = (
                    "META-INF/neoforge.mods.toml"
                    if item["loader"] == "neoforge"
                    else "META-INF/mods.toml"
                )
                classifiers, dependencies, license_value = _loader_toml_metadata(
                    archive.read(metadata_name).decode("utf-8"), item
                )
    except (KeyError, json.JSONDecodeError, UnicodeDecodeError, zipfile.BadZipFile) as error:
        raise ValueError(f"cannot read {item['loader']} metadata: {error}") from error
    return {
        "license": license_value,
        "dependencies": dependencies,
        "required_loader_classifiers": classifiers,
    }


def _payload(item: dict[str, Any], changelog: str) -> dict[str, Any]:
    return {
        "name": f"{PROJECT_NAME} {item['release_version']}",
        "version_number": item["release_version"],
        "changelog": changelog,
        "dependencies": item.get("dependencies", []),
        "game_versions": item["game_versions"],
        "version_type": item["version_type"],
        "loaders": [item["loader"]],
        "featured": item["featured"],
        "status": "listed",
        "project_id": PROJECT_ID,
        "file_parts": ["artifact"],
        "primary_file": "artifact",
        "environment": "client_only",
    }


def _attach_payload(item: dict[str, Any], changelog: str) -> None:
    item["payload"] = _payload(item, changelog)
    item["file"] = {
        "part": "artifact",
        "filename": item["name"],
        "size": item.get("bytes"),
        "hashes": item.get("hashes"),
        "primary": True,
    }


def inspect(
    registry: dict[str, Any],
    artifacts_dir: pathlib.Path,
    version: str,
    changelog: str = DEFAULT_CHANGELOG,
) -> tuple[list[dict[str, Any]], list[str]]:
    expected = expected_artifacts(registry, version)
    actual = {path.name: path for path in artifacts_dir.glob("packforge-*.jar")}
    errors: list[str] = []
    for item in expected:
        path = actual.get(item["name"])
        item["present"] = path is not None
        item["bytes"] = path.stat().st_size if path else None
        item["hashes"] = _hashes(path) if path else None
        item["size_ok"] = path is not None and (
            item["max_artifact_bytes"] is None
            or item["bytes"] <= item["max_artifact_bytes"]
        )
        item["metadata_ok"] = None
        if path is None:
            errors.append(f"missing artifact: {item['name']}")
        else:
            if not item["size_ok"]:
                errors.append(
                    f"size gate failed: {item['name']} ({item['bytes']} > "
                    f"{item['max_artifact_bytes']} bytes)"
                )
            try:
                metadata = inspect_artifact(path, item)
                item.update(metadata)
                item["metadata_ok"] = True
            except ValueError as error:
                item["metadata_ok"] = False
                errors.append(f"metadata gate failed: {item['name']} ({error})")
        _attach_payload(item, changelog)
        item["payload_ready"] = bool(
            item["present"]
            and item["size_ok"]
            and item["metadata_ok"]
            and item["hashes"]
        )
    unexpected = sorted(set(actual) - {item["name"] for item in expected})
    errors.extend(f"unexpected artifact: {name}" for name in unexpected)
    return expected, errors


def matrix(registry: dict[str, Any], version: str, kind: str) -> dict[str, Any]:
    if kind == "targets":
        return {"include": [{"target": target["key"]} for target in registry["targets"]]}
    if kind != "publish":
        raise ValueError(f"unsupported matrix kind: {kind}")
    items = expected_artifacts(registry, version)
    return {
        "include": [
            {
                "target": item["target"],
                "platform": item["loader"],
                "loader": item["loader"],
                "minecraft": item["minecraft"],
                "game_versions": item["game_versions"],
                "version_suffix": item["version_suffix"],
                "release_type": item["version_type"],
                "featured": item["featured"],
            }
            for item in items
        ]
    }


def write_preview(
    output_dir: pathlib.Path,
    version: str,
    registry: dict[str, Any],
    items: list[dict[str, Any]],
    errors: list[str],
    changelog: str = DEFAULT_CHANGELOG,
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    for item in items:
        if item.get("payload", {}).get("changelog") != changelog:
            _attach_payload(item, changelog)
    manifest = {
        "schemaVersion": 2,
        "project": {
            "id": PROJECT_ID,
            "license": PROJECT_LICENSE,
            "sourceUrl": PROJECT_SOURCE_URL,
            "issuesUrl": PROJECT_ISSUES_URL,
        },
        "releaseVersion": version,
        "artifactCount": len(items),
        "artifacts": items,
        "payloads": [item["payload"] for item in items],
        "validationErrors": errors,
        "publishing": {
            "mode": "manual-approval",
            "workflowTrigger": "workflow_dispatch",
            "confirmationInput": "publish_confirmation=true",
            "tokenRequired": True,
        },
    }
    (output_dir / "modrinth-release-manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    lines = [
        f"# PackForge {version} Modrinth release preview",
        "",
        "Generated from `gradle/minecraft-targets.json` and the local artifact directory.",
        "This is a dry-run preview: it does not contact Modrinth and cannot publish.",
        "",
        f"Project: **{PROJECT_ID}** · License: **{PROJECT_LICENSE}**",
        f"Source: <{PROJECT_SOURCE_URL}> · Issues: <{PROJECT_ISSUES_URL}>",
        f"Expected registry artifacts: **{len(items)}**",
        f"Validation status: **{'READY' if not errors else 'BLOCKED'}**",
        "",
        "| Loader | Minecraft | Version | Maturity | Artifact | Size | SHA-512 | Payload |",
        "|---|---|---|---|---|---:|---|---|",
    ]
    for item in items:
        size = "missing" if item["bytes"] is None else str(item["bytes"])
        if item["max_artifact_bytes"] is not None:
            size += f" / {item['max_artifact_bytes']}"
        if item["bytes"] is not None and not item["size_ok"]:
            size += " FAIL"
        digest = (item["hashes"] or {}).get("sha512", "missing")
        payload_state = "ready" if item["payload_ready"] else "blocked"
        lines.append(
            f"| {item['loader']} | {', '.join(item['game_versions'])} | "
            f"{item['release_version']} | {item['maturity']} | `{item['name']}` | "
            f"{size} | `{digest}` | {payload_state} |"
        )
    lines += [
        "",
        "## Changelog draft",
        "",
        changelog,
        "",
        "## Release gate",
        "",
        "Every listed artifact must exist, match its registry-derived filename, pass its size ceiling, and contain metadata matching the registry's required loader classifiers. The manifest records SHA-1 and SHA-512 for each artifact and the exact version payload that the workflow would submit.",
        "",
        "Publishing remains gated by the `publish_confirmation=true` workflow input.",
    ]
    if errors:
        lines += ["", "## Blocking findings", ""] + [f"- {error}" for error in errors]
    (output_dir / "modrinth-release-plan.md").write_text(
        "\n".join(lines) + "\n", encoding="utf-8"
    )


def _release_version(args: argparse.Namespace) -> str:
    if args.release_version:
        version = args.release_version
    else:
        properties = pathlib.Path("gradle.properties").read_text(encoding="utf-8")
        match = re.search(r"(?m)^mod_version=(.+)$", properties)
        if not match:
            raise ValueError("gradle.properties has no mod_version")
        version = match.group(1).strip()
    if not re.fullmatch(r"\d+(?:\.\d+)+", version):
        raise ValueError(f"invalid release version: {version}")
    return version


def _changelog(args: argparse.Namespace) -> str:
    if args.changelog_file:
        return args.changelog_file.read_text(encoding="utf-8")
    return args.changelog


def _write_single_payload(
    registry: dict[str, Any],
    version: str,
    artifact_path: pathlib.Path,
    output_path: pathlib.Path,
    changelog: str,
) -> None:
    expected = expected_artifacts(registry, version)
    item = next((entry for entry in expected if entry["name"] == artifact_path.name), None)
    if item is None:
        raise ValueError(f"artifact is not registry-derived: {artifact_path.name}")
    if not artifact_path.is_file():
        raise ValueError(f"artifact does not exist: {artifact_path}")
    item["present"] = True
    item["bytes"] = artifact_path.stat().st_size
    item["hashes"] = _hashes(artifact_path)
    item["size_ok"] = item["max_artifact_bytes"] is None or item["bytes"] <= item["max_artifact_bytes"]
    try:
        item.update(inspect_artifact(artifact_path, item))
        item["metadata_ok"] = True
    except ValueError as error:
        raise ValueError(f"metadata gate failed: {artifact_path.name} ({error})") from error
    if not item["size_ok"]:
        raise ValueError(
            f"size gate failed: {artifact_path.name} ({item['bytes']} > "
            f"{item['max_artifact_bytes']} bytes)"
        )
    _attach_payload(item, changelog)
    output_path.write_text(json.dumps(item["payload"], indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--registry", type=pathlib.Path, default=pathlib.Path("gradle/minecraft-targets.json"))
    parser.add_argument("--artifacts", type=pathlib.Path, default=pathlib.Path("build/libs"))
    parser.add_argument("--release-version", default=None)
    parser.add_argument("--write-preview", type=pathlib.Path, default=None)
    parser.add_argument("--write-payload", type=pathlib.Path, default=None)
    parser.add_argument("--artifact", type=pathlib.Path, default=None)
    parser.add_argument("--changelog", default=DEFAULT_CHANGELOG)
    parser.add_argument("--changelog-file", type=pathlib.Path, default=None)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--count-only", action="store_true")
    parser.add_argument("--matrix", choices=("publish", "targets"), default=None)
    args = parser.parse_args()
    registry = load_registry(args.registry)
    version = _release_version(args)
    changelog = _changelog(args)

    if args.matrix:
        print(json.dumps(matrix(registry, version, args.matrix), separators=(",", ":")))
        return 0
    if args.write_payload:
        if args.artifact is None:
            raise ValueError("--write-payload requires --artifact")
        _write_single_payload(registry, version, args.artifact, args.write_payload, changelog)
        return 0

    items, errors = inspect(registry, args.artifacts, version, changelog)
    if args.count_only:
        print(len(items))
        return 0
    if args.write_preview:
        write_preview(args.write_preview, version, registry, items, errors, changelog)
    print(f"registry-derived expected artifacts: {len(items)}")
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    return 1 if args.check and errors else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2)
