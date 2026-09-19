import copy
import json
from pathlib import Path
import random
import tempfile
import unittest

from compare_textures import compare, normalized, read_jsonl


def fixture(scenario="menu"):
    records, events = [], []
    def event(kind, when, generation, index=-1, **fields):
        return dict(schema=1, observerProtocol=2, sessionId="test", discoveryOnly=False, scenario=scenario,
                    event=kind, nanoTime=when, resourceGeneration=generation, reloadIndex=index, **fields)
    for generation in (1, 2):
        offset = (generation - 1) * 200
        def record(kind, when, atlas=True, **fields):
            return dict(schema=2, sessionId="test", event=kind, resourceGeneration=generation,
                        atlasIndex=generation if atlas else 0, atlasId="minecraft:blocks" if atlas else None,
                        nanoTime=offset + when, **fields)
        records.append(record("generation_started", 10, False))
        records.append(record("atlas_started", 20))
        for capture, (stage, when, levels) in enumerate((("decoded", 30, 1), ("mip_ready", 40, 2), ("upload_input", 50, 2))):
            for level in range(levels):
                records.append(record("pixels", when, captureIndex=(generation - 1) * 3 + capture,
                                      spriteIndex=generation, stage=stage, resourceId="minecraft:test",
                                      spriteId="minecraft:test", frameWidth=2, frameHeight=2, animated=True,
                                      requestedLevel=levels - 1, level=level, width=2 >> level, height=4 >> level,
                                      sha256=("a" if stage == "decoded" else "b") * 64, hashNanos=1))
        records.append(record("atlas_complete", 60, successful=True))
        records.append(record("atlas_uploaded", 70, sprites=1))
        records.append(record("generation_complete", 80, False, successful=True))
        events.extend([event("resource_reload_started", offset + 10, generation, generation - 2),
                       event("resource_reload_complete", offset + 80, generation, generation - 2)])
    events.extend([event("ready", 100, 1, renderedResourceGeneration=1, activeResourceReloads=0, windowActive=True), event("reload_requested", 200, 1, 0),
                   event("reload_complete", 290, 2, 0),
                   event("frame_ready", 300, 2, 0, renderedResourceGeneration=2, activeResourceReloads=0,
                         requestResourceGenerations=1, windowActive=True, worldRendered=scenario == "inworld"),
                   event("complete", 310, 2, 0)])
    if scenario == "inworld": events.append(event("world_ready", 150, 1, worldRendered=True))
    events.sort(key=lambda row: row["nanoTime"])
    records.append(dict(schema=2, sessionId="test", event="terminal", valid=True, reason=None,
                        captures=6, generations=2, atlases=2, records=len(records)))
    return records, events


class TextureComparisonTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)

    def write(self, records, events, stem="run"):
        paths = [Path(self.directory.name) / f"{stem}-{kind}.jsonl" for kind in ("pixels", "events")]
        for path, rows in zip(paths, (records, events)):
            path.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
        return paths

    def test_complete_menu_and_inworld_captures_compare(self):
        for scenario in ("menu", "inworld"):
            paths = self.write(*fixture(scenario))
            self.assertEqual(compare(*paths, *paths), dict(valid=True, scenario=scenario, resourceGenerations=2, atlasInvocations=2))

    def test_protocol_three_requires_input_ack_even_when_overlay_is_hidden(self):
        records, events = fixture()
        for row in events:
            row.update(observerProtocol=3, menuRendered=True, menuInputDispatchGeneration=row["resourceGeneration"],
                       inputProbeChangedActivity=False, overlayPresent=True, titleFading=False)
        paths = self.write(records, events)
        self.assertTrue(compare(*paths, *paths)["valid"])
        frame = next(row for row in events if row["event"] == "frame_ready")
        frame["menuInputDispatchGeneration"] = 1
        paths = self.write(records, events, "stale-input")
        with self.assertRaisesRegex(ValueError, "input dispatch"):
            compare(*paths, *paths)

    def test_protocol_three_probe_must_not_reset_activity_tracking(self):
        records, events = fixture()
        for row in events:
            row.update(observerProtocol=3, menuRendered=True, menuInputDispatchGeneration=row["resourceGeneration"],
                       inputProbeChangedActivity=True, overlayPresent=False, titleFading=False)
        paths = self.write(records, events)
        with self.assertRaisesRegex(ValueError, "input dispatch"):
            compare(*paths, *paths)

    def test_parallel_output_order_and_allocated_ids_do_not_change_equivalence(self):
        original = fixture()
        changed = copy.deepcopy(original)
        records = changed[0][:-1]
        for row in records:
            if row["atlasIndex"]: row["atlasIndex"] = 3 - row["atlasIndex"]
            if row["event"] == "pixels":
                row["captureIndex"] = 5 - row["captureIndex"]
                row["spriteIndex"] = 30 - row["spriteIndex"]
        random.Random(481).shuffle(records)
        changed[0][:] = records + [changed[0][-1]]
        self.assertTrue(compare(*self.write(*original, "control"), *self.write(*changed, "candidate"))["valid"])

    def test_same_sprite_id_in_different_atlases_remains_separate(self):
        original = fixture()
        changed = copy.deepcopy(original)
        for row in changed[0]:
            if row.get("atlasIndex") == 2: row["atlasId"] = "minecraft:items"
        with self.assertRaisesRegex(ValueError, "identities"):
            compare(*self.write(*original, "control"), *self.write(*changed, "candidate"))

    def test_identical_resource_ids_in_simultaneous_atlases_do_not_collapse(self):
        records, events = fixture()
        extra = []
        for row in records[:-1]:
            if not row["atlasIndex"]: continue
            added = dict(row, atlasIndex=row["atlasIndex"] + 2, atlasId="minecraft:items")
            if row["event"] == "pixels":
                added.update(captureIndex=row["captureIndex"] + 6, spriteIndex=row["spriteIndex"] + 2, sha256="c" * 64)
            extra.append(added)
        records[-1].update(captures=12, atlases=4, records=len(records) - 1 + len(extra))
        records[-1:-1] = extra
        paths = self.write(records, events, "control")
        self.assertEqual(compare(*paths, *paths)["atlasInvocations"], 4)
        changed = copy.deepcopy(records)
        for row in changed[:-1]:
            if row["atlasIndex"]:
                row["atlasId"] = "minecraft:items" if row["atlasId"] == "minecraft:blocks" else "minecraft:blocks"
        with self.assertRaises(ValueError): compare(*paths, *self.write(changed, events, "candidate"))

    def test_transparent_rgb_and_partial_alpha_digest_changes_are_not_normalized_away(self):
        for stage in ("decoded", "mip_ready", "upload_input"):
            original = fixture()
            changed = copy.deepcopy(original)
            for row in changed[0]:
                if row.get("stage") == stage: row["sha256"] = "c" * 64
            with self.assertRaises(ValueError):
                compare(*self.write(*original, "control"), *self.write(*changed, "candidate"))

    def test_missing_decoded_capture_cannot_pass_even_with_repaired_counters(self):
        records, events = fixture()
        records = [row for row in records if row.get("captureIndex") != 0]
        for row in records:
            if "captureIndex" in row: row["captureIndex"] -= 1
        records[-1].update(records=len(records) - 1, captures=5)
        with self.assertRaisesRegex(ValueError, "decoded"):
            normalized(*self.write(records, events))

    def test_missing_mip_level_cannot_pass(self):
        records, events = fixture()
        records = [row for row in records if not (row.get("captureIndex") == 1 and row.get("level") == 1)]
        records[-1]["records"] = len(records) - 1
        with self.assertRaisesRegex(ValueError, "mip chain"):
            normalized(*self.write(records, events))

    def test_missing_upload_or_generation_coverage_fails(self):
        for kind in ("atlas_uploaded", "atlas_complete", "generation_complete"):
            records, events = fixture()
            records = [row for row in records if not (row.get("event") == kind and row.get("resourceGeneration") == 2)]
            records[-1]["records"] = len(records) - 1
            with self.assertRaises(ValueError): normalized(*self.write(records, events))

    def test_cancelled_failed_and_stale_generation_fail(self):
        for mutation in ("failed", "stale", "invalid"):
            records, events = fixture()
            if mutation == "failed":
                next(row for row in records if row["event"] == "generation_complete")["successful"] = False
            elif mutation == "stale":
                next(row for row in records if row.get("stage") == "decoded")["nanoTime"] = 301
            else: records[-1].update(valid=False, reason="atlas preparation failed: CancellationException")
            with self.assertRaises(ValueError): normalized(*self.write(records, events))

    def test_observer_hidden_old_frame_extra_reload_and_protocol_one_fail(self):
        for mutation in ("old", "extra", "protocol", "world"):
            records, events = fixture("inworld")
            frame = next(row for row in events if row["event"] == "frame_ready")
            if mutation == "old": frame["renderedResourceGeneration"] = 1
            elif mutation == "extra": events.append(dict(next(row for row in events if row["event"] == "resource_reload_started")))
            elif mutation == "protocol": events[0]["observerProtocol"] = 1
            else: frame["worldRendered"] = False
            with self.assertRaises(ValueError): normalized(*self.write(records, events))

    def test_atlas_and_sprite_identity_collisions_fail(self):
        for field, value in (("atlasId", "minecraft:wrong"), ("spriteIndex", 10), ("resourceGeneration", 2)):
            records, events = fixture()
            next(row for row in records if row.get("stage") == "upload_input")[field] = value
            with self.assertRaises(ValueError): normalized(*self.write(records, events))

    def test_zero_capture_missing_terminal_and_boolean_counts_fail(self):
        records, events = fixture()
        for bad in ([], records[:-1], [dict(records[-1], records=0, captures=0)]):
            with self.assertRaises(ValueError): normalized(*self.write(bad, events))
        records[2]["width"] = True
        with self.assertRaises(ValueError): normalized(*self.write(records, events))

    def test_duplicate_json_keys_rejected(self):
        path = Path(self.directory.name) / "duplicate.jsonl"
        path.write_text('{"valid":false,"valid":true}\n', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "duplicate JSON key"): read_jsonl(path)


if __name__ == "__main__": unittest.main()
