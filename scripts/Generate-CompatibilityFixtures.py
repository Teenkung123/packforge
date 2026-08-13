#!/usr/bin/env python3
"""Generate deterministic, offline resource-pack fixtures for compatibility smoke runs."""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import tempfile
import warnings
import zlib
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_DIRECTORY = ROOT / "build" / "compatibility-fixtures"
MANIFEST_NAME = "compatibility-fixtures-manifest.json"
EPOCH = (1980, 1, 1, 0, 0, 0)
SUPPORTED_MINECRAFT = "1.21.1"
PACK_FORMAT = 34
HIGH_ENTRY_COUNT = 20_000
MODEL_COUNT = 256
FONT_PROVIDER_COUNT = 32


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def pack_metadata(description: str, overlays: bool = False) -> bytes:
    metadata: dict[str, object] = {
        "pack": {
            "description": description,
            "pack_format": PACK_FORMAT,
        }
    }
    if overlays:
        metadata["overlays"] = {
            "entries": [{
                "directory": "overlay",
                "formats": {"max_inclusive": PACK_FORMAT, "min_inclusive": PACK_FORMAT},
            }]
        }
    return json_bytes(metadata)


def png_rgba(width: int, height: int, seed: int) -> bytes:
    """Return a deterministic valid RGBA PNG without non-stdlib image tooling."""
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            rows.extend(((x * 17 + seed) & 0xFF, (y * 31 + seed) & 0xFF, (x + y * 3 + seed) & 0xFF, 0xFF))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)) + chunk(
        b"IDAT", zlib.compress(bytes(rows), level=9)
    ) + chunk(b"IEND", b"")


@dataclass(frozen=True)
class ArchiveEntry:
    name: str
    contents: bytes


@dataclass(frozen=True)
class FixtureSpec:
    fixture_id: str
    filename: str
    intent: str
    build_entries: Callable[[], list[ArchiveEntry]]
    required_entries: tuple[str, ...]
    expected_entry_count: int
    duplicate_contents: tuple[tuple[str, tuple[bytes, ...]], ...] = ()


def base_entries(description: str) -> list[ArchiveEntry]:
    return [
        ArchiveEntry("pack.mcmeta", pack_metadata(description)),
        ArchiveEntry("assets/minecraft/textures/fixture.txt", b"PackForge fixture\n"),
        ArchiveEntry("assets/minecraft/models/item/fixture.json", json_bytes({"parent": "item/generated"})),
    ]


def normal_entries() -> list[ArchiveEntry]:
    return base_entries("PackForge normal compatibility fixture")


def high_entry_count_entries() -> list[ArchiveEntry]:
    entries = base_entries("PackForge high-entry-count compatibility fixture")
    entries[0] = ArchiveEntry(
        "pack.mcmeta",
        pack_metadata("PackForge high-entry-count/overlay/malformed compatibility fixture", overlays=True),
    )
    entries.extend((
        ArchiveEntry("assets/example/textures/duplicate.txt", b"base-version\n"),
        ArchiveEntry("overlay/assets/example/textures/duplicate.txt", b"overlay-version\n"),
        ArchiveEntry("assets/minecraft/textures/duplicate.txt", b"first-zip-entry\n"),
        ArchiveEntry("assets/minecraft/textures/duplicate.txt", b"last-zip-entry\n"),
        ArchiveEntry("assets/second_namespace/models/item/fixture.json", json_bytes({"parent": "item/generated"})),
        ArchiveEntry("assets/empty_namespace/", b""),
        ArchiveEntry("assets/BadNamespace/textures/ignored.txt", b"invalid namespace case\n"),
        ArchiveEntry("assets/foo@bar/textures/legacy.txt", b"invalid namespace symbol\n"),
        ArchiveEntry("assets/minecraft/textures/Bad Path.png", b"invalid resource path\n"),
        ArchiveEntry("assets/minecraft/../escape.txt", b"normal zip entry; resource policy must reject\n"),
        ArchiveEntry("assets//textures/empty-namespace.txt", b"empty namespace path\n"),
    ))
    for index in range(HIGH_ENTRY_COUNT):
        namespace = f"generated{index % 8}"
        entries.append(ArchiveEntry(
            f"assets/{namespace}/textures/generated/{index:05d}.bin",
            f"entry-{index}\n".encode("ascii"),
        ))
    return entries


def shader_entries() -> list[ArchiveEntry]:
    entries = base_entries("PackForge shader compatibility fixture")
    entries.extend((
        ArchiveEntry("assets/minecraft/shaders/core/packforge_fixture.json", json_bytes({
            "attributes": ["Position"],
            "blend": {"func": "add", "srcrgb": "srcalpha", "dstrgb": "1-srcalpha"},
            "fragment": "packforge_fixture",
            "samplers": [],
            "uniforms": [],
            "vertex": "packforge_fixture",
        })),
        ArchiveEntry("assets/minecraft/shaders/core/packforge_fixture.vsh", b"#version 150\nin vec3 Position;\nvoid main(){gl_Position=vec4(Position,1.0);}\n"),
        ArchiveEntry("assets/minecraft/shaders/core/packforge_fixture.fsh", b"#version 150\nout vec4 fragColor;\nvoid main(){fragColor=vec4(1.0);}\n"),
    ))
    return entries


def connected_textures_entries() -> list[ArchiveEntry]:
    entries = base_entries("PackForge connected-textures compatibility fixture")
    entries.extend((
        ArchiveEntry("assets/minecraft/optifine/ctm/packforge/fixture.properties", b"method=fixed\nmatchBlocks=stone\ntiles=0\n"),
        ArchiveEntry("assets/minecraft/optifine/ctm/packforge/0.png", png_rgba(16, 16, 41)),
        ArchiveEntry("assets/minecraft/textures/block/packforge_ctm_base.png", png_rgba(16, 16, 42)),
    ))
    return entries


def cit_entries() -> list[ArchiveEntry]:
    entries = base_entries("PackForge CIT compatibility fixture")
    entries.extend((
        ArchiveEntry("assets/minecraft/optifine/cit/packforge/fixture.properties", b"type=item\nitems=diamond\ntexture=fixture\n"),
        ArchiveEntry("assets/minecraft/optifine/cit/packforge/fixture.png", png_rgba(16, 16, 51)),
    ))
    return entries


def entity_entries() -> list[ArchiveEntry]:
    entries = base_entries("PackForge entity texture/model compatibility fixture")
    entries.extend((
        ArchiveEntry("assets/minecraft/optifine/random/entity/cow/cow.properties", b"skins.1=2\nbiomes.1=plains\n"),
        ArchiveEntry("assets/minecraft/optifine/random/entity/cow/cow2.png", png_rgba(64, 32, 61)),
        ArchiveEntry("assets/minecraft/optifine/cem/cow.jem", json_bytes({
            "models": [],
            "texture": "minecraft:textures/entity/cow/cow.png",
            "textureSize": [64, 32],
        })),
    ))
    return entries


def font_heavy_entries() -> list[ArchiveEntry]:
    entries = base_entries("PackForge font-heavy compatibility fixture")
    providers: list[dict[str, object]] = []
    for index in range(FONT_PROVIDER_COUNT):
        character = chr(ord("A") + index % 26)
        path = f"minecraft:font/fixture_{index:02d}.png"
        providers.append({"ascent": 8, "chars": [character], "file": path, "height": 8, "type": "bitmap"})
        entries.append(ArchiveEntry(f"assets/minecraft/font/fixture_{index:02d}.png", png_rgba(8, 8, index)))
    entries.append(ArchiveEntry("assets/minecraft/font/default.json", json_bytes({"providers": providers})))
    return entries


def model_heavy_entries() -> list[ArchiveEntry]:
    entries = base_entries("PackForge model-heavy compatibility fixture")
    for index in range(MODEL_COUNT):
        name = f"fixture_{index:03d}"
        entries.append(ArchiveEntry(
            f"assets/minecraft/models/item/{name}.json",
            json_bytes({"parent": "item/generated", "textures": {"layer0": f"minecraft:item/{name}"}}),
        ))
        entries.append(ArchiveEntry(
            f"assets/minecraft/blockstates/{name}.json",
            json_bytes({"variants": {"": {"model": f"minecraft:item/{name}"}}}),
        ))
    return entries


def mipmap_high_resolution_entries() -> list[ArchiveEntry]:
    entries = base_entries("PackForge mipmap/high-resolution compatibility fixture")
    for size in (256, 512, 1024):
        entries.append(ArchiveEntry(
            f"assets/minecraft/textures/fixture/mipmap_{size}.png",
            png_rgba(size, size, size // 4),
        ))
    entries.append(ArchiveEntry(
        "assets/minecraft/textures/fixture/mipmap_1024.png.mcmeta",
        json_bytes({"animation": {"frametime": 2}}),
    ))
    return entries


FIXTURES = (
    FixtureSpec(
        "normal-resource-pack",
        "normal-resource-pack.zip",
        "Baseline resource-pack startup and reload path.",
        normal_entries,
        ("pack.mcmeta", "assets/minecraft/textures/fixture.txt"),
        3,
    ),
    FixtureSpec(
        "high-entry-count-resource-pack",
        "high-entry-count-resource-pack.zip",
        "Archive enumeration and ZIP-pool pressure with 20,000 generated resources; also covers overlay, namespace, duplicate central-directory, and malformed-but-ZIP-readable paths.",
        high_entry_count_entries,
        ("pack.mcmeta", "assets/generated7/textures/generated/19999.bin", "overlay/assets/example/textures/duplicate.txt", "assets/minecraft/../escape.txt"),
        14 + HIGH_ENTRY_COUNT,
        (("assets/minecraft/textures/duplicate.txt", (b"first-zip-entry\n", b"last-zip-entry\n")),),
    ),
    FixtureSpec(
        "shader-resource-pack",
        "shader-resource-pack.zip",
        "Shader-resource discovery with deterministic core shader metadata and sources.",
        shader_entries,
        ("pack.mcmeta", "assets/minecraft/shaders/core/packforge_fixture.json", "assets/minecraft/shaders/core/packforge_fixture.fsh"),
        6,
    ),
    FixtureSpec(
        "connected-textures-resource-pack",
        "connected-textures-resource-pack.zip",
        "Connected-texture metadata and texture resources for Continuity-style profile paths.",
        connected_textures_entries,
        ("pack.mcmeta", "assets/minecraft/optifine/ctm/packforge/fixture.properties", "assets/minecraft/optifine/ctm/packforge/0.png"),
        6,
    ),
    FixtureSpec(
        "cit-resource-pack",
        "cit-resource-pack.zip",
        "Custom-item-texture metadata and texture resources for CIT Resewn profile paths.",
        cit_entries,
        ("pack.mcmeta", "assets/minecraft/optifine/cit/packforge/fixture.properties", "assets/minecraft/optifine/cit/packforge/fixture.png"),
        5,
    ),
    FixtureSpec(
        "entity-resource-pack",
        "entity-resource-pack.zip",
        "Entity texture and model metadata for ETF/EMF profile paths.",
        entity_entries,
        ("pack.mcmeta", "assets/minecraft/optifine/random/entity/cow/cow.properties", "assets/minecraft/optifine/cem/cow.jem"),
        6,
    ),
    FixtureSpec(
        "font-heavy-resource-pack",
        "font-heavy-resource-pack.zip",
        "Bitmap font-provider discovery and font reload work with 32 deterministic providers.",
        font_heavy_entries,
        ("pack.mcmeta", "assets/minecraft/font/default.json", "assets/minecraft/font/fixture_31.png"),
        3 + FONT_PROVIDER_COUNT + 1,
    ),
    FixtureSpec(
        "model-heavy-resource-pack",
        "model-heavy-resource-pack.zip",
        "Model and blockstate loading with 256 deterministic model pairs.",
        model_heavy_entries,
        ("pack.mcmeta", "assets/minecraft/models/item/fixture_255.json", "assets/minecraft/blockstates/fixture_255.json"),
        3 + MODEL_COUNT * 2,
    ),
    FixtureSpec(
        "mipmap-heavy-resource-pack",
        "mipmap-heavy-resource-pack.zip",
        "High-resolution texture decode and atlas mipmap work with 256, 512, and 1024 pixel PNGs.",
        mipmap_high_resolution_entries,
        ("pack.mcmeta", "assets/minecraft/textures/fixture/mipmap_1024.png", "assets/minecraft/textures/fixture/mipmap_1024.png.mcmeta"),
        7,
    ),
)
EXPECTED_FIXTURE_IDS = (
    "normal-resource-pack",
    "high-entry-count-resource-pack",
    "shader-resource-pack",
    "connected-textures-resource-pack",
    "cit-resource-pack",
    "entity-resource-pack",
    "font-heavy-resource-pack",
    "model-heavy-resource-pack",
    "mipmap-heavy-resource-pack",
)
EXECUTION_SCENARIOS = (
    {
        "id": "repeated-reload",
        "fixtureIds": ["normal-resource-pack", "high-entry-count-resource-pack", "font-heavy-resource-pack", "model-heavy-resource-pack", "mipmap-heavy-resource-pack"],
        "intent": "Client harness must run at least ten reloads and inspect cleanup/leak slopes; a ZIP cannot encode repeated execution.",
        "materialized": False,
    },
    {
        "id": "reload-cancellation-failure",
        "fixtureIds": ["high-entry-count-resource-pack"],
        "intent": "Client harness must cancel or fail an in-progress reload and prove resource cleanup; this is an execution scenario, not a static pack.",
        "materialized": False,
    },
)


def write_zip(path: Path, entries: list[ArchiveEntry]) -> None:
    temporary_path = path.with_name(path.name + ".tmp")
    try:
        with warnings.catch_warnings():
            warnings.filterwarnings("ignore", message=r"Duplicate name: .*", category=UserWarning)
            with zipfile.ZipFile(temporary_path, "w", compression=zipfile.ZIP_STORED, allowZip64=False) as archive:
                archive.comment = b""
                for entry in entries:
                    zip_entry = zipfile.ZipInfo(entry.name, EPOCH)
                    zip_entry.compress_type = zipfile.ZIP_STORED
                    zip_entry.create_system = 0
                    zip_entry.external_attr = 0o100644 << 16
                    zip_entry.extra = b""
                    zip_entry.comment = b""
                    archive.writestr(zip_entry, entry.contents)
        temporary_path.replace(path)
    finally:
        if temporary_path.exists():
            temporary_path.unlink()


def fixture_manifest_entry(spec: FixtureSpec, path: Path) -> dict[str, object]:
    data = path.read_bytes()
    duplicates = [
        {"path": name, "count": len(contents)}
        for name, contents in spec.duplicate_contents
    ]
    return {
        "id": spec.fixture_id,
        "filename": spec.filename,
        "intent": spec.intent,
        "supportedMinecraft": [SUPPORTED_MINECRAFT],
        "packFormat": PACK_FORMAT,
        "entryCount": spec.expected_entry_count,
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "requiredEntries": list(spec.required_entries),
        "duplicateEntries": duplicates,
    }


def generate(output_directory: Path) -> dict[str, object]:
    output_directory.mkdir(parents=True, exist_ok=True)
    fixtures: list[dict[str, object]] = []
    for spec in FIXTURES:
        path = output_directory / spec.filename
        write_zip(path, spec.build_entries())
        fixtures.append(fixture_manifest_entry(spec, path))

    manifest = {
        "schemaVersion": 1,
        "supportedMinecraft": [SUPPORTED_MINECRAFT],
        "packFormat": PACK_FORMAT,
        "fixtures": fixtures,
        "executionScenarios": list(EXECUTION_SCENARIOS),
    }
    (output_directory / MANIFEST_NAME).write_bytes(json_bytes(manifest))
    return manifest


def assert_zip_contract(spec: FixtureSpec, path: Path) -> None:
    if not zipfile.is_zipfile(path):
        raise AssertionError(f"fixture is not ZIP-readable: {path}")
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise AssertionError(f"fixture has CRC failure: {path}")
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(entries) != spec.expected_entry_count:
            raise AssertionError(f"unexpected ZIP entry count for {spec.fixture_id}: {len(entries)}")
        for required_entry in spec.required_entries:
            if required_entry not in names:
                raise AssertionError(f"missing {required_entry} in {spec.fixture_id}")
        if json.loads(archive.read("pack.mcmeta").decode("utf-8"))["pack"]["pack_format"] != PACK_FORMAT:
            raise AssertionError(f"invalid pack metadata in {spec.fixture_id}")
        for duplicate_name, expected_contents in spec.duplicate_contents:
            matching = [entry for entry in entries if entry.filename == duplicate_name]
            actual_contents = tuple(archive.read(entry) for entry in matching)
            if actual_contents != expected_contents:
                raise AssertionError(f"duplicate entry contract changed for {spec.fixture_id}")


def assert_catalog_contract(output_directory: Path) -> None:
    manifest_path = output_directory / MANIFEST_NAME
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    fixture_ids = tuple(entry["id"] for entry in manifest["fixtures"])
    if fixture_ids != EXPECTED_FIXTURE_IDS:
        raise AssertionError(f"fixture IDs changed: {fixture_ids}")
    if manifest["supportedMinecraft"] != [SUPPORTED_MINECRAFT]:
        raise AssertionError(f"catalog Minecraft scope changed: {manifest['supportedMinecraft']}")
    if manifest["packFormat"] != PACK_FORMAT:
        raise AssertionError(f"catalog pack format changed: {manifest['packFormat']}")
    for spec, manifest_entry in zip(FIXTURES, manifest["fixtures"]):
        path = output_directory / spec.filename
        if manifest_entry["filename"] != spec.filename:
            raise AssertionError(f"manifest filename changed for {spec.fixture_id}")
        if manifest_entry["supportedMinecraft"] != [SUPPORTED_MINECRAFT]:
            raise AssertionError(f"Minecraft scope changed for {spec.fixture_id}")
        if manifest_entry["packFormat"] != PACK_FORMAT:
            raise AssertionError(f"pack format changed for {spec.fixture_id}")
        if manifest_entry["entryCount"] != spec.expected_entry_count:
            raise AssertionError(f"manifest entry count changed for {spec.fixture_id}")
        if manifest_entry["size"] != path.stat().st_size:
            raise AssertionError(f"manifest size changed for {spec.fixture_id}")
        if manifest_entry["sha256"] != hashlib.sha256(path.read_bytes()).hexdigest():
            raise AssertionError(f"manifest hash changed for {spec.fixture_id}")
        assert_zip_contract(spec, path)
    scenario_ids = tuple(scenario["id"] for scenario in manifest["executionScenarios"])
    if scenario_ids != ("repeated-reload", "reload-cancellation-failure"):
        raise AssertionError(f"execution scenario catalog changed: {scenario_ids}")
    if any(scenario["materialized"] for scenario in manifest["executionScenarios"]):
        raise AssertionError("execution scenarios must not claim static ZIP coverage")


def run_self_test() -> None:
    with tempfile.TemporaryDirectory(prefix="packforge-fixtures-first-") as first, tempfile.TemporaryDirectory(
        prefix="packforge-fixtures-second-"
    ) as second:
        first_path = Path(first)
        second_path = Path(second)
        generate(first_path)
        generate(second_path)
        assert_catalog_contract(first_path)
        assert_catalog_contract(second_path)
        first_files = sorted(path.name for path in first_path.iterdir())
        second_files = sorted(path.name for path in second_path.iterdir())
        if first_files != second_files:
            raise AssertionError("deterministic fixture files differ")
        for name in first_files:
            if (first_path / name).read_bytes() != (second_path / name).read_bytes():
                raise AssertionError(f"fixture bytes differ across generations: {name}")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIRECTORY)
    parser.add_argument(
        "--minecraft-version",
        default=SUPPORTED_MINECRAFT,
        help=f"Exact fixture target; only {SUPPORTED_MINECRAFT} is implemented.",
    )
    parser.add_argument("--self-test", action="store_true", help="Generate twice in temporary directories and validate deterministic contracts.")
    return parser.parse_args()


def main() -> None:
    arguments = parse_arguments()
    if arguments.minecraft_version != SUPPORTED_MINECRAFT:
        raise SystemExit(
            f"unsupported fixture Minecraft version: {arguments.minecraft_version}; only {SUPPORTED_MINECRAFT} is implemented"
        )
    if arguments.self_test:
        run_self_test()
        print("PASS compatibility fixture generator self-test")
        return
    manifest = generate(arguments.output_dir)
    print(f"PASS generated {len(manifest['fixtures'])} compatibility fixtures in {arguments.output_dir}")


if __name__ == "__main__":
    main()
