import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


MODULE = importlib.util.spec_from_file_location("input_binding", Path(__file__).with_name("input_binding.py"))
binding = importlib.util.module_from_spec(MODULE)
MODULE.loader.exec_module(binding)


def digest(data):
    return hashlib.sha256(data).hexdigest()


class InputBindingTests(unittest.TestCase):
    def fixture(self):
        temp = tempfile.TemporaryDirectory()
        root = Path(temp.name)
        h = {"mods/sodium.jar": "a" * 64, "resourcepacks/Pack.zip": "b" * 64,
             "shaderpacks/Shader.zip": "c" * 64, "config/packforge.json": "d" * 64,
             "options.txt": digest(b"options")}
        files = [{"id": key, "path": "private/" + key, "sha256": value, "bytes": 1}
                 for key, value in h.items()]
        frozen = {"schema": 1, "suite": "full-modpack", "private": True, "files": files,
                  "optimizerFiles": {"packforge": "mods/packforge.jar", "quickpack": "mods/quick.jar"},
                  "orderedSelectedPackIds": ["vanilla"]}
        def item(ident, sha):
            return {"id": ident, "path": "prepared/" + ident, "sha256": sha}
        mods = [item("fo/sodium.jar", h["mods/sodium.jar"]), item("observer", "f" * 64), item("packforge", "1" * 64)]
        resources = [item("Pack.zip", h["resourcepacks/Pack.zip"])]
        shaders = [item("Shader.zip", h["shaderpacks/Shader.zip"])]
        configs = [item("packforge.json", h["config/packforge.json"])]
        reviewed = {"schema": 1, "suite": "full-modpack", "sourceFrozenProfileSha256": "9" * 64,
                    "mode": "saved", "scenario": "menu", "workload": "fabulously_optimized",
                    "mods": mods, "resourcePacks": resources, "shaderPacks": shaders, "configs": configs,
                    "optionsSnapshot": {"path": "prepared/options.txt", "sha256": h["options.txt"]}, "deviations": []}
        spec = {"mode": "saved", "scenario": "menu", "workload": "fabulously_optimized",
                "mods": mods, "resourcePacks": resources, "shaderPacks": shaders, "configs": configs,
                "optionsSnapshot": {"path": str(root / "options.txt"), "sha256": h["options.txt"]}}
        (root / "options.txt").write_bytes(b"options")
        def bind(name, value):
            path = root / name
            raw = (json.dumps(value, sort_keys=True) + "\n").encode()
            path.write_bytes(raw)
            return {"path": str(path), "sha256": digest(raw)}
        spec["frozenProfileManifest"] = bind("frozen.json", frozen)
        reviewed["sourceFrozenProfileSha256"] = spec["frozenProfileManifest"]["sha256"]
        spec["reviewedInputManifest"] = bind("reviewed.json", reviewed)
        return temp, spec, frozen, reviewed

    def test_valid_and_pilot(self):
        temp, spec, _, _ = self.fixture()
        self.addCleanup(temp.cleanup)
        self.assertIsNotNone(binding.validate(spec))
        self.assertIsNone(binding.validate({"mode": "vanilla"}))

    def test_manifest_pair_and_spec_review_mismatch(self):
        temp, spec, _, _ = self.fixture(); self.addCleanup(temp.cleanup)
        with self.assertRaises(ValueError): binding.validate({**spec, "reviewedInputManifest": None})
        with self.assertRaisesRegex(ValueError, "exactly match"):
            bad = {**spec, "mods": spec["mods"][:-1]}
            binding.validate(bad)

    def test_companion_omitted_added_or_changed_rejected(self):
        temp, spec, _, _ = self.fixture(); self.addCleanup(temp.cleanup)
        for mods in (spec["mods"][:-1], spec["mods"] + [{"id": "fo/extra.jar", "path": "x", "sha256": "2" * 64}],
                     [{**item, "sha256": "2" * 64} if item["id"] == "fo/sodium.jar" else item for item in spec["mods"]]):
            with self.assertRaises(ValueError): binding.validate({**spec, "mods": mods})

    def test_nested_config_tamper_wrong_deviation_and_hash_mismatch(self):
        temp, spec, frozen, reviewed = self.fixture(); self.addCleanup(temp.cleanup)
        tampered = {**spec, "configs": [{**spec["configs"][0], "sha256": "2" * 64}]}
        with self.assertRaises(ValueError): binding.validate(tampered)
        reviewed["deviations"] = [{"id": "config/packforge.json", "sourceSha256": "d" * 64,
                                   "resultSha256": "2" * 64, "kind": "wrong-kind", "reason": "x"}]
        path = Path(spec["reviewedInputManifest"]["path"]); raw = (json.dumps(reviewed, sort_keys=True) + "\n").encode(); path.write_bytes(raw)
        with self.assertRaises(ValueError): binding.validate({**spec, "configs": [{**spec["configs"][0], "sha256": "2" * 64}],
                                                               "reviewedInputManifest": {"path": str(path), "sha256": digest(raw)}})


if __name__ == "__main__":
    unittest.main()
