#!/usr/bin/env python3
"""Generate a deterministic, target-compatible production smoke fixture."""

from __future__ import annotations

import argparse
import hashlib
import json
import tempfile
import uuid
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REGISTRY = ROOT / "gradle" / "minecraft-targets.json"
GENERATED_ENTRY_COUNT = 20_000
EPOCH = (1980, 1, 1, 0, 0, 0)
MARKER_PATH = "assets/example/textures/fixture-marker.txt"
FIXTURE_VERSION = 2


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def target_metadata(target: dict[str, object]) -> dict[str, object]:
    pack_metadata = target["packMetadata"]
    schema = pack_metadata["schema"]
    pack: dict[str, object] = {"description": "PackForge resources"}
    if schema == "single":
        pack["pack_format"] = int(pack_metadata["packFormat"])
    elif schema == "supported-range":
        pack["pack_format"] = int(pack_metadata["packFormat"])
        pack["supported_formats"] = [
            int(pack_metadata["minFormat"]),
            int(pack_metadata["maxFormat"]),
        ]
    elif schema == "range":
        pack["min_format"] = [int(value) for value in pack_metadata["minFormat"]]
        pack["max_format"] = [int(value) for value in pack_metadata["maxFormat"]]
    else:
        raise ValueError(f"unsupported pack metadata schema: {schema}")
    return {"pack": pack}


def fixture_entries(target: dict[str, object]) -> list[tuple[str, bytes]]:
    entries = [
        ("pack.mcmeta", json_bytes(target_metadata(target))),
        ("assets/minecraft/textures/fixture.txt", b"PackForge fixture\n"),
        ("assets/minecraft/models/item/fixture.json", json_bytes({"parent": "item/generated"})),
        (MARKER_PATH, b"PackForge semantic hash fixture\n"),
    ]
    for index in range(GENERATED_ENTRY_COUNT):
        namespace = f"generated{index % 8}"
        path = f"assets/{namespace}/textures/generated/{index:05d}.bin"
        entries.append((path, f"entry-{index}\n".encode("ascii")))
    return entries


def write_fixture(path: Path, entries: list[tuple[str, bytes]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_STORED, allowZip64=False) as archive:
            for name, contents in entries:
                entry = zipfile.ZipInfo(name, EPOCH)
                entry.compress_type = zipfile.ZIP_STORED
                entry.create_system = 0
                entry.external_attr = 0o100644 << 16
                entry.extra = b""
                entry.comment = b""
                archive.writestr(entry, contents)
        temporary.replace(path)
    finally:
        if temporary.exists():
            temporary.unlink()


def is_valid_fixture(path: Path, entries: list[tuple[str, bytes]]) -> bool:
    if not path.is_file():
        return False
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            expected_names = [name for name, _ in entries]
            if names != expected_names or MARKER_PATH not in names:
                return False
            for name, contents in entries:
                if archive.read(name) != contents:
                    return False
            return archive.testzip() is None
    except (OSError, KeyError, zipfile.BadZipFile):
        return False


def generate(registry_path: Path, target_key: str, output_path: Path) -> dict[str, object]:
    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    target = next((item for item in registry["targets"] if item["key"] == target_key), None)
    if target is None:
        raise ValueError(f"unknown target: {target_key}")
    entries = fixture_entries(target)
    if not is_valid_fixture(output_path, entries):
        write_fixture(output_path, entries)
    data = output_path.read_bytes()
    return {
        "schemaVersion": FIXTURE_VERSION,
        "target": target_key,
        "entryCount": len(entries),
        "sha256": hashlib.sha256(data).hexdigest(),
        "path": str(output_path),
    }


def self_test(registry_path: Path, target_key: str) -> None:
    with tempfile.TemporaryDirectory(prefix="packforge-production-fixture-") as directory:
        first = Path(directory) / "first.zip"
        second = Path(directory) / "second.zip"
        first_result = generate(registry_path, target_key, first)
        second_result = generate(registry_path, target_key, second)
        if first.read_bytes() != second.read_bytes() or first_result["sha256"] != second_result["sha256"]:
            raise AssertionError("production fixture generation is not deterministic")
        if first_result["entryCount"] != GENERATED_ENTRY_COUNT + 4:
            raise AssertionError("production fixture entry count changed")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--registry", type=Path, default=DEFAULT_REGISTRY)
    parser.add_argument("--target")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--self-test", action="store_true")
    return parser.parse_args()


def main() -> None:
    arguments = parse_arguments()
    if arguments.self_test:
        self_test(arguments.registry, arguments.target or "mc1_21_1")
        print("PASS production fixture generator self-test")
        return
    if not arguments.target or not arguments.output:
        raise SystemExit("--target and --output are required unless --self-test is used")
    result = generate(arguments.registry, arguments.target, arguments.output)
    print(f"PASS production fixture target={result['target']} entries={result['entryCount']} sha256={result['sha256']} path={result['path']}")


if __name__ == "__main__":
    main()
