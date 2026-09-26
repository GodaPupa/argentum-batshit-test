#!/usr/bin/env python3
"""Preserve post-run dependency identities from real Gradle test-worker arguments.

This is read-only with respect to build inputs, outputs, and caches. It neither
launches Gradle nor admits test results. A post-run hash is an observation at the
recorded time, not proof that no file changed between execution and observation.
"""

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shutil


def sha(data):
    return hashlib.sha256(data).hexdigest()


def stamp():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--attempt", type=Path, required=True)
    parser.add_argument("--gradle-home", type=Path, default=Path.home() / ".gradle")
    args = parser.parse_args()
    root, attempt = args.root.resolve(), args.attempt.resolve()
    gradle = args.gradle_home.resolve()
    receipt_path = attempt / "receipt.json"
    receipt = json.loads(receipt_path.read_text())
    if receipt["status"] == "RUNNING":
        raise RuntimeError("Only a finalized attempt can receive a post-run snapshot")
    destination = attempt / "runtime-classpath-observation"
    destination.mkdir(exist_ok=False)
    shutil.copyfile(Path(__file__), destination / "recorder.py")
    result = {
        "schema": "ferocity-post-run-runtime-classpath-observation-v1",
        "observed_at_utc": stamp(), "receipt_sha256": sha(receipt_path.read_bytes()),
        "qualification": "Post-run identity observation only; original test receipt and acceptance are unchanged.",
        "stages": [], "research_games": 0,
    }

    def identify(path):
        path = path.resolve()
        for base, kind in ((root, "repository"), (gradle, "gradle_home")):
            if path.is_relative_to(base):
                return {"kind": kind, "path": str(path.relative_to(base))}
        raise RuntimeError(f"Unrecognized classpath location; do not publish automatically: {path.name}")

    try:
        stages = receipt.get("stages")
        if stages is None:
            stages = [{"module": item["module"], "started_at_utc": receipt["started_at_utc"], "finished_at_utc": receipt.get("command_finished_at_utc", receipt["finished_at_utc"])} for item in receipt["module_results"] if item["test_task_executed"]]
            result["full_suite_marker_scope_limit"] = "Only modules with an execution marker are searched; missing worker arguments remain explicitly unobserved. This never changes an original freshness assessment."
        for index, stage in enumerate(stages, 1):
            start = dt.datetime.fromisoformat(stage["started_at_utc"]).timestamp()
            end = dt.datetime.fromisoformat(stage["finished_at_utc"]).timestamp()
            expected = root / stage["module"] / "build/classes/kotlin/test"
            matching = []
            for file in (gradle / ".tmp").glob("gradle-worker-classpath*"):
                if not start <= file.stat().st_mtime <= end:
                    continue
                lines = file.read_text().splitlines()
                if len(lines) != 2 or lines[0] != "-cp":
                    continue
                entries = [Path(name) for name in lines[1].split(os.pathsep)]
                if expected in entries:
                    matching.append((file, entries))
            observation = {"module": stage["module"], "stage_index": index, "matching_workers": []}
            result["stages"].append(observation)
            for worker_index, (file, entries) in enumerate(sorted(matching), 1):
                worker = {
                    "argument_file_sha256": sha(file.read_bytes()),
                    "argument_file_mtime_utc": dt.datetime.fromtimestamp(file.stat().st_mtime, dt.timezone.utc).isoformat(),
                    "classpath": [],
                }
                observation["matching_workers"].append(worker)
                for entry in entries:
                    entry = entry.resolve()
                    identity = identify(entry)
                    if entry.is_file():
                        data = entry.read_bytes()
                        identity.update(type="file", sha256=sha(data), bytes=len(data))
                    elif entry.is_dir():
                        members = []
                        for member in sorted(entry.rglob("*")):
                            if member.is_file():
                                if not member.resolve().is_relative_to(entry):
                                    raise RuntimeError("A directory member escapes its classpath root; observation aborted")
                                data = member.read_bytes()
                                members.append({"path": str(member.relative_to(entry)), "sha256": sha(data), "bytes": len(data)})
                        identity.update(type="directory", members=members)
                    else:
                        identity.update(type="absent_at_observation")
                    worker["classpath"].append(identity)
            observation["status"] = "OBSERVED" if matching else "WORKER_ARGUMENTS_UNAVAILABLE"
        result["status"] = "OBSERVED" if result["stages"] and all(s["status"] == "OBSERVED" for s in result["stages"]) else "INCOMPLETE"
    except Exception as exc:
        result["status"] = "FAILED_OBSERVATION"
        result["error"] = {"type": type(exc).__name__, "message": str(exc)}
    finally:
        result["finished_at_utc"] = stamp()
        (destination / "manifest.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"status": result["status"], "manifest": str(destination / "manifest.json")}))
    return 0 if result["status"] == "OBSERVED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
