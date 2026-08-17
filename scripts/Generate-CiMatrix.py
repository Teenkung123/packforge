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

# Keep the tier definitions registry-key based. This makes the representative
# and expanded jobs explicit, reviewable, and resilient to release-cell range
# changes without inventing a second version list.
REPRESENTATIVE_TARGET_KEYS = (
    "mc1_20_1",
    "mc1_21_1",
    "mc1_21_11",
    "mc26_1_to_26_2",
)
EXPANDED_TARGET_KEYS = (
    "mc1_20_1",
    "mc1_21_1",
    "mc1_21_8",
    "mc1_21_11",
    "mc26_1_to_26_2",
)


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


def artifact_minecraft(target: dict[str, Any], loader: str) -> str:
    return str(target["platforms"][loader].get("artifactMinecraft", target["artifactMinecraft"]))


def _target_keys_for_tier(registry: dict[str, Any], target_keys: tuple[str, ...]) -> list[str]:
    targets = target_map(registry)
    active = list(dict.fromkeys(str(cell["targetKey"]) for cell in registry["releaseCells"]))
    missing = [key for key in target_keys if key not in targets or key not in active]
    if missing:
        raise SystemExit(f"CI tier references missing active registry targets: {', '.join(missing)}")
    return list(target_keys)


def build_matrix(registry: dict[str, Any], target_keys: tuple[str, ...] | None = None) -> dict[str, list[dict[str, Any]]]:
    targets = target_map(registry)
    active_target_keys = (
        list(dict.fromkeys(str(cell["targetKey"]) for cell in registry["releaseCells"]))
        if target_keys is None
        else _target_keys_for_tier(registry, target_keys)
    )
    rows = []
    for target_key in active_target_keys:
        target = targets[target_key]
        rows.append(
            {
                "target": str(target["key"]),
                "java_version": str(target["javaVersion"]),
                "maturity": str(target["maturity"]),
                "source_family": str(target["sourceFamily"]),
            }
        )
    return {"include": rows}


def exact_smoke_matrix(
    registry: dict[str, Any],
    published_only: bool = False,
    target_keys: tuple[str, ...] | None = None,
) -> dict[str, list[dict[str, Any]]]:
    targets = target_map(registry)
    selected_targets = None if target_keys is None else set(_target_keys_for_tier(registry, target_keys))
    rows = []
    for cell in registry["releaseCells"]:
        target_key = str(cell["targetKey"])
        if selected_targets is not None and target_key not in selected_targets:
            continue
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
                "artifact_minecraft": artifact_minecraft(target, loader),
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


def representative_build_matrix(registry: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    return build_matrix(registry, REPRESENTATIVE_TARGET_KEYS)


def expanded_build_matrix(registry: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    return build_matrix(registry, EXPANDED_TARGET_KEYS)


def representative_smoke_matrix(registry: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    return exact_smoke_matrix(registry, target_keys=REPRESENTATIVE_TARGET_KEYS)


def expanded_smoke_matrix(registry: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    return exact_smoke_matrix(registry, target_keys=EXPANDED_TARGET_KEYS)


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
            loader_cells = [cell for cell in cells if loader in cell["loaderAvailability"]]
            if not loader_cells:
                continue
            rows.append(
                {
                    "target": target_key,
                    "loader": str(loader),
                    "minecraft": artifact_minecraft(target, str(loader)),
                    "versions": "\n".join(str(cell["id"]) for cell in loader_cells),
                    "version_suffix": str(platform.get("versionSuffix", "")),
                    "release_type": "release" if str(target["maturity"]) == "stable" else "beta",
                    "featured": "true" if str(target["maturity"]) == "stable" else "false",
                }
            )
    return {"include": rows}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "kind",
        choices=(
            "build",
            "smoke",
            "representative-build",
            "representative-smoke",
            "expanded-build",
            "expanded-smoke",
            "publish-smoke",
            "publish",
        ),
    )
    args = parser.parse_args()
    registry = load_registry()
    if args.kind == "build":
        matrix = build_matrix(registry)
    elif args.kind == "smoke":
        matrix = exact_smoke_matrix(registry)
    elif args.kind == "representative-build":
        matrix = representative_build_matrix(registry)
    elif args.kind == "representative-smoke":
        matrix = representative_smoke_matrix(registry)
    elif args.kind == "expanded-build":
        matrix = expanded_build_matrix(registry)
    elif args.kind == "expanded-smoke":
        matrix = expanded_smoke_matrix(registry)
    elif args.kind == "publish-smoke":
        matrix = exact_smoke_matrix(registry, published_only=True)
    else:
        matrix = publication_matrix(registry)
    print(json.dumps(matrix, separators=(",", ":"), sort_keys=True))


if __name__ == "__main__":
    main()
