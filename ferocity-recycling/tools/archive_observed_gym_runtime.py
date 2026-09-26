#!/usr/bin/env python3
"""Preserve the already observed gym classpath; never build, test, initialize or allocate."""
import argparse
import gzip
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import tarfile


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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--attempt", type=Path, required=True)
    parser.add_argument("--gradle-home", type=Path, required=True)
    args = parser.parse_args()
    root, attempt, gradle = args.root.resolve(strict=True), args.attempt.resolve(strict=True), args.gradle_home.resolve(strict=True)
    output = attempt / "observed-gym-runtime"
    output.mkdir(exist_ok=False)
    report = {"schema": "ferocity-observed-gym-runtime-archive-v1", "status": "INCOMPLETE",
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
            raise ValueError("Expected the one original batched gym qualification stage")
        stage = observed["stages"][0]
        if stage["module"] != "gym" or stage["status"] != "OBSERVED" or len(stage["matching_workers"]) != 1:
            raise ValueError("Ambiguous or absent actual gym worker classpath")
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
        java = Path(os.environ["JAVA_HOME"]).resolve(strict=True) / "bin/java"
        report.update(status="ARCHIVED_OBSERVED_BYTES_REQUIRES_INDEPENDENT_REVIEW", source_head=head,
                      qualification_receipt_sha256=digest(receipt_raw), observation_sha256=digest(observed_raw),
                      archiver_sha256=digest(Path(__file__).read_bytes()),
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


if __name__ == "__main__":
    main()
