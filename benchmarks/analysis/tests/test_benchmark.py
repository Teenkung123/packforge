import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("benchmark", Path(__file__).parents[1] / "benchmark.py")
benchmark = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(benchmark)


def fixture(run, improvement=.2):
    ratio = 1 - improvement if run["mode"] == "candidate" else 1
    manifest = {"schema": 1, **{key: run[key] for key in ("runId", "cellId", "mode", "block", "workload")},
                "sessionId": run["runId"], "processStartedEpochMillis": 1000000, "pid": 42,
                "exitCode": 0, "valid": True, "inputsUnchanged": True, "timeout": False,
                "eventsFile": run["runId"] + ".jsonl",
                "context": {"minecraft": run["minecraft"], "loader": run["loader"], "loaderVersion": "1",
                            "jdk": {"version": "25", "vendor": "test", "executableSha256": "a" * 64},
                            "hardware": {"cpu": "test", "gpu": "test", "os": "test", "ramBytes": 4096},
                            "jvmArgs": ["-Xmx4G"], "graphicsSettings": {"width": 1280, "height": 720},
                            "runtimeEnvironment": {"TEMP": str(benchmark.RUNTIME_TEMP_ROOT), "TMP": str(benchmark.RUNTIME_TEMP_ROOT)},
                            "optionsSnapshotPinned": True,
                            "effectiveOptions": {"lang": "en_us", "forceUnicodeFont": "false", "japaneseGlyphVariants": "false",
                                                 "textureFiltering": "0", "mipmapLevels": "4", "graphicsPreset": '"custom"',
                                                 "resourcePacks": '["vanilla","file/DevelopRP-Full-1.9.5.zip"]',
                                                 "inactivityFpsLimit": '"minimized"', "enableVsync": "false", "maxFps": "120"},
                            "orderedPacks": [{"id": "DevelopRP-Full-1.9.5.zip", "sha256": "b" * 64}],
                            "artifacts": [{"id": "observer", "sha256": "c" * 64}], "configs": [{"id": "options.txt", "sha256": "e" * 64}],
                            "filesystemCache": "uncontrolled", "profilingEnabled": False, "resourceHashingEnabled": False}}
    if run["mode"] in ("current", "corrected", "candidate"):
        manifest["context"]["artifacts"].append({"id": "packforge", "sha256": "d" * 64})
    elif run["mode"] == "quickpack":
        manifest["context"]["artifacts"].append({"id": "quickpack", "sha256": "e" * 64})
    elapsed = int(1000 * ratio)
    events = []
    def event(kind, index):
        events.append({"schema": 1, "sessionId": run["runId"], "event": kind,
                       "windowActive": True,
                       "framebufferWidth": 1280, "framebufferHeight": 720,
                       "epochMillis": 1000000 + elapsed, "nanoTime": elapsed * 1000000,
                       "jvmUptimeMillis": elapsed, "reloadIndex": index})
        if kind in ("ready", "frame_ready"):
            events[-1]["activePackIds"] = ["vanilla", "loader_resources", "file/DevelopRP-Full-1.9.5.zip"]
    event("ready", -1)
    for index in range(6):
        elapsed += 100
        event("reload_requested", index)
        elapsed += int(90 * ratio)
        event("reload_complete", index)
        elapsed += int(10 * ratio)
        event("frame_ready", index)
    event("complete", 5)
    return manifest, events


class ScheduleTests(unittest.TestCase):
    def test_deterministic_complete_balance(self):
        schedule = benchmark.make_schedule()
        self.assertEqual(schedule, benchmark.make_schedule())
        self.assertNotEqual(schedule, benchmark.make_schedule(9))
        self.assertEqual(198, len(schedule["runs"]))
        benchmark.validate_schedule(schedule)
        self.assertEqual(198, len({run["runId"] for run in schedule["runs"]}))
        self.assertEqual([0, 4, 9], schedule["referenceBlockIndices"])
        self.assertEqual(10, schedule["primaryBlocks"])
        self.assertEqual(3, schedule["referenceBlocks"])
        for cell in schedule["cells"]:
            for mode in cell["modes"]:
                runs = [r for r in schedule["runs"] if r["cellId"] == cell["cellId"] and r["mode"] == mode]
                expected_blocks = [0, 4, 9] if mode in benchmark.REFERENCE_MODES else list(range(10))
                self.assertEqual(expected_blocks, [r["block"] for r in runs])
                self.assertEqual(len(expected_blocks), cell["processesPerMode"][mode])
                if mode not in benchmark.REFERENCE_MODES:
                    positions = [0] * 3
                    for block in range(10):
                        order = [r["mode"] for r in schedule["runs"] if r["cellId"] == cell["cellId"]
                                 and r["block"] == block and r["mode"] not in benchmark.REFERENCE_MODES]
                        positions[order.index(mode)] += 1
                    self.assertLessEqual(max(positions) - min(positions), 1)

    def test_original_ten_reference_design_remains_available(self):
        schedule = benchmark.make_schedule(reference_blocks=10)
        benchmark.validate_schedule(schedule)
        self.assertEqual(240, len(schedule["runs"]))
        for cell in schedule["cells"]:
            for mode in cell["modes"]:
                positions = [0] * 4
                for block in range(10):
                    order = [r["mode"] for r in schedule["runs"] if r["cellId"] == cell["cellId"] and r["block"] == block]
                    positions[order.index(mode)] += 1
                self.assertLessEqual(max(positions) - min(positions), 1)

    def test_optional_quickpack_and_companion(self):
        schedule = benchmark.make_schedule(quickpack_cells=["26.1.2-fabric"], companion_workloads=["small"])
        benchmark.validate_schedule(schedule)
        self.assertEqual(402, len(schedule["runs"]))
        self.assertEqual(6, sum(r["mode"] == "quickpack" for r in schedule["runs"]))

    def test_reference_count_cannot_drop_required_modes(self):
        for count in (0, 11, True, "3", None):
            with self.subTest(count=count), self.assertRaises(benchmark.Invalid):
                benchmark.make_schedule(reference_blocks=count)

    def test_incomplete_schedule_rejected(self):
        schedule = benchmark.make_schedule()
        schedule["runs"].pop()
        with self.assertRaises(benchmark.Invalid):
            benchmark.validate_schedule(schedule)


class ProcessTests(unittest.TestCase):
    def setUp(self):
        self.run = benchmark.make_schedule()["runs"][0]
        self.manifest, self.events = fixture(self.run)

    def validate(self):
        return benchmark.validate_process(self.manifest, self.events, self.run)

    def test_complete_neutral_fixture(self):
        samples = self.validate()
        self.assertEqual(5, len(samples["measuredReloadsMs"]))
        self.assertGreater(samples["startupMs"], 0)

    def test_priming_is_excluded(self):
        self.events[1]["nanoTime"] -= 1000000
        result = self.validate()
        self.assertEqual(1, len(set(result["measuredReloadsMs"])))

    def test_reject_bad_event_sequences(self):
        for change in (lambda events: events.pop(),
                       lambda events: events.append(events[-1]),
                       lambda events: events[3].update(event="error"),
                       lambda events: events[3].update(event="cancelled"),
                       lambda events: events[3].update(event="ready"),
                       lambda events: events[3].update(reloadIndex=2),
                       lambda events: events[3].update(sessionId="other"),
                       lambda events: events[3].update(nanoTime=0),
                       lambda events: events[3].update(epochMillis=0),
                       lambda events: events[3].update(jvmUptimeMillis=0),
                       lambda events: events[3].update(schema=True),
                       lambda events: events[3].update(nanoTime=True),
                       lambda events: events[3].pop("nanoTime")):
            with self.subTest(change=change):
                events = copy.deepcopy(self.events)
                change(events)
                with self.assertRaises(benchmark.Invalid):
                    benchmark.validate_process(self.manifest, events, self.run)

    def test_reject_inaccurate_manifest(self):
        for change in (lambda m: m.update(exitCode=1),
                       lambda m: m.update(exitCode=None),
                       lambda m: m.update(pid=0),
                       lambda m: m.update(processStartedEpochMillis=999999999),
                       lambda m: m["context"].update(profilingEnabled=True),
                       lambda m: m["context"]["orderedPacks"][0].update(sha256="not hash")):
            manifest = copy.deepcopy(self.manifest)
            change(manifest)
            with self.assertRaises(benchmark.Invalid):
                benchmark.validate_process(manifest, self.events, self.run)

    def test_runner_attestations_required_even_with_zero_exit(self):
        for key, required in (("valid", True), ("inputsUnchanged", True), ("timeout", False)):
            for invalid in (not required, None, int(required), str(required)):
                with self.subTest(key=key, invalid=invalid):
                    manifest = copy.deepcopy(self.manifest)
                    manifest[key] = invalid
                    self.assertEqual(0, manifest["exitCode"])
                    with self.assertRaises(benchmark.Invalid):
                        benchmark.validate_process(manifest, self.events, self.run)
            with self.subTest(key=key, missing=True):
                manifest = copy.deepcopy(self.manifest)
                del manifest[key]
                with self.assertRaises(benchmark.Invalid):
                    benchmark.validate_process(manifest, self.events, self.run)

    def test_every_phase_requires_active_window_boolean(self):
        for index in range(len(self.events)):
            for invalid in (False, None, 1, "true"):
                with self.subTest(index=index, invalid=invalid):
                    events = copy.deepcopy(self.events)
                    events[index]["windowActive"] = invalid
                    with self.assertRaises(benchmark.Invalid):
                        benchmark.validate_process(self.manifest, events, self.run)
            with self.subTest(index=index, missing=True):
                events = copy.deepcopy(self.events)
                del events[index]["windowActive"]
                with self.assertRaises(benchmark.Invalid):
                    benchmark.validate_process(self.manifest, events, self.run)

    def test_controlled_runtime_environment_is_required(self):
        valid = self.manifest["context"]["runtimeEnvironment"]
        for invalid in (None, {}, {"TEMP": valid["TEMP"]}, {"TMP": valid["TMP"]},
                        {**valid, "PATH": "uncontrolled"}, {**valid, "TEMP": False},
                        {"TEMP": ".gradle/modernization-temp", "TMP": ".gradle/modernization-temp"},
                        {**valid, "TMP": str(benchmark.RUNTIME_TEMP_ROOT.parent / "different")},
                        {"TEMP": str(benchmark.RUNTIME_TEMP_ROOT.parent), "TMP": str(benchmark.RUNTIME_TEMP_ROOT.parent)}):
            with self.subTest(invalid=invalid):
                manifest = copy.deepcopy(self.manifest)
                manifest["context"]["runtimeEnvironment"] = invalid
                with self.assertRaises(benchmark.Invalid):
                    benchmark.validate_process(manifest, self.events, self.run)
        manifest = copy.deepcopy(self.manifest)
        del manifest["context"]["runtimeEnvironment"]
        with self.assertRaises(benchmark.Invalid):
            benchmark.validate_process(manifest, self.events, self.run)

    def test_dimensions_required_on_every_event(self):
        for index in range(len(self.events)):
            for key, invalid in (("framebufferWidth", None), ("framebufferHeight", None),
                                 ("framebufferWidth", 1280.0), ("framebufferHeight", "720"),
                                 ("framebufferWidth", 800), ("framebufferHeight", 600)):
                with self.subTest(index=index, key=key, invalid=invalid):
                    events = copy.deepcopy(self.events)
                    if invalid is None:
                        del events[index][key]
                    else:
                        events[index][key] = invalid
                    with self.assertRaises(benchmark.Invalid):
                        benchmark.validate_process(self.manifest, events, self.run)

    def test_active_primary_pack_required_and_stack_stable(self):
        for index in [0] + [3 + 3 * i for i in range(6)]:
            for invalid in (None, [], ["vanilla"], ["vanilla", "file/DevelopRP-Full-1.9.5.zip"],
                            ["vanilla", "file/DevelopRP-Full-1.9.5.zip", "file/DevelopRP-Full-1.9.5.zip"]):
                with self.subTest(index=index, invalid=invalid):
                    events = copy.deepcopy(self.events)
                    if invalid is None:
                        del events[index]["activePackIds"]
                    else:
                        events[index]["activePackIds"] = invalid
                    with self.assertRaises(benchmark.Invalid):
                        benchmark.validate_process(self.manifest, events, self.run)

    def test_already_prefixed_file_id_is_not_doubled(self):
        self.manifest["context"]["orderedPacks"][0]["id"] = "file/DevelopRP-Full-1.9.5.zip"
        self.validate()

    def test_complete_options_snapshot_is_required(self):
        for invalid in (False, None, 1, "true"):
            with self.subTest(invalid=invalid):
                manifest = copy.deepcopy(self.manifest)
                manifest["context"]["optionsSnapshotPinned"] = invalid
                with self.assertRaises(benchmark.Invalid):
                    benchmark.validate_process(manifest, self.events, self.run)
        for key in ("optionsSnapshotPinned", "effectiveOptions"):
            manifest = copy.deepcopy(self.manifest)
            del manifest["context"][key]
            with self.assertRaises(benchmark.Invalid):
                benchmark.validate_process(manifest, self.events, self.run)
        for invalid in ({}, {"lang": "en_us"}, {"lang": 1}):
            manifest = copy.deepcopy(self.manifest)
            manifest["context"]["effectiveOptions"] = invalid
            with self.assertRaises(benchmark.Invalid):
                benchmark.validate_process(manifest, self.events, self.run)
        for key in self.manifest["context"]["effectiveOptions"]:
            manifest = copy.deepcopy(self.manifest)
            del manifest["context"]["effectiveOptions"][key]
            with self.assertRaises(benchmark.Invalid):
                benchmark.validate_process(manifest, self.events, self.run)
        manifest = copy.deepcopy(self.manifest)
        manifest["context"]["configs"] = []
        with self.assertRaises(benchmark.Invalid):
            benchmark.validate_process(manifest, self.events, self.run)


class AnalysisTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.schedule = benchmark.make_schedule()
        self.paths = []
        for run in self.schedule["runs"]:
            manifest, events = fixture(run)
            path = self.root / (run["runId"] + ".json")
            path.write_text(json.dumps(manifest))
            (self.root / manifest["eventsFile"]).write_text("\n".join(json.dumps(x) for x in events))
            self.paths.append(path)

    def analyze(self):
        return benchmark.analyze(self.schedule, self.paths, iterations=100)

    def test_complete_schedule_pass_and_process_sample_count(self):
        report = self.analyze()
        self.assertTrue(report["passed"])
        self.assertEqual(6, len(report["cells"]))
        for cell in report["cells"]:
            reference = cell["modeStatistics"]["vanilla"]
            self.assertEqual(3, reference["validProcessCount"])
            self.assertEqual(3, reference["expectedProcessCount"])
            self.assertTrue(reference["complete"])
            self.assertNotIn("p95Ms", reference["reloadMs"])
            self.assertEqual(10, cell["modeStatistics"]["corrected"]["validProcessCount"])
            for metric in cell["comparisons"].values():
                self.assertEqual(10, metric["samplesPerMode"])
                self.assertAlmostEqual(20, metric["improvementPercent"])
        self.assertEqual(report, self.analyze())
        self.assertIn("PASS", benchmark.markdown(report))

    def test_missing_process_invalidates_cell(self):
        self.paths.pop()
        report = self.analyze()
        self.assertFalse(report["passed"])
        self.assertTrue(any(cell["problems"] for cell in report["cells"]))

    def test_missing_one_reference_still_invalidates_cell(self):
        self.paths = [p for p in self.paths if p.name != "26.1.2-fabric-developrp-vanilla-04.json"]
        report = self.analyze()
        self.assertFalse(report["passed"])
        cell = next(c for c in report["cells"] if c["cellId"] == "26.1.2-fabric-developrp")
        self.assertFalse(cell["passed"])
        self.assertEqual(2, cell["modeStatistics"]["vanilla"]["validProcessCount"])
        self.assertFalse(cell["modeStatistics"]["vanilla"]["complete"])

    def test_duplicates_never_replace_failure(self):
        self.paths.append(self.paths[0])
        report = self.analyze()
        self.assertFalse(report["passed"])
        self.assertIn("duplicate", report["failures"][0]["error"])

    def test_malformed_preserved(self):
        self.paths[0].write_text("{")
        report = self.analyze()
        self.assertFalse(report["passed"])
        self.assertEqual(1, len(report["failures"]))

    def test_mixed_hashes_rejected(self):
        for key in ("orderedPacks", "artifacts"):
            with self.subTest(key=key):
                old = self.paths[0].read_text()
                manifest = json.loads(old)
                manifest["context"][key][0]["sha256"] = "f" * 64
                self.paths[0].write_text(json.dumps(manifest))
                report = self.analyze()
                self.assertFalse(report["passed"])
                self.assertTrue(any(c["problems"] for c in report["cells"]))
                self.paths[0].write_text(old)

    def test_meaningful_jvm_changes_are_not_normalized_away(self):
        old = self.paths[0].read_text()
        for arguments in (["-Xmx8G"], ["-Xmx4G", "-Djava.io.tmpdir=different"],
                          ["-Xmx4G", "-cp", "different-client.jar"]):
            with self.subTest(arguments=arguments):
                manifest = json.loads(old)
                manifest["context"]["jvmArgs"] = arguments
                self.paths[0].write_text(json.dumps(manifest))
                report = self.analyze()
                self.assertFalse(report["passed"])
                self.assertTrue(any(c["problems"] for c in report["cells"]))
        self.paths[0].write_text(old)

    def test_all_effective_options_are_compared_without_ignore_lists(self):
        old = self.paths[0].read_text()
        for key, value in (("lang", "th_th"), ("forceUnicodeFont", "true"), ("textureFiltering", "1"),
                           ("mipmapLevels", "2"), ("key_generated_loader_binding", "key.keyboard.x")):
            with self.subTest(key=key):
                manifest = json.loads(old)
                manifest["context"]["effectiveOptions"][key] = value
                self.paths[0].write_text(json.dumps(manifest))
                report = self.analyze()
                self.assertFalse(report["passed"])
                self.assertTrue(any(c["problems"] for c in report["cells"]))
        self.paths[0].write_text(old)

    def test_no_averaging_failing_loader(self):
        for path in self.paths:
            manifest = json.loads(path.read_text())
            if manifest["mode"] == "candidate" and manifest["context"]["loader"] == "forge":
                run = next(r for r in self.schedule["runs"] if r["runId"] == manifest["runId"])
                _, events = fixture(run, improvement=.1)
                (self.root / manifest["eventsFile"]).write_text("\n".join(json.dumps(x) for x in events))
        report = self.analyze()
        self.assertFalse(report["passed"])
        self.assertEqual(2, sum(not c["passed"] for c in report["cells"]))

    def test_optimizer_pack_differences_allowed_only_below_primary(self):
        for path in self.paths:
            manifest = json.loads(path.read_text())
            if manifest["mode"] in ("current", "corrected", "candidate"):
                event_path = self.root / manifest["eventsFile"]
                events = [json.loads(line) for line in event_path.read_text().splitlines()]
                for event in events:
                    if "activePackIds" in event:
                        event["activePackIds"].insert(2, "mod:packforge")
                event_path.write_text("\n".join(json.dumps(e) for e in events))
        report = self.analyze()
        self.assertTrue(report["passed"])
        self.assertEqual(["mod:packforge"], report["cells"][0]["activePackComparison"]["differencesFromVanilla"]["candidate"]["added"])
        for path in self.paths:
            manifest = json.loads(path.read_text())
            if manifest["mode"] in ("current", "corrected", "candidate"):
                event_path = self.root / manifest["eventsFile"]
                events = [json.loads(line) for line in event_path.read_text().splitlines()]
                for event in events:
                    if "activePackIds" in event:
                        event["activePackIds"].remove("mod:packforge")
                        event["activePackIds"].append("mod:packforge")
                event_path.write_text("\n".join(json.dumps(e) for e in events))
        self.assertFalse(self.analyze()["passed"])

    def test_primary_precedence_or_unrecognized_builtin_change_fails(self):
        path = self.paths[0]
        manifest = json.loads(path.read_text())
        event_path = self.root / manifest["eventsFile"]
        original = event_path.read_text()
        for stack in (["loader_resources", "vanilla", "file/DevelopRP-Full-1.9.5.zip"],
                      ["vanilla", "file/DevelopRP-Full-1.9.5.zip", "loader_resources"],
                      ["vanilla", "loader_resources", "unknown_builtin", "file/DevelopRP-Full-1.9.5.zip"]):
            with self.subTest(stack=stack):
                events = [json.loads(line) for line in original.splitlines()]
                for event in events:
                    if "activePackIds" in event:
                        event["activePackIds"] = stack
                event_path.write_text("\n".join(json.dumps(e) for e in events))
                report = self.analyze()
                self.assertFalse(report["passed"])
                self.assertTrue(any(c["problems"] for c in report["cells"]))

    def test_bad_json_duplicate_keys(self):
        self.paths[0].write_text('{"schema":1,"schema":2}')
        self.assertIn("duplicate JSON key", self.analyze()["failures"][0]["error"])

    def test_duplicate_event_json_keys_rejected(self):
        manifest = json.loads(self.paths[0].read_text())
        event_path = self.root / manifest["eventsFile"]
        event_path.write_text(event_path.read_text().replace('"schema": 1', '"schema": 1, "schema": 1', 1))
        self.assertIn("duplicate JSON key", self.analyze()["failures"][0]["error"])

    def test_reused_session_rejected(self):
        first = json.loads(self.paths[0].read_text())
        second = json.loads(self.paths[1].read_text())
        second["sessionId"] = first["sessionId"]
        self.paths[1].write_text(json.dumps(second))
        report = self.analyze()
        self.assertFalse(report["passed"])
        self.assertTrue(report["failures"])


class StatisticsTests(unittest.TestCase):
    def test_pairing_determinism_and_outliers_preserved(self):
        base = [100 + x for x in range(9)] + [10000]
        candidate = [x * .8 for x in base]
        result = benchmark.comparison(base, candidate, 7, iterations=500)
        self.assertEqual(result, benchmark.comparison(base, candidate, 7, iterations=500))
        self.assertGreater(result["baselineP95Ms"], 1000)
        self.assertAlmostEqual(20, result["improvementCi95Percent"][0])
        self.assertEqual(10, result["samplesPerMode"])

    def test_insufficient_samples_rejected(self):
        with self.assertRaises(benchmark.Invalid):
            benchmark.comparison([1] * 9, [1] * 9, 1)


if __name__ == "__main__":
    unittest.main()
