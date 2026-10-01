from __future__ import annotations

import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import tempfile
import textwrap
import unittest
import zipfile
from pathlib import Path


SCRIPT = Path(__file__).with_name("prepare-modrinth-release.py")
WORKFLOW = SCRIPT.parents[1] / ".github" / "workflows" / "publish-modrinth.yml"
BUILD_WORKFLOW = SCRIPT.parents[1] / ".github" / "workflows" / "build.yml"
SPEC = importlib.util.spec_from_file_location("prepare_modrinth_release", SCRIPT)
assert SPEC and SPEC.loader
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


def fixture_registry(
    *,
    loader: str = "fabric",
    max_bytes: int = 4096,
    game_versions: list[str] | None = None,
) -> dict:
    if loader == "fabric":
        platform = {
            "maxArtifactBytes": max_bytes,
            "maturity": "stable",
            "versionSuffix": "",
            "loaderDependency": ">=0.19.2",
            "minecraftDependency": ">=26.1 <26.3",
            "mappingMode": "official",
        }
    else:
        platform = {
            "maxArtifactBytes": max_bytes,
            "maturity": "beta",
            "versionSuffix": "-candidate",
            "version": "26.1-62.0.0",
            "versionRange": "[62.0.0,66.0.0)",
            "minecraftVersionRange": "[26.1,26.3)",
        }
    target = {
        "key": "fixture_range",
        "minecraftVersion": "26.1",
        "artifactMinecraft": "26.1-26.2",
        "javaVersion": 25,
        "gameVersions": game_versions
        if game_versions is not None
        else ["26.1", "26.1.1", "26.1.2", "26.2"],
        "platforms": {loader: platform},
    }
    return {"schemaVersion": 1, "targets": [target]}


def write_fixture_artifact(
    directory: Path,
    registry: dict,
    content_suffix: bytes = b"",
    source_url: str = "https://github.com/Teenkung123/packforge",
) -> Path:
    item = release.expected_artifacts(registry, "1.4")[0]
    path = directory / item["name"]
    with zipfile.ZipFile(path, "w") as archive:
        if item["loader"] == "fabric":
            archive.writestr(
                "fabric.mod.json",
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "id": "packforge",
                        "version": "1.4",
                        "license": "MIT",
                        "contact": {
                            "homepage": source_url,
                            "sources": source_url,
                        },
                        "depends": {
                            "fabricloader": ">=0.19.2",
                            "minecraft": ">=26.1 <26.3",
                            "java": ">=25",
                        },
                        "suggests": {"modmenu": "*"},
                    }
                ),
            )
        else:
            archive.writestr(
                "META-INF/mods.toml",
                '\n'.join(
                    [
                        'modLoader="javafml"',
                        'license="MIT"',
                        f'issueTrackerURL="{source_url}/issues"',
                        "[[mods]]",
                        'modId="packforge"',
                        'version="1.4"',
                        f'displayURL="{source_url}"',
                        "[[dependencies.packforge]]",
                        'modId="forge"',
                        "mandatory=true",
                        'versionRange="[62.0.0,66.0.0)"',
                        "[[dependencies.packforge]]",
                        'modId="minecraft"',
                        "mandatory=true",
                        'versionRange="[26.1,26.3)"',
                    ]
                ),
            )
        if content_suffix:
            archive.writestr("fixture.bin", content_suffix)
    return path


class ReleasePreparationTests(unittest.TestCase):
    def test_valid_fixture_has_payload_hashes_dependencies_and_classifiers(self) -> None:
        registry = fixture_registry()
        with tempfile.TemporaryDirectory() as temporary:
            artifact_dir = Path(temporary)
            artifact = write_fixture_artifact(artifact_dir, registry, b"payload")
            artifact_bytes = artifact.stat().st_size
            artifact_data = artifact.read_bytes()
            items, errors = release.inspect(registry, artifact_dir, "1.4", "Fixture notes")

        self.assertEqual([], errors)
        item = items[0]
        self.assertTrue(item["payload_ready"])
        self.assertEqual(item["bytes"], artifact_bytes)
        self.assertEqual(
            item["hashes"]["sha1"],
            hashlib.sha1(artifact_data).hexdigest(),
        )
        self.assertEqual(
            item["hashes"]["sha512"],
            hashlib.sha512(artifact_data).hexdigest(),
        )
        self.assertEqual(
            [{"project_id": "mOgUt4GM", "dependency_type": "optional"}],
            item["payload"]["dependencies"],
        )
        self.assertEqual("Fixture notes", item["payload"]["changelog"])
        self.assertEqual("client_only", item["payload"]["environment"])
        self.assertEqual(
            {
                "name",
                "version_number",
                "changelog",
                "dependencies",
                "game_versions",
                "version_type",
                "loaders",
                "featured",
                "status",
                "project_id",
                "file_parts",
                "primary_file",
                "environment",
            },
            set(item["payload"]),
        )
        self.assertEqual(
            [
                {"id": "fabricloader", "version": ">=0.19.2", "required": True},
                {"id": "minecraft", "version": ">=26.1 <26.3", "required": True},
                {"id": "java", "version": ">=25", "required": True},
            ],
            item["required_loader_classifiers"],
        )

    def test_missing_and_extra_artifacts_are_rejected(self) -> None:
        registry = fixture_registry()
        with tempfile.TemporaryDirectory() as temporary:
            artifact_dir = Path(temporary)
            _, missing_errors = release.inspect(registry, artifact_dir, "1.4")
            self.assertTrue(any(error.startswith("missing artifact:") for error in missing_errors))

            write_fixture_artifact(artifact_dir, registry)
            (artifact_dir / "packforge-unexpected-1.4.jar").write_bytes(b"extra")
            _, extra_errors = release.inspect(registry, artifact_dir, "1.4")
        self.assertIn("unexpected artifact: packforge-unexpected-1.4.jar", extra_errors)

    def test_size_gate_blocks_payload_readiness(self) -> None:
        registry = fixture_registry(max_bytes=32)
        with tempfile.TemporaryDirectory() as temporary:
            artifact_dir = Path(temporary)
            write_fixture_artifact(artifact_dir, registry, b"large fixture payload")
            items, errors = release.inspect(registry, artifact_dir, "1.4")
        self.assertTrue(any(error.startswith("size gate failed:") for error in errors))
        self.assertFalse(items[0]["size_ok"])
        self.assertFalse(items[0]["payload_ready"])

    def test_range_accepts_patches_and_rejects_out_of_range_patch(self) -> None:
        registry = fixture_registry()
        target = registry["targets"][0]
        self.assertEqual(
            ["26.1", "26.1.1", "26.1.2", "26.2"],
            release.game_versions_for_target(target),
        )
        target["gameVersions"] = ["26.1", "26.1.1", "26.3"]
        with self.assertRaisesRegex(ValueError, "outside 26.1-26.2"):
            release.game_versions_for_target(target)

    def test_registry_suffix_and_maturity_drive_publish_matrix(self) -> None:
        registry = fixture_registry(loader="forge")
        matrix = release.matrix(registry, "1.4", "publish")["include"]
        self.assertEqual(
            {
                "version_suffix": "-candidate",
                "release_type": "beta",
                "featured": False,
                "game_versions": ["26.1", "26.1.1", "26.1.2", "26.2"],
            },
            {key: matrix[0][key] for key in ("version_suffix", "release_type", "featured", "game_versions")},
        )

    def test_publish_matrix_orders_legacy_targets_before_modern_targets(self) -> None:
        registry = release.load_registry(SCRIPT.parents[1] / "gradle" / "minecraft-targets.json")
        rows = release.matrix(registry, "1.4.1", "publish")["include"]

        self.assertEqual(
            [
                ("mc1_20_1", "fabric"),
                ("mc1_20_1", "forge"),
                ("mc1_21_1", "fabric"),
                ("mc1_21_1", "forge"),
                ("mc1_21_1", "neoforge"),
                ("mc1_21_4", "fabric"),
                ("mc1_21_4", "forge"),
                ("mc1_21_4", "neoforge"),
                ("mc1_21_8", "fabric"),
                ("mc1_21_8", "forge"),
                ("mc1_21_8", "neoforge"),
                ("mc1_21_11", "fabric"),
                ("mc1_21_11", "forge"),
                ("mc1_21_11", "neoforge"),
                ("mc26_1_to_26_2", "forge"),
                ("mc26_1_to_26_3", "fabric"),
                ("mc26_1_to_26_3", "neoforge"),
            ],
            [(row["target"], row["loader"]) for row in rows],
        )

    def test_publish_shell_decoder_preserves_beta_and_empty_stable_suffixes(self) -> None:
        bash_candidates = [
            r"C:\Program Files\Git\bin\bash.exe",
            shutil.which("bash"),
        ]
        bash = next((candidate for candidate in bash_candidates if candidate and Path(candidate).is_file()), None)
        if bash is None:
            self.skipTest("bash is required to exercise the workflow decoder")

        registry = release.load_registry(SCRIPT.parents[1] / "gradle" / "minecraft-targets.json")
        publish_matrix = json.dumps(release.matrix(registry, "1.4.1", "publish"))
        decoder = textwrap.dedent(
            """
            set -euo pipefail
            while IFS= read -r artifact_name; do
                printf '%s\\n' "$artifact_name"
            done < <(
                PUBLISH_MATRIX="$PUBLISH_MATRIX" RELEASE_VERSION="$RELEASE_VERSION" python - <<'PY'
            import json
            import os

            matrix = json.loads(os.environ["PUBLISH_MATRIX"])
            for item in matrix["include"]:
                version = os.environ["RELEASE_VERSION"] + item["version_suffix"]
                print(f"packforge-{item['loader']}-{version}-mc{item['minecraft']}.jar")
            PY
            )
            """
        )
        environment = os.environ.copy()
        environment.update(
            PUBLISH_MATRIX=publish_matrix,
            RELEASE_VERSION="1.4.1",
        )
        result = subprocess.run(
            [bash, "-c", decoder],
            check=True,
            capture_output=True,
            text=True,
            env=environment,
        )
        artifact_names = result.stdout.splitlines()
        self.assertEqual(
            "packforge-fabric-1.4.1-beta.1-mc1.20.1.jar",
            artifact_names[0],
        )
        self.assertEqual(
            [
                "packforge-forge-1.4.1-mc26.1-26.2.jar",
                "packforge-fabric-1.4.1-mc26.1-26.3.jar",
                "packforge-neoforge-1.4.1-mc26.1-26.3.jar",
            ],
            artifact_names[-3:],
        )

    def test_forge_loader_classifiers_are_checked(self) -> None:
        registry = fixture_registry(loader="forge")
        with tempfile.TemporaryDirectory() as temporary:
            artifact_dir = Path(temporary)
            write_fixture_artifact(artifact_dir, registry)
            items, errors = release.inspect(registry, artifact_dir, "1.4")
        self.assertEqual([], errors)
        self.assertTrue(items[0]["payload_ready"])
        self.assertEqual("-candidate", items[0]["version_suffix"])

    def test_source_url_mismatch_blocks_release(self) -> None:
        registry = fixture_registry()
        with tempfile.TemporaryDirectory() as temporary:
            artifact_dir = Path(temporary)
            write_fixture_artifact(
                artifact_dir,
                registry,
                source_url="https://github.com/Teenkung/PackForge",
            )
            _, errors = release.inspect(registry, artifact_dir, "1.4")
        self.assertTrue(any(error.startswith("metadata gate failed:") for error in errors))

    def test_preview_contains_payloads_and_file_hashes(self) -> None:
        registry = fixture_registry()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifact_dir = root / "artifacts"
            artifact_dir.mkdir()
            write_fixture_artifact(artifact_dir, registry)
            items, errors = release.inspect(registry, artifact_dir, "1.4", "Preview notes")
            preview_dir = root / "preview"
            release.write_preview(preview_dir, "1.4", registry, items, errors, "Preview notes")
            manifest = json.loads(
                (preview_dir / "modrinth-release-manifest.json").read_text(encoding="utf-8")
            )
        self.assertEqual([], manifest["validationErrors"])
        self.assertEqual(manifest["artifacts"][0]["payload"], manifest["payloads"][0])
        self.assertEqual("Preview notes", manifest["payloads"][0]["changelog"])
        self.assertEqual(
            set(manifest["artifacts"][0]["file"]["hashes"]), {"sha1", "sha512"}
        )

    def test_workflow_keeps_verified_bundle_flat_and_preview_separate(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        verified_start = workflow.index("      - name: Upload verified artifacts")
        verified_end = workflow.index("      - name:", verified_start + 1)
        verified = workflow[verified_start:verified_end]
        preview_start = workflow.index("      - name: Upload release preview")
        preview_end = workflow.index("  publish:", preview_start)
        preview = workflow[preview_start:preview_end]

        self.assertIn("name: packforge-all-supported", verified)
        self.assertIn("build/libs/packforge-*.jar", verified)
        self.assertIn("build/libs/SHA256SUMS", verified)
        self.assertNotIn("build/release-preview", verified)
        self.assertIn("name: packforge-release-preview", preview)
        self.assertIn("build/release-preview/modrinth-release-manifest.json", preview)
        self.assertIn("build/release-preview/modrinth-release-plan.md", preview)

    def test_current_registry_target_matrix_contract_has_seven_targets(self) -> None:
        registry = release.load_registry(SCRIPT.parents[1] / "gradle" / "minecraft-targets.json")
        target_rows = release.matrix(registry, "1.4", "targets")["include"]
        self.assertEqual(7, len(target_rows))
        self.assertEqual(
            {target["key"] for target in registry["targets"]},
            {row["target"] for row in target_rows},
        )

    def test_push_workflows_consume_registry_matrices(self) -> None:
        build = BUILD_WORKFLOW.read_text(encoding="utf-8")
        publish = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("registry_matrix:", build)
        self.assertIn("matrix: ${{ fromJSON(needs.registry_matrix.outputs.target_matrix) }}", build)
        self.assertNotIn("- mc26_1_to_26_2", build)
        self.assertIn("release_matrix:", publish)
        self.assertIn("publish_matrix: ${{ steps.matrix.outputs.publish_matrix }}", publish)
        self.assertIn("needs: [release_matrix, build]", publish)
        publish_job = publish[publish.index("  publish:"):]
        self.assertIn("PUBLISH_MATRIX: ${{ needs.release_matrix.outputs.publish_matrix }}", publish_job)
        self.assertIn("while IFS= read -r artifact_name", publish_job)
        self.assertNotIn("strategy:", publish_job)
        self.assertNotIn("matrix: ${{", publish_job)
        self.assertNotIn("runtime-smoke:", publish)
        self.assertNotIn("  benchmark:", publish)


if __name__ == "__main__":
    unittest.main()
