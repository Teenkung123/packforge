#!/usr/bin/env python3
"""Report final PackForge artifact sizes without rebuilding or launching Minecraft."""

from __future__ import annotations

import argparse
import pathlib
import struct
import sys
import zipfile


ICON_PATH = "assets/packforge/icon.png"
MAX_ICON_BYTES = 32 * 1024


def inspect_artifact(path: pathlib.Path) -> bool:
    entries: list[tuple[str, int, int]] = []
    nested: list[tuple[str, int, int]] = []
    seen: set[str] = set()
    duplicates: list[str] = []
    packforge_class_bytes = 0
    resource_bytes = 0
    icon_bytes = None
    icon_dimensions = None

    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if info.filename in seen:
                duplicates.append(info.filename)
            seen.add(info.filename)
            if info.is_dir():
                continue
            size = info.file_size
            compressed_size = info.compress_size
            entries.append((info.filename, size, compressed_size))
            nested_path = (
                info.filename.startswith("META-INF/jarjar/")
                or info.filename.startswith("META-INF/jars/")
            ) and info.filename.endswith(".jar")
            if info.filename.startswith("com/teenkung/packforge/") and info.filename.endswith(".class"):
                packforge_class_bytes += size
            elif not nested_path and not info.filename.endswith(".class"):
                resource_bytes += size
            if nested_path:
                nested.append((info.filename, size, compressed_size))
            if info.filename == ICON_PATH:
                icon_bytes = archive.read(info)
                if len(icon_bytes) >= 24 and icon_bytes[:8] == b"\x89PNG\r\n\x1a\n" and icon_bytes[12:16] == b"IHDR":
                    icon_dimensions = struct.unpack(">II", icon_bytes[16:24])

    largest = sorted(entries, key=lambda item: item[1], reverse=True)[:5]
    largest_text = ",".join(f"{name}:{size}" for name, size, _ in largest)
    nested_text = ",".join(f"{name}:{size}/{compressed}" for name, size, compressed in nested)
    duplicate_text = ",".join(sorted(set(duplicates))) if duplicates else "none"
    icon_text = "missing" if icon_bytes is None else f"{len(icon_bytes)}:{icon_dimensions[0]}x{icon_dimensions[1]}" if icon_dimensions else f"{len(icon_bytes)}:invalid"
    print(
        f"artifactSize name={path.name} bytes={path.stat().st_size} "
        f"packforgeClassBytes={packforge_class_bytes} resourceBytes={resource_bytes}"
    )
    print(f"artifactSize largest={largest_text}")
    print(f"artifactSize nested={nested_text}")
    print(f"artifactSize icon={icon_text}")
    print(f"artifactSize duplicateZipEntries={duplicate_text}")
    icon_valid = icon_bytes is not None and len(icon_bytes) <= MAX_ICON_BYTES and icon_dimensions == (128, 128)
    return not duplicates and icon_valid


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifacts-dir", type=pathlib.Path, required=True)
    args = parser.parse_args()
    if not args.artifacts_dir.is_dir():
        print(f"Artifact directory does not exist: {args.artifacts_dir}", file=sys.stderr)
        return 2
    artifacts = sorted(
        path
        for path in args.artifacts_dir.glob("packforge-*.jar")
        if not path.name.endswith(("-sources.jar", "-named.jar", "-slim.jar"))
    )
    if not artifacts:
        print(f"No PackForge JARs found in {args.artifacts_dir}", file=sys.stderr)
        return 2
    return 0 if all(inspect_artifact(path) for path in artifacts) else 1


if __name__ == "__main__":
    raise SystemExit(main())
