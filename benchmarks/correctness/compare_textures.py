"""Strict diagnostic texture equivalence; independent of timing analysis and optimizer code."""
import argparse
import collections
import json
from pathlib import Path


def read_jsonl(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"duplicate JSON key {key}")
            result[key] = value
        return result
    with Path(path).open(encoding="utf-8") as stream:
        return [json.loads(line, object_pairs_hook=unique) for line in stream if line.strip()]


def integer(row, field, minimum=0):
    value = row.get(field)
    if type(value) is not int or value < minimum:
        raise ValueError(f"invalid {field}")
    return value


def one(rows, name):
    found = [row for row in rows if row.get("event") == name]
    if len(found) != 1:
        raise ValueError(f"missing/duplicate {name}")
    return found[0]


def validate_observer(events, session, generations):
    if not events or any(row.get("event") == "error" for row in events):
        raise ValueError("missing observer events or observer error")
    if any(row.get("sessionId") != session or row.get("schema") != 1
           or row.get("observerProtocol") not in (2, 3) or row.get("discoveryOnly") is not False for row in events):
        raise ValueError("observer session/protocol mismatch or discovery run")
    if len({row["observerProtocol"] for row in events}) != 1:
        raise ValueError("mixed observer protocols")
    ready, complete = one(events, "ready"), one(events, "complete")
    if ready.get("reloadIndex") != -1:
        raise ValueError("invalid initial readiness")
    if (ready.get("resourceGeneration") not in generations
            or ready.get("renderedResourceGeneration") != ready.get("resourceGeneration")
            or ready.get("activeResourceReloads") != 0 or ready.get("windowActive") is not True):
        raise ValueError("initial menu has no completed generation proof")
    validate_input_proof(ready, "menu")
    scenarios = {row.get("scenario") for row in events}
    if len(scenarios) != 1 or not scenarios <= {"menu", "inworld"}:
        raise ValueError("invalid/mixed observer scenarios")
    for kind in ("resource_reload_started", "resource_reload_complete"):
        ids = [integer(row, "resourceGeneration", 1) for row in events if row.get("event") == kind]
        if sorted(ids) != list(generations):
            raise ValueError("capture/observer resource generation mismatch")
    requests = [row for row in events if row.get("event") == "reload_requested"]
    completions = [row for row in events if row.get("event") == "reload_complete"]
    frames = [row for row in events if row.get("event") == "frame_ready"]
    if not requests or len(requests) != len(completions) or len(requests) != len(frames):
        raise ValueError("incomplete observer reload triplets")
    if scenarios == {"inworld"}:
        world_ready = one(events, "world_ready")
        if (not ready["nanoTime"] <= world_ready["nanoTime"] < requests[0]["nanoTime"]
                or world_ready.get("worldRendered") is not True):
            raise ValueError("missing in-world scene readiness")
        validate_input_proof(world_ready, "inworld")
    last = integer(ready, "nanoTime")
    for index, (request, completion, frame) in enumerate(zip(requests, completions, frames)):
        if any(row.get("reloadIndex") != index for row in (request, completion, frame)):
            raise ValueError("noncontiguous reload indices")
        if not last < request["nanoTime"] <= completion["nanoTime"] <= frame["nanoTime"]:
            raise ValueError("invalid observer time order")
        generation = integer(frame, "resourceGeneration", 1)
        if completion.get("resourceGeneration") != generation or generation not in generations:
            raise ValueError("frame/completion generation mismatch")
        started = [row for row in events if row.get("event") == "resource_reload_started"
                   and request["nanoTime"] <= row["nanoTime"] <= frame["nanoTime"]]
        if len(started) != 1 or started[0]["resourceGeneration"] != generation:
            raise ValueError("nested, extra, or missing resource reload")
        if frame.get("renderedResourceGeneration") != generation or frame.get("activeResourceReloads") != 0:
            raise ValueError("frame has no completed generation proof")
        if frame.get("requestResourceGenerations") != 1 or frame.get("windowActive") is not True:
            raise ValueError("invalid focus or generation count")
        if scenarios == {"inworld"} and frame.get("worldRendered") is not True:
            raise ValueError("missing in-world rendering proof")
        validate_input_proof(frame, next(iter(scenarios)))
        last = frame["nanoTime"]
    if complete.get("reloadIndex") != len(requests) - 1 or complete["nanoTime"] < last:
        raise ValueError("invalid observer terminal index/time")
    return next(iter(scenarios))


def validate_input_proof(row, scenario):
    if row["observerProtocol"] != 3:
        return
    field = "worldInputDispatchGeneration" if scenario == "inworld" else "menuInputDispatchGeneration"
    rendered = "worldRendered" if scenario == "inworld" else "menuRendered"
    if (row.get(field) != row.get("resourceGeneration") or row.get(rendered) is not True
            or row.get("inputProbeChangedActivity") is not False
            or type(row.get("overlayPresent")) is not bool
            or row.get("titleFading") is not False):
        raise ValueError("missing actual input dispatch proof")


def normalized(capture_path, events_path):
    records = read_jsonl(capture_path)
    if not records:
        raise ValueError("zero capture coverage")
    terminal = records[-1]
    if terminal.get("event") != "terminal" or terminal.get("valid") is not True or terminal.get("reason") is not None:
        raise ValueError("missing valid terminal capture record")
    session = terminal.get("sessionId")
    if not isinstance(session, str) or not session:
        raise ValueError("missing session")
    if any(row.get("schema") != 2 or row.get("sessionId") != session for row in records):
        raise ValueError("capture session/schema mismatch")
    records = records[:-1]
    if integer(terminal, "records", 1) != len(records):
        raise ValueError("capture record count mismatch")
    generation_ids = range(1, integer(terminal, "generations", 1) + 1)
    scenario = validate_observer(read_jsonl(events_path), session, generation_ids)
    by_generation = collections.defaultdict(list)
    by_atlas = collections.defaultdict(list)
    captures = collections.defaultdict(list)
    common = {"schema", "sessionId", "event", "resourceGeneration", "atlasIndex", "atlasId", "nanoTime"}
    pixels = {"captureIndex", "spriteIndex", "stage", "resourceId", "spriteId", "frameWidth", "frameHeight",
              "animated", "requestedLevel", "level", "width", "height", "sha256", "hashNanos"}
    fields = {"generation_started": set(), "generation_complete": {"successful"}, "atlas_started": set(),
              "atlas_complete": {"successful"}, "atlas_uploaded": {"sprites"}, "pixels": pixels}
    for row in records:
        event = row.get("event")
        if event not in fields or set(row) != common | fields[event]:
            raise ValueError("unknown or missing capture fields")
        generation = integer(row, "resourceGeneration", 1)
        if generation not in generation_ids:
            raise ValueError("unknown resource generation")
        integer(row, "nanoTime")
        by_generation[generation].append(row)
        if event.startswith("generation_"):
            if row["atlasIndex"] != 0 or row["atlasId"] is not None:
                raise ValueError("generation record has atlas ownership")
        else:
            atlas = integer(row, "atlasIndex", 1)
            if not isinstance(row["atlasId"], str) or not row["atlasId"]:
                raise ValueError("missing atlas identity")
            by_atlas[atlas].append(row)
        if event == "pixels":
            for field in ("captureIndex", "requestedLevel", "level", "hashNanos"):
                integer(row, field)
            for field in ("spriteIndex", "width", "height", "frameWidth", "frameHeight"):
                integer(row, field, 1)
            if type(row["animated"]) is not bool or row["stage"] not in {"decoded", "mip_ready", "upload_input"}:
                raise ValueError("invalid pixel stage/animation")
            if any(not isinstance(row[field], str) or not row[field] for field in ("resourceId", "spriteId")):
                raise ValueError("missing resource identity")
            digest = row["sha256"]
            if not isinstance(digest, str) or len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
                raise ValueError("invalid pixel digest")
            captures[row["captureIndex"]].append(row)
    if sorted(by_generation) != list(generation_ids):
        raise ValueError("missing generation")
    for rows in by_generation.values():
        start, end = one(rows, "generation_started"), one(rows, "generation_complete")
        if end.get("successful") is not True or start["nanoTime"] > end["nanoTime"]:
            raise ValueError("failed or invalid resource generation")
        if any(row["event"] == "pixels" and not start["nanoTime"] <= row["nanoTime"] <= end["nanoTime"] for row in rows):
            raise ValueError("stale pixel capture outside owned generation")
    if sorted(by_atlas) != list(range(1, integer(terminal, "atlases", 1) + 1)):
        raise ValueError("missing atlas invocation")
    if sorted(captures) != list(range(integer(terminal, "captures", 1))):
        raise ValueError("missing capture invocation")

    chains = {}
    sprites = collections.defaultdict(list)
    for index, rows in captures.items():
        rows.sort(key=lambda row: row["level"])
        first = rows[0]
        same = ("resourceGeneration", "atlasIndex", "atlasId", "spriteIndex", "stage", "resourceId",
                "spriteId", "requestedLevel", "frameWidth", "frameHeight", "animated")
        if any(any(row[field] != first[field] for field in same) for row in rows):
            raise ValueError("mixed capture identity")
        if [row["level"] for row in rows] != list(range(len(rows))) or len(rows) <= first["requestedLevel"]:
            raise ValueError("incomplete or duplicate mip chain")
        if first["stage"] == "decoded" and (len(rows) != 1 or first["requestedLevel"] != 0):
            raise ValueError("decoded capture contains mips")
        chain = tuple((row["level"], row["width"], row["height"], row["sha256"]) for row in rows)
        chains[index] = (first, chain)
        sprites[first["spriteIndex"]].append((first, chain))
    for groups in sprites.values():
        decoded = [(row, chain) for row, chain in groups if row["stage"] == "decoded"]
        if len(decoded) != 1:
            raise ValueError("missing/duplicate decoded sprite")
        origin = decoded[0][0]
        same = ("resourceGeneration", "atlasIndex", "atlasId", "spriteId", "frameWidth", "frameHeight", "animated")
        if any(any(row[field] != origin[field] for field in same) for row, _ in groups):
            raise ValueError("sprite ownership collision")
        mip_groups = [(row, chain) for row, chain in groups if row["stage"] == "mip_ready"]
        for row, chain in groups:
            if row["stage"] == "upload_input":
                if not any(mip["nanoTime"] <= row["nanoTime"] and mip_chain == chain for mip, mip_chain in mip_groups):
                    raise ValueError("upload has no matching preceding completed mip chain")

    result = collections.defaultdict(list)
    for index, rows in by_atlas.items():
        start, end, uploaded = one(rows, "atlas_started"), one(rows, "atlas_complete"), one(rows, "atlas_uploaded")
        if end.get("successful") is not True:
            raise ValueError("failed atlas")
        if any((row["resourceGeneration"], row["atlasId"]) != (start["resourceGeneration"], start["atlasId"]) for row in rows):
            raise ValueError("atlas ownership collision")
        atlas_chains = [(row, chain) for row, chain in chains.values() if row["atlasIndex"] == index]
        uploads = [(row, chain) for row, chain in atlas_chains if row["stage"] == "upload_input"]
        if len(uploads) != integer(uploaded, "sprites", 1):
            raise ValueError("missing atlas upload coverage")
        if len({row["resourceId"] for row, _ in uploads}) != len(uploads):
            raise ValueError("duplicate atlas resource ID")
        if any(row["nanoTime"] < start["nanoTime"] for row, _ in atlas_chains):
            raise ValueError("capture before owned atlas started")
        canonical = sorted((row["stage"], row["resourceId"], row["spriteId"], row["frameWidth"], row["frameHeight"],
                            row["animated"], row["requestedLevel"], chain) for row, chain in atlas_chains)
        result[start["resourceGeneration"]].append((start["atlasId"], tuple(canonical)))
    if sorted(result) != list(generation_ids):
        raise ValueError("zero atlas coverage in resource generation")
    return {"scenario": scenario, "generations": {key: sorted(value) for key, value in result.items()}}


def compare(control_capture, control_events, candidate_capture, candidate_events):
    control = normalized(control_capture, control_events)
    candidate = normalized(candidate_capture, candidate_events)
    if control != candidate:
        raise ValueError("exact decoded/mip/upload pixels, atlas identities, or generation coverage differ")
    return {"valid": True, "scenario": control["scenario"],
            "resourceGenerations": len(control["generations"]),
            "atlasInvocations": sum(map(len, control["generations"].values()))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("control_capture")
    parser.add_argument("control_events")
    parser.add_argument("candidate_capture")
    parser.add_argument("candidate_events")
    args = parser.parse_args()
    try:
        print(json.dumps(compare(args.control_capture, args.control_events, args.candidate_capture, args.candidate_events)))
    except (ValueError, KeyError, TypeError, OSError) as failure:
        raise SystemExit(f"FAIL: {failure}") from failure


if __name__ == "__main__":
    main()
