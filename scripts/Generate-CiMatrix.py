#!/usr/bin/env python3
"""Emit GitHub Actions matrices from the canonical PackForge target registry."""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
REGISTRY_PATH = ROOT / "gradle" / "minecraft-targets.json"
LOADERS = ("fabric", "forge", "neoforge")


def load_registry() -> dict[str, Any]:
    with REGISTRY_PATH.open(encoding="utf-8") as stream:
        registry = json.load(stream)
    if registry.get("schemaVersion") != 2:
        raise SystemExit(f"unsupported target registry schema: {registry.get('schemaVersion')}")
    return registry


def target_map(registry: dict[str, Any]) -> dict[str, dict[str, Any]]:
    targets = {str(target["key"]): target for target in registry["targets"]}
    if len(targets) != len(registry["targets"]):
        raise SystemExit("target registry contains duplicate target keys")
    return targets


def exact_loader_version(target: dict[str, Any], release: str, loader: str) -> str:
    exact = target.get("requiredExactSmokeLoaderVersions", {}).get(release, {})
    value = exact.get(loader) or target.get("loaderBuildVersions", {}).get(loader)
    if not value:
        raise SystemExit(f"missing {loader} smoke coordinate for {target['key']} / {release}")
    return str(value)


def build_matrix(registry: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    rows = []
    for target in registry["targets"]:
        rows.append(
            {
                "target": str(target["key"]),
                "java_version": str(target["javaVersion"]),
                "maturity": str(target["maturity"]),
                "source_family": str(target["sourceFamily"]),
            }
        )
    return {"include": rows}


def exact_smoke_matrix(registry: dict[str, Any], published_only: bool = False) -> dict[str, list[dict[str, Any]]]:
    targets = target_map(registry)
    rows = []
    for cell in registry["releaseCells"]:
        target_key = str(cell["targetKey"])
        if published_only and str(cell["buildStatus"]) != "existing":
            continue
        target = targets[target_key]
        release = str(cell["id"])
        for loader in cell["loaderAvailability"]:
            loader = str(loader)
            if loader not in target.get("platforms", {}):
                raise SystemExit(f"registry cell {release} declares unavailable loader {loader}")
            row = {
                "target": target_key,
                "platform": loader,
                "release": release,
                "java_version": str(target["javaVersion"]),
                "maturity": str(cell["maturity"]),
                "artifact_minecraft": str(target["artifactMinecraft"]),
                "artifact_smoke": "false" if loader == "forge" else "true",
                "production_harness": "required",
            }
            if loader == "fabric":
                row["minecraft_version"] = release
            elif loader == "forge":
                row["forge_version"] = exact_loader_version(target, release, loader)
            else:
                row["neoforge_version"] = exact_loader_version(target, release, loader)
            rows.append(row)
    return {"include": rows}


def publication_matrix(registry: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    targets = target_map(registry)
    cells_by_target: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for cell in registry["releaseCells"]:
        cells_by_target[str(cell["targetKey"])].append(cell)

    rows = []
    for target_key, cells in cells_by_target.items():
        if not cells or not all(str(cell["buildStatus"]) == "existing" for cell in cells):
            continue
        target = targets[target_key]
        for loader in target["loaderAvailability"]:
            platform = target["platforms"].get(loader)
            if platform is None:
                raise SystemExit(f"published target {target_key} is missing platform metadata for {loader}")
            rows.append(
                {
                    "target": target_key,
                    "loader": str(loader),
                    "minecraft": str(target["artifactMinecraft"]),
                    "versions": "\n".join(str(cell["id"]) for cell in cells),
                    "version_suffix": str(platform.get("versionSuffix", "")),
                    "release_type": "release" if str(target["maturity"]) == "stable" else "beta",
                    "featured": "true" if str(target["maturity"]) == "stable" else "false",
                }
            )
    return {"include": rows}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("build", "smoke", "publish-smoke", "publish"))
    args = parser.parse_args()
    registry = load_registry()
    if args.kind == "build":
        matrix = build_matrix(registry)
    elif args.kind == "smoke":
        matrix = exact_smoke_matrix(registry)
    elif args.kind == "publish-smoke":
        matrix = exact_smoke_matrix(registry, published_only=True)
    else:
        matrix = publication_matrix(registry)
    print(json.dumps(matrix, separators=(",", ":"), sort_keys=True))


if __name__ == "__main__":
    main()
