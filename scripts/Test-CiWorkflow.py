#!/usr/bin/env python3
"""Cheap contract test for registry-derived CI tiers and PR cancellation."""

from __future__ import annotations

import importlib.util
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
MATRIX_SCRIPT = ROOT / "scripts" / "Generate-CiMatrix.py"
BUILD_WORKFLOW_PATH = ROOT / ".github" / "workflows" / "build.yml"
RUNTIME_WORKFLOW_PATH = ROOT / ".github" / "workflows" / "runtime-smoke.yml"


def _load_matrix_module() -> Any:
    spec = importlib.util.spec_from_file_location("generate_ci_matrix", MATRIX_SCRIPT)
    if spec is None or spec.loader is None:
        raise AssertionError(f"unable to load {MATRIX_SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _require_fragments(path: Path, fragments: tuple[str, ...], label: str) -> None:
    workflow = path.read_text(encoding="utf-8")
    missing = [fragment for fragment in fragments if fragment not in workflow]
    if missing:
        raise AssertionError(f"{label} workflow is missing CI tier wiring: {', '.join(missing)}")


def main() -> None:
    module = _load_matrix_module()
    registry = module.load_registry()
    full_build = module.build_matrix(registry)["include"]
    full_smoke = module.exact_smoke_matrix(registry)["include"]
    representative_build = module.representative_build_matrix(registry)["include"]
    representative_smoke = module.representative_smoke_matrix(registry)["include"]
    expanded_build = module.expanded_build_matrix(registry)["include"]
    expanded_smoke = module.expanded_smoke_matrix(registry)["include"]

    if len(full_smoke) != 62:
        raise AssertionError(f"full registry smoke matrix changed unexpectedly: {len(full_smoke)}")
    if not (0 < len(representative_build) < len(expanded_build) <= len(full_build)):
        raise AssertionError(
            "CI build tiers must be representative < expanded <= full: "
            f"{len(representative_build)} < {len(expanded_build)} <= {len(full_build)}"
        )
    if not (0 < len(representative_smoke) < len(expanded_smoke) < len(full_smoke)):
        raise AssertionError(
            "CI smoke tiers must be strictly representative < expanded < full: "
            f"{len(representative_smoke)} < {len(expanded_smoke)} < {len(full_smoke)}"
        )

    target_by_key = {str(target["key"]): target for target in registry["targets"]}
    representative_targets = {row["target"] for row in representative_smoke}
    representative_families = {target_by_key[key]["sourceFamily"] for key in representative_targets}
    representative_loaders = {row["platform"] for row in representative_smoke}
    representative_build_families = {row["source_family"] for row in representative_build}
    required_families = {"mc1_20_1", "mc1_21_0_1", "mc1_21_9_11", "mc26"}
    if not required_families.issubset(representative_families):
        raise AssertionError(
            "representative smoke tier must cover every Java/source family: "
            f"missing={sorted(required_families - representative_families)}"
        )
    if not required_families.issubset(representative_build_families):
        raise AssertionError(
            "representative build tier must cover every Java/source family: "
            f"missing={sorted(required_families - representative_build_families)}"
        )
    if representative_loaders != {"fabric", "forge", "neoforge"}:
        raise AssertionError(f"representative smoke tier loader coverage drifted: {representative_loaders}")

    concurrency_fragments = (
        "github.workflow",
        "github.event.pull_request.number || github.ref",
        "cancel-in-progress: ${{ github.event_name == 'pull_request' }}",
    )
    _require_fragments(
        BUILD_WORKFLOW_PATH,
        (
            "representative-build",
            "matrix_kind=build",
            *concurrency_fragments,
        ),
        "build",
    )
    _require_fragments(
        RUNTIME_WORKFLOW_PATH,
        (
            "representative-build",
            "representative-smoke",
            "expanded-build",
            "expanded-smoke",
            "schedule|workflow_dispatch",
            "contains(github.event.pull_request.labels.*.name, 'performance')",
            *concurrency_fragments,
        ),
        "runtime",
    )

    print(
        "PASS CI workflow contract "
        f"build={len(representative_build)}/{len(expanded_build)}/{len(full_build)} "
        f"smoke={len(representative_smoke)}/{len(expanded_smoke)}/{len(full_smoke)} "
        f"families={len(representative_families)} loaders={','.join(sorted(representative_loaders))}"
    )


if __name__ == "__main__":
    main()
