#!/usr/bin/env python3
"""Generate and optionally verify the registry-derived publishable artifact manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import defaultdict
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
REGISTRY_PATH = ROOT / "gradle" / "minecraft-targets.json"
PROPERTIES_PATH = ROOT / "gradle.properties"


def load_properties() -> dict[str, str]:
    values: dict[str, str] = {}
    for line in PROPERTIES_PATH.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def load_registry() -> dict[str, Any]:
    registry = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
    if registry.get("schemaVersion") != 2:
        raise SystemExit(f"unsupported target registry schema: {registry.get('schemaVersion')}")
    return registry


def published_targets(registry: dict[str, Any]) -> list[dict[str, Any]]:
    cells_by_target: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for cell in registry["releaseCells"]:
        cells_by_target[str(cell["targetKey"])].append(cell)
    published_keys = {
        target_key
        for target_key, cells in cells_by_target.items()
        if cells and all(str(cell["buildStatus"]) == "existing" for cell in cells)
    }
    return [target for target in registry["targets"] if str(target["key"]) in published_keys]


def artifact_name(version: str, target: dict[str, Any], loader: str) -> str:
    platform = target["platforms"][loader]
    suffix = str(platform.get("versionSuffix", ""))
    artifact_minecraft = str(platform.get("artifactMinecraft", target["artifactMinecraft"]))
    return f"packforge-{loader}-{version}{suffix}-mc{artifact_minecraft}.jar"


def build_manifest(registry: dict[str, Any], version: str, artifacts_dir: Path | None) -> dict[str, Any]:
    cells_by_target: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for cell in registry["releaseCells"]:
        cells_by_target[str(cell["targetKey"])].append(cell)

    artifacts: list[dict[str, Any]] = []
    for target in published_targets(registry):
        target_key = str(target["key"])
        cells = cells_by_target[target_key]
        for loader in target["loaderAvailability"]:
            loader_cells = [cell for cell in cells if loader in cell["loaderAvailability"]]
            if not loader_cells:
                continue
            filename = artifact_name(version, target, str(loader))
            platform = target["platforms"][loader]
            entry: dict[str, Any] = {
                "filename": filename,
                "loader": str(loader),
                "target": target_key,
                "minecraft": str(platform.get("artifactMinecraft", target["artifactMinecraft"])),
                "gameVersions": [str(cell["id"]) for cell in loader_cells],
                "maturity": str(target["maturity"]),
                "releaseType": "release" if str(target["maturity"]) == "stable" else "beta",
            }
            if artifacts_dir:
                path = artifacts_dir / filename
                if not path.is_file():
                    raise SystemExit(f"missing publishable artifact: {path}")
                data = path.read_bytes()
                entry["size"] = len(data)
                entry["sha256"] = hashlib.sha256(data).hexdigest()
                entry["sha512"] = hashlib.sha512(data).hexdigest()
            artifacts.append(entry)

    if artifacts_dir:
        actual = sorted(path.name for path in artifacts_dir.glob("packforge-*.jar"))
        expected = sorted(entry["filename"] for entry in artifacts)
        if actual != expected:
            raise SystemExit(f"publish directory contains stale or missing artifacts: expected={expected} actual={actual}")

    return {
        "schemaVersion": 1,
        "modVersion": version,
        "registrySchemaVersion": registry["schemaVersion"],
        "publishedArtifactCount": len(artifacts),
        "artifacts": artifacts,
    }


def verify_existing_manifest(manifest_path: Path, registry: dict[str, Any], version: str, artifacts_dir: Path) -> None:
    if not manifest_path.is_file():
        raise SystemExit(f"missing release manifest: {manifest_path}")
    try:
        existing = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exception:
        raise SystemExit(f"invalid release manifest: {manifest_path}: {exception}") from exception

    expected = build_manifest(registry, version, artifacts_dir)
    if existing != expected:
        raise SystemExit(
            "release manifest is stale or does not match the registry-derived artifacts: "
            f"{manifest_path}"
        )


def write_manifest(output_dir: Path, manifest: dict[str, Any]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    version = str(manifest["modVersion"])
    artifacts = manifest["artifacts"]

    table = [
        "# PackForge release matrix",
        "",
        f"Generated from `gradle/minecraft-targets.json` for PackForge `{version}`.",
        "",
        "| Loader | Artifact | Minecraft releases | Maturity | SHA-256 |",
        "|---|---|---|---|---|",
    ]
    for entry in artifacts:
        table.append(
            f"| {entry['loader']} | `{entry['filename']}` | "
            f"{', '.join(entry['gameVersions'])} | {entry['maturity']} | "
            f"`{entry.get('sha256', 'not-checked')}` |"
        )
    (output_dir / "release-table.md").write_text("\n".join(table) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts-dir", type=Path)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument(
        "--verify-existing",
        action="store_true",
        help="Verify the existing manifest.json without rewriting it or release-table.md.",
    )
    args = parser.parse_args()

    registry = load_registry()
    properties = load_properties()
    version = properties.get("mod_version")
    if not version:
        raise SystemExit("gradle.properties is missing mod_version")

    if args.verify_existing:
        if args.artifacts_dir is None or args.output_dir is None:
            parser.error("--verify-existing requires --artifacts-dir and --output-dir")
        verify_existing_manifest(args.output_dir / "manifest.json", registry, version, args.artifacts_dir)
        print(f"Verified release manifest: {args.output_dir / 'manifest.json'}")
        return

    if args.output_dir is None:
        parser.error("--output-dir is required unless --verify-existing is used")
    manifest = build_manifest(registry, version, args.artifacts_dir)
    write_manifest(args.output_dir, manifest)
    print(f"Generated release manifest: artifacts={len(manifest['artifacts'])} output={args.output_dir}")


if __name__ == "__main__":
    main()
