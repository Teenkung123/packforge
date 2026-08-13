#!/usr/bin/env python3
"""Focused self-test for release-manifest verification."""

from __future__ import annotations

import importlib.util
import json
import tempfile
from pathlib import Path


SCRIPT = Path(__file__).with_name("Generate-ReleaseManifest.py")
SPEC = importlib.util.spec_from_file_location("release_manifest", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


REGISTRY = {
    "schemaVersion": 2,
    "releaseCells": [
        {"targetKey": "test", "id": "1.0", "loaderAvailability": ["fabric"], "buildStatus": "existing"}
    ],
    "targets": [
        {
            "key": "test",
            "loaderAvailability": ["fabric"],
            "artifactMinecraft": "1.0",
            "maturity": "stable",
            "platforms": {"fabric": {}},
        }
    ],
}


def expect_failure(action) -> None:
    try:
        action()
    except SystemExit:
        return
    raise AssertionError("verification unexpectedly succeeded")


with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary)
    artifacts_dir = root / "libs"
    output_dir = artifacts_dir / "release"
    artifacts_dir.mkdir()
    artifact = artifacts_dir / "packforge-fabric-1.0-mc1.0.jar"
    artifact.write_bytes(b"release artifact")

    manifest = MODULE.build_manifest(REGISTRY, "1.0", artifacts_dir)
    output_dir.mkdir()
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    MODULE.verify_existing_manifest(manifest_path, REGISTRY, "1.0", artifacts_dir)

    artifact.write_bytes(b"changed artifact")
    expect_failure(lambda: MODULE.verify_existing_manifest(manifest_path, REGISTRY, "1.0", artifacts_dir))

    artifact.write_bytes(b"release artifact")
    (artifacts_dir / "packforge-stale.jar").write_bytes(b"stale")
    expect_failure(lambda: MODULE.verify_existing_manifest(manifest_path, REGISTRY, "1.0", artifacts_dir))
