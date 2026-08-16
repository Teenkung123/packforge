#!/usr/bin/env python3
"""Validate a PackForge publication manifest, matrix, and artifact bundle."""

from __future__ import annotations

import argparse
import hashlib
import json
import tempfile
from pathlib import Path
from typing import Any, Iterable


def _read_json(path: Path, description: str) -> dict[str, Any]:
    if not path.is_file():
        raise SystemExit(f"missing {description}: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exception:
        raise SystemExit(f"invalid {description}: {path}: {exception}") from exception
    if not isinstance(value, dict):
        raise SystemExit(f"{description} must be a JSON object: {path}")
    return value


def _manifest_entries(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list) or not all(isinstance(entry, dict) for entry in artifacts):
        raise SystemExit("release manifest artifacts must be a list of objects")

    count = manifest.get("publishedArtifactCount")
    if not isinstance(count, int) or isinstance(count, bool):
        raise SystemExit("release manifest publishedArtifactCount must be an integer")
    if count != len(artifacts):
        raise SystemExit(
            "release manifest artifact count does not match its entries: "
            f"count={count} entries={len(artifacts)}"
        )

    names: list[str] = []
    for entry in artifacts:
        filename = entry.get("filename")
        if not isinstance(filename, str) or not filename or Path(filename).name != filename:
            raise SystemExit(f"release manifest contains an invalid artifact filename: {filename!r}")
        if filename in names:
            raise SystemExit(f"release manifest contains duplicate artifact filename: {filename}")
        names.append(filename)
    return artifacts


def _manifest_version(manifest: dict[str, Any], expected_version: str | None) -> str:
    version = manifest.get("modVersion")
    if not isinstance(version, str) or not version:
        raise SystemExit("release manifest modVersion must be a non-empty string")
    if expected_version is not None and version != expected_version:
        raise SystemExit(
            f"release manifest version {version!r} does not match requested version {expected_version!r}"
        )
    return version


def _expected_names(manifest: dict[str, Any]) -> list[str]:
    return sorted(str(entry["filename"]) for entry in _manifest_entries(manifest))


def _matrix_rows(matrix: dict[str, Any]) -> list[dict[str, Any]]:
    rows = matrix.get("include")
    if not isinstance(rows, list) or not all(isinstance(row, dict) for row in rows):
        raise SystemExit("publication matrix must contain an include list of objects")
    return rows


def _matrix_filename(row: dict[str, Any], version: str) -> str:
    required = ("loader", "minecraft", "version_suffix")
    missing = [key for key in required if key not in row]
    if missing:
        raise SystemExit(f"publication matrix row is missing: {', '.join(missing)}")
    loader = str(row["loader"])
    minecraft = str(row["minecraft"])
    suffix = str(row["version_suffix"])
    return f"packforge-{loader}-{version}{suffix}-mc{minecraft}.jar"


def validate_publication_matrix(manifest: dict[str, Any], matrix: dict[str, Any]) -> None:
    """Require every generated publication row to map to one manifest entry."""

    version = _manifest_version(manifest, None)
    entries = _manifest_entries(manifest)
    rows = _matrix_rows(matrix)
    expected_names = _expected_names(manifest)
    generated_names = [_matrix_filename(row, version) for row in rows]

    if len(generated_names) != len(set(generated_names)):
        raise SystemExit("publication matrix contains duplicate artifact names")
    if sorted(generated_names) != expected_names:
        raise SystemExit(
            "publication matrix artifact names do not match release manifest: "
            f"manifest={expected_names} matrix={sorted(generated_names)}"
        )

    entries_by_name = {str(entry["filename"]): entry for entry in entries}
    for row, filename in zip(rows, generated_names):
        entry = entries_by_name[filename]
        if str(row.get("loader")) != str(entry.get("loader")):
            raise SystemExit(f"publication matrix loader does not match manifest for {filename}")
        if str(row.get("target")) != str(entry.get("target")):
            raise SystemExit(f"publication matrix target does not match manifest for {filename}")
        if str(row.get("minecraft")) != str(entry.get("minecraft")):
            raise SystemExit(f"publication matrix Minecraft coordinate does not match manifest for {filename}")
        row_versions = str(row.get("versions", "")).splitlines()
        if row_versions != [str(version) for version in entry.get("gameVersions", [])]:
            raise SystemExit(f"publication matrix game versions do not match manifest for {filename}")
        expected_release_type = str(entry.get("releaseType"))
        if str(row.get("release_type")) != expected_release_type:
            raise SystemExit(f"publication matrix release type does not match manifest for {filename}")


def _sha256sum_rows(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise SystemExit(f"missing SHA256SUMS file: {path}")
    rows: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line:
            continue
        parts = line.split(maxsplit=1)
        if len(parts) != 2 or len(parts[0]) != hashlib.sha256().digest_size * 2:
            raise SystemExit(f"invalid SHA256SUMS row {line_number}: {raw_line!r}")
        digest, filename = parts
        filename = filename.lstrip("*")
        if not filename or Path(filename).name != filename:
            raise SystemExit(f"invalid SHA256SUMS filename on row {line_number}: {filename!r}")
        if filename in rows:
            raise SystemExit(f"duplicate SHA256SUMS filename: {filename}")
        try:
            int(digest, 16)
        except ValueError as exception:
            raise SystemExit(f"invalid SHA256SUMS digest on row {line_number}: {digest!r}") from exception
        rows[filename] = digest.lower()
    return rows


def _digest(path: Path, algorithm: str) -> str:
    hasher = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def validate_bundle(
    artifacts_dir: Path,
    manifest_path: Path,
    checksums_path: Path | None = None,
    release_table_path: Path | None = None,
    expected_version: str | None = None,
) -> dict[str, Any]:
    """Validate exact artifact names, checksums, and manifest hashes in a bundle."""

    manifest = _read_json(manifest_path, "release manifest")
    _manifest_version(manifest, expected_version)
    entries = _manifest_entries(manifest)
    expected_names = sorted(str(entry["filename"]) for entry in entries)
    if not artifacts_dir.is_dir():
        raise SystemExit(f"missing artifact directory: {artifacts_dir}")
    actual_names = sorted(path.name for path in artifacts_dir.glob("packforge-*.jar") if path.is_file())
    if actual_names != expected_names:
        raise SystemExit(
            "publication bundle artifact names do not match release manifest: "
            f"manifest={expected_names} bundle={actual_names}"
        )

    checksum_rows = _sha256sum_rows(checksums_path or artifacts_dir / "SHA256SUMS")
    if sorted(checksum_rows) != expected_names:
        raise SystemExit(
            "SHA256SUMS filenames do not match release manifest: "
            f"manifest={expected_names} checksums={sorted(checksum_rows)}"
        )

    for entry in entries:
        filename = str(entry["filename"])
        artifact = artifacts_dir / filename
        sha256 = _digest(artifact, "sha256")
        if checksum_rows[filename] != sha256:
            raise SystemExit(f"SHA256SUMS digest mismatch: {filename}")
        if "size" in entry and int(entry["size"]) != artifact.stat().st_size:
            raise SystemExit(f"release manifest size mismatch: {filename}")
        if "sha256" in entry and str(entry["sha256"]).lower() != sha256:
            raise SystemExit(f"release manifest SHA-256 mismatch: {filename}")
        if "sha512" in entry and str(entry["sha512"]).lower() != _digest(artifact, "sha512"):
            raise SystemExit(f"release manifest SHA-512 mismatch: {filename}")

    table = release_table_path or manifest_path.with_name("release-table.md")
    if not table.is_file():
        raise SystemExit(f"missing release table: {table}")
    return {"artifactCount": len(entries), "artifactNames": expected_names, "manifest": str(manifest_path)}


def _self_test() -> None:
    manifest = {
        "schemaVersion": 1,
        "modVersion": "test",
        "publishedArtifactCount": 2,
        "artifacts": [
            {
                "filename": "packforge-fabric-test-mc1.jar",
                "loader": "fabric",
                "target": "test",
                "minecraft": "1",
                "gameVersions": ["1"],
                "releaseType": "release",
            },
            {
                "filename": "packforge-forge-test-beta-mc1.jar",
                "loader": "forge",
                "target": "test-beta",
                "minecraft": "1",
                "gameVersions": ["1"],
                "releaseType": "beta",
            },
        ],
    }
    matrix = {
        "include": [
            {
                "target": "test",
                "loader": "fabric",
                "minecraft": "1",
                "version_suffix": "",
                "versions": "1",
                "release_type": "release",
            },
            {
                "target": "test-beta",
                "loader": "forge",
                "minecraft": "1",
                "version_suffix": "-beta",
                "versions": "1",
                "release_type": "beta",
            },
        ]
    }
    validate_publication_matrix(manifest, matrix)
    with tempfile.TemporaryDirectory(prefix="packforge-publication-bundle-") as directory:
        root = Path(directory)
        artifacts = root / "libs"
        artifacts.mkdir()
        entries = []
        for entry in manifest["artifacts"]:
            path = artifacts / entry["filename"]
            path.write_bytes(entry["filename"].encode("ascii"))
            entry["size"] = path.stat().st_size
            entry["sha256"] = _digest(path, "sha256")
            entry["sha512"] = _digest(path, "sha512")
            entries.append(entry)
        manifest_path = artifacts / "release" / "manifest.json"
        manifest_path.parent.mkdir()
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        (manifest_path.parent / "release-table.md").write_text("# test\n", encoding="utf-8")
        (artifacts / "SHA256SUMS").write_text(
            "".join(f"{entry['sha256']}  {entry['filename']}\n" for entry in entries),
            encoding="utf-8",
        )
        validate_bundle(artifacts, manifest_path, expected_version="test")
        (artifacts / entries[0]["filename"]).write_bytes(b"changed")
        try:
            validate_bundle(artifacts, manifest_path, expected_version="test")
        except SystemExit:
            return
        raise AssertionError("bundle self-test did not reject a changed artifact")


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--artifacts-dir", type=Path)
    parser.add_argument("--checksums", type=Path)
    parser.add_argument("--release-table", type=Path)
    parser.add_argument("--publication-matrix", type=Path)
    parser.add_argument("--release-version")
    parser.add_argument("--self-test", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = _parse_args()
    if args.self_test:
        _self_test()
        print("PASS publication bundle validator self-test")
        return
    if args.manifest is None:
        raise SystemExit("--manifest is required unless --self-test is used")
    manifest = _read_json(args.manifest, "release manifest")
    _manifest_version(manifest, args.release_version)
    if args.publication_matrix is not None:
        matrix = _read_json(args.publication_matrix, "publication matrix")
        validate_publication_matrix(manifest, matrix)
    if args.artifacts_dir is not None:
        result = validate_bundle(
            args.artifacts_dir,
            args.manifest,
            args.checksums,
            args.release_table,
            args.release_version,
        )
        print(f"PASS publication bundle artifacts={result['artifactCount']} manifest={args.manifest}")
    elif args.publication_matrix is not None:
        print(
            "PASS publication matrix "
            f"rows={len(_matrix_rows(_read_json(args.publication_matrix, 'publication matrix')))} "
            f"manifest={args.manifest}"
        )
    else:
        raise SystemExit("one of --artifacts-dir or --publication-matrix is required")


if __name__ == "__main__":
    main()
