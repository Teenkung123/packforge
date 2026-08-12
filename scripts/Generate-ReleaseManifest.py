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
    suffix = str(target["platforms"][loader].get("versionSuffix", ""))
    return f"packforge-{loader}-{version}{suffix}-mc{target['artifactMinecraft']}.jar"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts-dir", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    registry = load_registry()
    properties = load_properties()
    version = properties.get("mod_version")
    if not version:
        raise SystemExit("gradle.properties is missing mod_version")

    cells_by_target: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for cell in registry["releaseCells"]:
        cells_by_target[str(cell["targetKey"])].append(cell)

    artifacts: list[dict[str, Any]] = []
    for target in published_targets(registry):
        target_key = str(target["key"])
        cells = cells_by_target[target_key]
        for loader in target["loaderAvailability"]:
            filename = artifact_name(version, target, str(loader))
            entry: dict[str, Any] = {
                "filename": filename,
                "loader": str(loader),
                "target": target_key,
                "minecraft": str(target["artifactMinecraft"]),
                "gameVersions": [str(cell["id"]) for cell in cells],
                "maturity": str(target["maturity"]),
                "releaseType": "release" if str(target["maturity"]) == "stable" else "beta",
            }
            if args.artifacts_dir:
                path = args.artifacts_dir / filename
                if not path.is_file():
                    raise SystemExit(f"missing publishable artifact: {path}")
                data = path.read_bytes()
                entry["size"] = len(data)
                entry["sha256"] = hashlib.sha256(data).hexdigest()
                entry["sha512"] = hashlib.sha512(data).hexdigest()
            artifacts.append(entry)

    if args.artifacts_dir:
        actual = sorted(path.name for path in args.artifacts_dir.glob("packforge-*.jar"))
        expected = sorted(entry["filename"] for entry in artifacts)
        if actual != expected:
            raise SystemExit(f"publish directory contains stale or missing artifacts: expected={expected} actual={actual}")

    manifest = {
        "schemaVersion": 1,
        "modVersion": version,
        "registrySchemaVersion": registry["schemaVersion"],
        "publishedArtifactCount": len(artifacts),
        "artifacts": artifacts,
    }
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

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
    (args.output_dir / "release-table.md").write_text("\n".join(table) + "\n", encoding="utf-8")
    print(f"Generated release manifest: artifacts={len(artifacts)} output={args.output_dir}")


if __name__ == "__main__":
    main()
