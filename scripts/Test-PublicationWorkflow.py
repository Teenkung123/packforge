#!/usr/bin/env python3
"""Cheap static and registry-derived contract test for Modrinth publication CI."""

from __future__ import annotations

import importlib.util
import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "publish-modrinth.yml"
PROPERTIES_PATH = ROOT / "gradle.properties"
MATRIX_SCRIPT = ROOT / "scripts" / "Generate-CiMatrix.py"
MANIFEST_SCRIPT = ROOT / "scripts" / "Generate-ReleaseManifest.py"
VALIDATOR_SCRIPT = ROOT / "scripts" / "Validate-PublicationBundle.py"


def _load_module(name: str, path: Path) -> Any:
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise AssertionError(f"unable to load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _configured_version() -> str:
    for line in PROPERTIES_PATH.read_text(encoding="utf-8").splitlines():
        if line.startswith("mod_version="):
            return line.split("=", 1)[1].strip()
    raise AssertionError("gradle.properties is missing mod_version")


def _workflow_default(workflow: str) -> str:
    match = re.search(
        r"release_version:\s*\n(?:[^\n]*\n)*?\s*default:\s*[\"']?([^\"'\s]+)",
        workflow,
    )
    if match is None:
        raise AssertionError("workflow is missing release_version default")
    return match.group(1)


def _reject_stale_workflow_values(workflow: str, configured_version: str) -> None:
    if _workflow_default(workflow) != configured_version:
        raise AssertionError(
            "workflow release_version default does not match gradle.properties: "
            f"default={_workflow_default(workflow)!r} configured={configured_version!r}"
        )
    if "1.3.4" in workflow:
        raise AssertionError("workflow contains stale release version 1.3.4")

    stale_count_patterns = (
        r"(?i)expected\s+(?:exactly\s+)?17\s+(?:release\s+)?artifacts?",
        r"(?i)expected\s+17\s+checksum",
        r"(?i)(?:artifacts?|checksums?|checksum\s+rows?).{0,100}-ne\s*17\b",
        r"(?i)-ne\s*17\b.{0,100}(?:artifacts?|checksums?|checksum\s+rows?)",
    )
    for pattern in stale_count_patterns:
        if re.search(pattern, workflow):
            raise AssertionError(f"workflow contains stale hard-coded artifact/checksum count: {pattern}")


def _assert_wiring(workflow: str) -> None:
    required_fragments = (
        "Generate-CiMatrix.py publish",
        "Generate-ReleaseManifest.py",
        "Validate-PublicationBundle.py",
        "build:",
        "runtime-smoke:",
        "publish:",
    )
    missing = [fragment for fragment in required_fragments if fragment not in workflow]
    if missing:
        raise AssertionError(f"publication workflow is missing required wiring: {', '.join(missing)}")
    if workflow.count("Validate-PublicationBundle.py") < 3:
        raise AssertionError("publication bundle must be validated in build, runtime-smoke, and publish jobs")


def _assert_registry_parity() -> tuple[int, list[str]]:
    matrix_module = _load_module("generate_ci_matrix", MATRIX_SCRIPT)
    manifest_module = _load_module("generate_release_manifest", MANIFEST_SCRIPT)
    validator_module = _load_module("validate_publication_bundle", VALIDATOR_SCRIPT)
    registry = matrix_module.load_registry()
    version = _configured_version()
    matrix = matrix_module.publication_matrix(registry)
    manifest = manifest_module.build_manifest(registry, version, None)
    validator_module.validate_publication_matrix(manifest, matrix)
    names = sorted(entry["filename"] for entry in manifest["artifacts"])
    if len(matrix["include"]) != manifest["publishedArtifactCount"]:
        raise AssertionError(
            "publication matrix count does not match manifest: "
            f"matrix={len(matrix['include'])} manifest={manifest['publishedArtifactCount']}"
        )
    return len(names), names


def main() -> None:
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
    configured_version = _configured_version()
    _reject_stale_workflow_values(workflow, configured_version)
    _assert_wiring(workflow)
    count, names = _assert_registry_parity()
    print(f"PASS publication workflow contract version={configured_version} artifacts={count}")
    for name in names:
        print(name)


if __name__ == "__main__":
    main()
