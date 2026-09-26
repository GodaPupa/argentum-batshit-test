#!/usr/bin/env python3
"""Shared read-only observation/archive of completed Gradle test workers.

Extracted from independently reviewed Ferocity d097c9eff09dd402dfacb644351bfb9d62f35aff.
The caller supplies its own receipt, module and evidence schemas. Post-run bytes are
an observation, not proof that no byte changed between execution and observation.
No function builds, tests, initializes, allocates or admits experimental outcomes.
"""
import datetime as dt
import gzip
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tarfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def stamp():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def record_classpath(root, attempt, gradle, *, schema, recorder_source):
    """Observe one completed qualification; do not execute or admit anything."""
    root, attempt, gradle = root.resolve(), attempt.resolve(), gradle.resolve()
    receipt_path = attempt / "receipt.json"
    receipt = json.loads(receipt_path.read_text())
    if receipt["status"] == "RUNNING":
        raise RuntimeError("Only a finalized attempt can receive a post-run snapshot")
    destination = attempt / "runtime-classpath-observation"
    destination.mkdir(exist_ok=False)
    shutil.copyfile(recorder_source, destination / "recorder.py")
    result = {
        "schema": schema,
        "runtime_capture_core_sha256": sha(Path(__file__).read_bytes()),
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


def digest(data):
    return hashlib.sha256(data).hexdigest()


def safe_child(base, relative):
    name = PurePosixPath(relative)
    if not relative or name.is_absolute() or any(p in ("", ".", "..") for p in relative.split("/")):
        raise ValueError("Unsafe observed classpath member")
    path = base.joinpath(*name.parts)
    if path.resolve(strict=True) != path or not path.is_relative_to(base):
        raise ValueError("Classpath aliases or escaped paths are not archived")
    return path


def archive_classpath(root, attempt, gradle, *, expected_module, output_name, schema, archiver_source,
                      java_executable=None):
    """Archive the observed module's bytes; never build, test or initialize."""
    root, attempt, gradle = root.resolve(strict=True), attempt.resolve(strict=True), gradle.resolve(strict=True)
    if not expected_module or "/" in expected_module or expected_module in {".", ".."}:
        raise ValueError("One explicit test module is required")
    if not output_name or "/" in output_name or output_name in {".", ".."}:
        raise ValueError("One archive output directory name is required")
    output = attempt / output_name
    output.mkdir(exist_ok=False)
    report = {"schema": schema, "status": "INCOMPLETE",
              "scope": "Exact post-qualification classpath bytes only; no new runtime or gameplay acceptance.",
              "engine_initializations": 0, "games": 0, "entropy_draws": 0}
    try:
        receipt_raw = (attempt / "receipt.json").read_bytes()
        receipt = json.loads(receipt_raw)
        observed_raw = (attempt / "runtime-classpath-observation/manifest.json").read_bytes()
        observed = json.loads(observed_raw)
        head = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
        if receipt["status"] != "PASS" or not receipt["compiled_inputs_unchanged"] or receipt["source_head"] != head:
            raise ValueError("Only the exact source of a successful completed qualification is eligible")
        if observed["status"] != "OBSERVED" or observed["receipt_sha256"] != digest(receipt_raw):
            raise ValueError("Completed observation must bind the exact qualification receipt")
        if len(observed["stages"]) != 1:
            raise ValueError("Expected one original batched qualification stage")
        stage = observed["stages"][0]
        if stage["module"] != expected_module or stage["status"] != "OBSERVED" or len(stage["matching_workers"]) != 1:
            raise ValueError("Ambiguous or absent actual declared-module worker classpath")
        worker = stage["matching_workers"][0]
        entries = worker["classpath"]
        if not entries:
            raise ValueError("Empty observed runtime")
        files, archive_members, actual_paths = [], [], []
        for index, entry in enumerate(entries):
            base = {"repository": root, "gradle_home": gradle}[entry["kind"]]
            path = safe_child(base, entry["path"])
            actual_paths.append(path)
            if output == path or output.is_relative_to(path):
                raise ValueError("Archive output overlaps an observed runtime entry")
            if entry["type"] == "file" and path.is_file():
                selected = [(path, "runtime", entry["sha256"], entry["bytes"])]
            elif entry["type"] == "directory" and path.is_dir():
                expected = {m["path"] for m in entry["members"]}
                actual = {p.relative_to(path).as_posix() for p in path.rglob("*") if p.is_file()}
                if expected != actual or len(expected) != len(entry["members"]):
                    raise ValueError("Observed directory member set changed")
                selected = [(safe_child(path, m["path"]), m["path"], m["sha256"], m["bytes"])
                            for m in entry["members"]]
            else:
                raise ValueError("Absent or unsupported observed runtime entry")
            for file, relative, expected_sha, expected_bytes in selected:
                member = f"classpath/{index:03d}/{relative}"
                files.append((file, member, expected_sha, expected_bytes))
                archive_members.append({"path": member, "classpath_index": index,
                                        "sha256": expected_sha, "bytes": expected_bytes})
        if len(set(actual_paths)) != len(actual_paths):
            raise ValueError("Duplicate observed runtime entry")
        archive = output / "runtime-files.tar.gz"
        with archive.open("xb") as raw_out, gzip.GzipFile(fileobj=raw_out, mode="wb", mtime=0) as zipped:
            with tarfile.open(fileobj=zipped, mode="w|") as tar:
                for file, member, expected_sha, expected_bytes in files:
                    data = file.read_bytes()
                    if len(data) != expected_bytes or digest(data) != expected_sha:
                        raise ValueError("Observed runtime bytes changed before archive: " + member)
                    info = tarfile.TarInfo(member)
                    info.size, info.mode = len(data), 0o644
                    tar.addfile(info, io.BytesIO(data))
        for file, member, expected_sha, expected_bytes in files:
            data = file.read_bytes()
            if len(data) != expected_bytes or digest(data) != expected_sha:
                raise ValueError("Observed runtime bytes changed after archive: " + member)
        for entry in entries:
            base = {"repository": root, "gradle_home": gradle}[entry["kind"]]
            path = safe_child(base, entry["path"])
            if entry["type"] == "directory":
                expected = {m["path"] for m in entry["members"]}
                actual = {p.relative_to(path).as_posix() for p in path.rglob("*") if p.is_file()}
                if not path.is_dir() or expected != actual:
                    raise ValueError("Observed directory member set changed after archive")
                for member in expected:
                    safe_child(path, member)
            elif not path.is_file():
                raise ValueError("Observed runtime file changed kind after archive")
        if (attempt / "receipt.json").read_bytes() != receipt_raw or (attempt / "runtime-classpath-observation/manifest.json").read_bytes() != observed_raw:
            raise ValueError("Qualification receipt or observation changed")
        if subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip() != head:
            raise ValueError("Source HEAD changed")
        java = (Path(java_executable).resolve(strict=True) if java_executable is not None
                else Path(os.environ["JAVA_HOME"]).resolve(strict=True) / "bin/java")
        report.update(status="ARCHIVED_OBSERVED_BYTES_REQUIRES_INDEPENDENT_REVIEW", source_head=head,
                      qualification_receipt_sha256=digest(receipt_raw), observation_sha256=digest(observed_raw),
                      archiver_sha256=digest(archiver_source.read_bytes()),
                      runtime_capture_core_sha256=digest(Path(__file__).read_bytes()),
                      java_executable={"path": str(java), "sha256": digest(java.read_bytes())},
                      ordered_classpath=entries, actual_worker_argument_file_sha256=worker["argument_file_sha256"],
                      archive={"path": archive.name, "bytes": archive.stat().st_size,
                               "sha256": digest(archive.read_bytes()), "members": archive_members})
    except Exception as error:
        report["error"] = {"type": type(error).__name__, "message": str(error)}
        raise
    finally:
        with (output / "runtime-archive-receipt.json").open("x") as handle:
            json.dump(report, handle, indent=2)
            handle.write("\n")
        print(json.dumps({"status": report["status"], "receipt": str(output / "runtime-archive-receipt.json")}))
