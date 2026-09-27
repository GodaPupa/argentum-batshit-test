"""Read two bounded snapshots of the actual parent JVM's launch classpath.

This is an observation component, not an execution/admission guard. It never
imports an engine, reserves a claim, derives an allocation, or starts a worker.
It does not prove loaded-class identity, JDK closure, or in-flight immutability.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import stat
import time

from observed_test_runtime import safe_child


INPUT_LIMIT = 8 * 1024 * 1024
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
BOUND_NAMES = (
    "max_seconds", "max_metadata_bytes", "max_file_bytes", "max_total_bytes",
    "max_entries", "max_classpath_entries", "max_journal_bytes",
)


class ObservationError(ValueError):
    pass


def require(condition, code):
    if not condition:
        raise ObservationError(code)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write_exclusive(path, data):
    with path.open("xb") as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    descriptor = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def encoded(value):
    return (json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n").encode()


class Budget:
    def __init__(self, limits, clock):
        require(isinstance(limits, dict) and set(limits) == set(BOUND_NAMES), "MISSING_OR_UNKNOWN_BOUNDS")
        for name, value in limits.items():
            require(type(value) in (int, float) and math.isfinite(value) and value > 0, "INVALID_BOUND")
            if name != "max_seconds":
                require(type(value) is int, "NONINTEGER_BYTE_OR_COUNT_BOUND")
        self.limits = limits
        self.clock = clock
        self.started = clock()
        self.bytes = 0
        self.entries = 0

    def check(self):
        require(self.clock() - self.started <= self.limits["max_seconds"], "TIME_BOUND_EXCEEDED")

    def consume(self, count):
        self.check()
        self.bytes += count
        require(self.bytes <= self.limits["max_total_bytes"], "TOTAL_BYTE_BOUND_EXCEEDED")

    def entry(self):
        self.check()
        self.entries += 1
        require(self.entries <= self.limits["max_entries"], "ENTRY_BOUND_EXCEEDED")


def limited_read(path, limit, budget):
    budget.check()
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, "rb") as stream:
        require(stat.S_ISREG(os.fstat(stream.fileno()).st_mode), "NONREGULAR_METADATA")
        data = stream.read(limit + 1)
    budget.consume(len(data))
    require(len(data) <= limit, "METADATA_BOUND_EXCEEDED")
    return data


def file_identity(path, budget, *, proc_executable=False):
    """Reject nonregular inputs and changes observed during a bounded file read."""
    budget.check()
    flags = os.O_RDONLY | os.O_NONBLOCK
    if not proc_executable:
        flags |= os.O_NOFOLLOW
    fd = os.open(path, flags)
    with os.fdopen(fd, "rb") as stream:
        before = os.fstat(stream.fileno())
        require(stat.S_ISREG(before.st_mode), "NONREGULAR_FILE")
        require(before.st_size <= budget.limits["max_file_bytes"], "FILE_BYTE_BOUND_EXCEEDED")
        digest = hashlib.sha256()
        count = 0
        while data := stream.read(1024 * 1024):
            budget.consume(len(data))
            count += len(data)
            require(count <= budget.limits["max_file_bytes"], "FILE_BYTE_BOUND_EXCEEDED")
            digest.update(data)
        after = os.fstat(stream.fileno())
    current = path.stat() if proc_executable else path.lstat()
    signature = lambda s: (s.st_dev, s.st_ino, s.st_mode, s.st_size, s.st_mtime_ns, s.st_ctime_ns)
    require(signature(before) == signature(after) == signature(current) and count == before.st_size, "FILE_CHANGED_DURING_READ")
    return {"sha256": digest.hexdigest(), "bytes": count}


def identify(path, roots):
    require(path.is_absolute() and path.resolve(strict=True) == path, "NONCANONICAL_PATH")
    matches = [(kind, root) for kind, root in roots.items() if path.is_relative_to(root)]
    require(len(matches) == 1, "PATH_OUTSIDE_OR_AMBIGUOUS_ROOT")
    kind, root = matches[0]
    relative = path.relative_to(root).as_posix()
    require(relative != ".", "CLASSPATH_ROOT_NOT_ENTRY")
    require(safe_child(root, relative) == path, "UNSAFE_CLASSPATH_ENTRY")
    return {"kind": kind, "path": relative}


def directory_identity(path, budget):
    members = []
    pending = [path]
    while pending:
        directory = pending.pop()
        children = []
        with os.scandir(directory) as entries:
            for entry in entries:
                budget.entry()
                require(not entry.is_symlink(), "SYMLINK_IN_CLASSPATH_DIRECTORY")
                children.append(Path(entry.path))
        for child in sorted(children):
            relative = child.relative_to(path).as_posix()
            require(safe_child(path, relative) == child, "ESCAPED_DIRECTORY_MEMBER")
            mode = child.lstat().st_mode
            if stat.S_ISDIR(mode):
                pending.append(child)
            else:
                require(stat.S_ISREG(mode), "NONREGULAR_DIRECTORY_MEMBER")
                members.append({"path": relative, **file_identity(child, budget)})
    return {"type": "directory", "members": sorted(members, key=lambda row: row["path"])}


def process_identity(proc, pid, budget):
    node = proc / str(pid)
    raw_stat = limited_read(node / "stat", budget.limits["max_metadata_bytes"], budget)
    text = raw_stat.decode()
    end = text.rfind(")")
    fields = text[end + 2:].split()
    require(text.startswith(str(pid) + " (") and end >= 0 and len(fields) >= 20, "MALFORMED_PROCESS_STAT")
    require(fields[0] not in ("Z", "X", "x") and fields[19].isdigit(), "DEAD_OR_MALFORMED_PARENT")
    executable_link = os.readlink(node / "exe")
    require(not executable_link.endswith(" (deleted)"), "DELETED_JAVA_EXECUTABLE")
    executable = Path(executable_link)
    require(executable.is_absolute() and executable.resolve(strict=True) == executable and executable.name == "java", "PARENT_IS_NOT_CANONICAL_JAVA")
    cmdline = limited_read(node / "cmdline", budget.limits["max_metadata_bytes"], budget)
    require(cmdline.endswith(b"\0"), "MALFORMED_PARENT_COMMAND")
    argv = [part.decode() for part in cmdline[:-1].split(b"\0")]
    require(argv and all(argv), "MALFORMED_PARENT_COMMAND")
    argument_files = [arg[1:] for arg in argv[1:] if arg.startswith("@")]
    require(len(argument_files) == 1 and not any(arg in ("-cp", "-classpath", "--class-path") for arg in argv[1:]), "AMBIGUOUS_PARENT_CLASSPATH")
    boot = limited_read(proc / "sys/kernel/random/boot_id", budget.limits["max_metadata_bytes"], budget).decode().strip()
    require(bool(re.fullmatch(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}", boot)), "MALFORMED_BOOT_ID")
    return {
        "pid": pid, "start_time_ticks": fields[19], "boot_id": boot,
        "java_path": str(executable), "cmdline_sha256": sha(cmdline),
        "argument_file": argument_files[0],
    }


def snapshot(expected, roots, proc, pid, budget, emit):
    identity = process_identity(proc, pid, budget)
    require(identity["cmdline_sha256"] == expected["cmdline_sha256"], "PARENT_COMMAND_MISMATCH")
    # Hash the kernel's executing inode, not a file merely occupying its pathname.
    java = file_identity(proc / str(pid) / "exe", budget, proc_executable=True)
    require(java["sha256"] == expected["java_sha256"], "JAVA_BYTE_MISMATCH")
    emit({"kind": "parent", "identity": identity, "java": java})
    argument = Path(identity["argument_file"])
    location = identify(argument, roots)
    require(location["kind"] == "gradle_home", "ARGUMENT_FILE_OUTSIDE_GRADLE_ROOT")
    raw = limited_read(argument, budget.limits["max_metadata_bytes"], budget)
    require(sha(raw) == expected["argument_file_sha256"], "ARGUMENT_BYTE_MISMATCH")
    lines = raw.decode().splitlines()
    require(len(lines) == 2 and lines[0] == "-cp" and bool(lines[1]), "MALFORMED_WORKER_ARGUMENT_FILE")
    names = lines[1].split(os.pathsep)
    require(all(names) and len(names) <= budget.limits["max_classpath_entries"], "INVALID_CLASSPATH_LENGTH")
    observed = []
    seen = set()
    for index, name in enumerate(names):
        budget.entry()
        path = Path(name)
        location = identify(path, roots)
        key = (location["kind"], location["path"])
        require(key not in seen, "DUPLICATE_CLASSPATH_ENTRY")
        seen.add(key)
        mode = path.lstat().st_mode
        if stat.S_ISDIR(mode):
            entry = {**location, **directory_identity(path, budget)}
        else:
            require(stat.S_ISREG(mode), "NONREGULAR_CLASSPATH_ENTRY")
            entry = {**location, "type": "file", **file_identity(path, budget)}
        observed.append(entry)
        emit({"kind": "classpath_entry", "index": index, "identity": entry})
    require(observed == expected["ordered_classpath"], "ORDERED_CLASSPATH_MISMATCH")
    require(process_identity(proc, pid, budget) == identity, "PARENT_CHANGED_DURING_SNAPSHOT")
    return {"parent": identity, "java": java, "argument_file_sha256": sha(raw), "ordered_classpath": observed}


def observe(input_path, output, *, _proc=Path("/proc"), _parent=os.getppid, _clock=time.monotonic):
    """Test seams are Python-only; CLI always observes its real direct parent."""
    output = Path(output)
    output.mkdir(exist_ok=False)
    result = {"schema": "parent-jvm-classpath-observation-v1", "status": "INCOMPLETE",
              "admission": False, "claims_created": 0, "engine_initializations": 0,
              "scope": "Two bounded launch-byte snapshots only; not loaded-class/JDK/image closure or in-flight immutability."}
    journal = output / "observations.jsonl"
    try:
        write_exclusive(output / "intent.json", encoded({"observer_sha256": sha(Path(__file__).read_bytes()), "input_name": Path(input_path).name, "admission": False}))
        input_fd = os.open(input_path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(input_fd, "rb") as source:
            require(stat.S_ISREG(os.fstat(source.fileno()).st_mode), "NONREGULAR_INPUT")
            raw = source.read(INPUT_LIMIT + 1)
        write_exclusive(output / "input-identity.json", encoded({"input_sha256": sha(raw), "input_bytes_read": len(raw)}))
        require(len(raw) <= INPUT_LIMIT, "INPUT_CODEC_BOUND_EXCEEDED")
        expected = json.loads(raw)
        require(expected.get("schema") == "parent-jvm-classpath-observation-input-v1", "UNKNOWN_INPUT_SCHEMA")
        budget = Budget(expected.get("bounds"), _clock)
        for key in ("java_sha256", "cmdline_sha256", "argument_file_sha256"):
            require(isinstance(expected.get(key), str) and SHA256.fullmatch(expected[key]), "MISSING_EXPECTED_DIGEST")
        require(isinstance(expected.get("ordered_classpath"), list) and bool(expected["ordered_classpath"]), "MISSING_EXPECTED_CLASSPATH")
        require(len(expected["ordered_classpath"]) <= budget.limits["max_classpath_entries"], "EXPECTED_CLASSPATH_BOUND_EXCEEDED")
        require(isinstance(expected.get("roots"), dict) and set(expected["roots"]) == {"repository", "gradle_home"}, "MISSING_LOGICAL_ROOTS")
        roots = {kind: Path(name) for kind, name in expected["roots"].items()}
        require(all(p.is_absolute() and p.resolve(strict=True) == p and p.is_dir() for p in roots.values()), "NONCANONICAL_LOGICAL_ROOT")
        require(not any(a.is_relative_to(b) for ka, a in roots.items() for kb, b in roots.items() if ka != kb), "OVERLAPPING_LOGICAL_ROOTS")
        pid = _parent()
        require(type(pid) is int and pid > 1, "INVALID_DIRECT_PARENT")
        result["input_sha256"] = sha(raw)
        with journal.open("xb") as stream:
            written = 0
            def emit(record):
                nonlocal written
                data = encoded(record)
                written += len(data)
                require(written <= budget.limits["max_journal_bytes"], "JOURNAL_BYTE_BOUND_EXCEEDED")
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            first = snapshot(expected, roots, _proc, pid, budget, emit)
            emit({"kind": "first_snapshot_complete", "sha256": sha(encoded(first))})
            require(_parent() == pid, "DIRECT_PARENT_REPLACED")
            second = snapshot(expected, roots, _proc, pid, budget, emit)
            require(_parent() == pid and first == second, "SNAPSHOTS_OR_PARENT_DIFFER")
            budget.check()
            result.update(status="MATCHED_TWO_SNAPSHOTS_NOT_ADMISSION", snapshot_sha256=sha(encoded(first)),
                          bytes_read=budget.bytes, entries_visited=budget.entries, journal_bytes=written,
                          elapsed_seconds=_clock() - budget.started)
    except Exception as error:
        result["status"] = "FAILED_OBSERVATION"
        result["error"] = {"type": type(error).__name__, "code": str(error) if isinstance(error, ObservationError) else "READ_OR_DECODE_FAILURE"}
    finally:
        write_exclusive(output / "result.json", encoded(result))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = observe(args.input, args.output)
    print(json.dumps({"status": result["status"], "admission": False}))
    return 0 if result["status"] == "MATCHED_TWO_SNAPSHOTS_NOT_ADMISSION" else 1


if __name__ == "__main__":
    raise SystemExit(main())
