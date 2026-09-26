#!/usr/bin/env python3
"""Durable, single-launch process supervision. Never interprets a process exit as a game result."""
from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import re
import resource
import signal
import stat
import subprocess
import sys
import time
from typing import Any

VERSION = "ferocity-watchdog-v1.2"
SOURCE = Path(__file__).resolve()
RECORD_TYPES = {"HEADER", "INITIALIZED", "INTENT", "RESULT", "FAULT", "END"}


def canonical(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_digest(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def no_duplicates(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON field: {key}")
        result[key] = value
    return result


def strict_json(text: bytes | str) -> Any:
    def invalid_constant(value: str) -> None:
        raise ValueError(f"Nonfinite JSON constant: {value}")
    return json.loads(text, object_pairs_hook=no_duplicates, parse_constant=invalid_constant)


def force_directory(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def durable_directory(path: Path) -> None:
    if path.exists():
        if not path.is_dir():
            raise ValueError("Evidence parent is not a directory")
        return
    durable_directory(path.parent)
    path.mkdir()
    force_directory(path.parent)
    force_directory(path)


def write_new(path: Path, data: bytes) -> None:
    with path.open("xb", buffering=0) as stream:
        view = memoryview(data)
        while view:
            count = stream.write(view)
            if count is None or count <= 0:
                raise OSError("Incomplete durable write")
            view = view[count:]
        os.fsync(stream.fileno())
    force_directory(path.parent)


@dataclasses.dataclass(frozen=True)
class WatchdogSpec:
    schema_version: int
    run_id: str
    argv: tuple[str, ...]
    cwd: str
    wall_seconds: float
    term_grace_seconds: float
    journal_path: str | None
    pinned_files: dict[str, str]
    supervisor_sha256: str
    inspection_limit_bytes: int = 32 * 1024 * 1024
    file_size_limit_bytes: int | None = None
    minimum_free_bytes: int | None = None

    @classmethod
    def parse(cls, value: dict[str, Any]) -> "WatchdogSpec":
        if not isinstance(value, dict) or set(value) != {field.name for field in dataclasses.fields(cls)}:
            raise ValueError("Unsupported watchdog spec fields")
        copy = strict_json(canonical(value))
        if not isinstance(copy["argv"], list):
            raise ValueError("argv must be a structured array")
        copy["argv"] = tuple(copy["argv"])
        result = cls(**copy)
        result.validate()
        return result

    def validate(self) -> None:
        if type(self.schema_version) is not int or self.schema_version != 1:
            raise ValueError("Unsupported watchdog schema")
        if not isinstance(self.run_id, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}", self.run_id):
            raise ValueError("Invalid supervisor run identity")
        if not self.argv or any(not isinstance(arg, str) or "\0" in arg for arg in self.argv):
            raise ValueError("An exact nonempty argv without NUL is required")
        executable = Path(self.argv[0])
        if not executable.is_absolute() or not executable.is_file() or not os.access(executable, os.X_OK):
            raise ValueError("Child executable must be an absolute executable file")
        if not Path(self.cwd).is_absolute() or not Path(self.cwd).is_dir():
            raise ValueError("Working directory must be an existing absolute directory")
        if self.journal_path is not None and not Path(self.journal_path).is_absolute():
            raise ValueError("Journal path must be absolute")
        for label, value, upper in (("wall", self.wall_seconds, 86400), ("TERM grace", self.term_grace_seconds, 60)):
            if type(value) not in (int, float) or not math.isfinite(value) or not 0 < value <= upper:
                raise ValueError(f"Invalid finite {label} limit")
        if type(self.inspection_limit_bytes) is not int or not 1 <= self.inspection_limit_bytes <= 128 * 1024 * 1024:
            raise ValueError("Invalid inspection size limit")
        if (self.file_size_limit_bytes is None) != (self.minimum_free_bytes is None):
            raise ValueError("File size and free-space limits must be supplied together")
        if self.file_size_limit_bytes is not None:
            if type(self.file_size_limit_bytes) is not int or self.file_size_limit_bytes != 128 * 1024 * 1024:
                raise ValueError("The qualified file-size limit is exactly 128 MiB")
            if type(self.minimum_free_bytes) is not int or self.minimum_free_bytes != 768 * 1024 * 1024:
                raise ValueError("The qualified free-space floor is exactly 768 MiB")
        if not isinstance(self.pinned_files, dict) or not self.pinned_files:
            raise ValueError("Exact executable/input pins are required")
        for name, expected in self.pinned_files.items():
            if not isinstance(name, str) or not Path(name).is_absolute() or not re.fullmatch(r"[0-9a-f]{64}", expected):
                raise ValueError("Invalid file pin")
            if not Path(name).is_file():
                raise ValueError("Pinned input must be a regular file")
            if file_digest(Path(name)) != expected:
                raise ValueError(f"Input pin mismatch: {name}")
        if str(executable.resolve()) not in self.pinned_files:
            raise ValueError("Resolved child executable must be pinned")
        if not re.fullmatch(r"[0-9a-f]{64}", self.supervisor_sha256) or file_digest(SOURCE) != self.supervisor_sha256:
            raise ValueError("Supervisor source pin mismatch")


class EvidenceWriter:
    def __init__(self, directory: Path, claim: dict[str, Any]):
        durable_directory(directory.parent)
        directory.mkdir()  # Exclusive run reservation; an existing run is never reopened.
        force_directory(directory.parent)
        force_directory(directory)
        self.directory = directory
        self.previous = digest(canonical(claim))
        write_new(directory / "claim.json", canonical(claim))
        self.stream = (directory / "events.jsonl").open("xb", buffering=0)
        os.fsync(self.stream.fileno())
        force_directory(directory)
        self.index = 0
        self.poisoned = False

    def append(self, kind: str, **data: Any) -> None:
        if self.poisoned:
            raise OSError("Supervisor evidence writer has an incomplete prior write")
        unsigned = {"index": self.index, "previous_sha256": self.previous, "kind": kind, "data": data}
        row = dict(unsigned, sha256=digest(canonical(unsigned)))
        try:
            view = memoryview(canonical(row) + b"\n")
            while view:
                count = self.stream.write(view)
                if count is None or count <= 0:
                    raise OSError("Incomplete supervisor record")
                view = view[count:]
            os.fsync(self.stream.fileno())
        except BaseException:
            self.poisoned = True
            raise
        self.previous = row["sha256"]
        self.index += 1

    def close(self) -> None:
        self.stream.close()


def inspect_file(path: Path, limit: int) -> tuple[dict[str, Any], bytes | None]:
    """Read-only bounded inspection. Never follow a substituted symlink or block on a FIFO."""
    try:
        descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    except FileNotFoundError:
        return {"path": str(path), "state": "ABSENT"}, None
    except OSError as error:
        return {"path": str(path), "state": "UNREADABLE", "error": str(error)}, None
    with os.fdopen(descriptor, "rb") as stream:
        before = os.fstat(stream.fileno())
        if not stat.S_ISREG(before.st_mode):
            return {"path": str(path), "state": "NOT_REGULAR"}, None
        if before.st_size > limit:
            return {"path": str(path), "state": "OVER_INSPECTION_LIMIT", "size": before.st_size, "sha256": None}, None
        raw = stream.read(limit + 1)
        after = os.fstat(stream.fileno())
    metadata = {"path": str(path), "size": len(raw), "sha256": digest(raw)}
    if (before.st_size, before.st_mtime_ns, before.st_ctime_ns) != (after.st_size, after.st_mtime_ns, after.st_ctime_ns):
        return dict(metadata, state="CHANGED_DURING_INSPECTION"), None
    return dict(metadata, state="READ_ONLY_SNAPSHOT"), raw


def inspect_journal(path: str | None, limit: int) -> dict[str, Any]:
    if path is None:
        return {"classification": "NO_JOURNAL_CONFIGURED", "engine_verified": False}
    metadata, raw = inspect_file(Path(path), limit)
    result = dict(metadata, engine_verified=False)
    if raw is None:
        return dict(result, classification="UNRESOLVED_" + metadata["state"])
    if not raw:
        return dict(result, classification="UNRESOLVED_EMPTY_PREFIX")
    if not raw.endswith(b"\n"):
        return dict(result, classification="UNRESOLVED_TRUNCATED_TAIL")
    pending: int | None = None
    last: str | None = None
    try:
        for line in raw.splitlines():
            envelope = strict_json(line)
            if not isinstance(envelope, dict) or set(envelope) != {"index", "previousSha256", "record", "sha256"}:
                raise ValueError("Unrecognized journal envelope")
            record = envelope["record"]
            if not isinstance(record, dict) or record.get("type") not in RECORD_TYPES:
                raise ValueError("Unrecognized record marker")
            if last == "END":
                raise ValueError("Bytes occur after END")
            last = record["type"]
            if last == "INTENT":
                if pending is not None or type(record.get("submission")) is not int:
                    raise ValueError("Invalid intent marker sequence")
                pending = record["submission"]
            if last == "RESULT":
                if pending is None or record.get("submission") != pending:
                    raise ValueError("Result marker has no matching intent")
                pending = None
    except (ValueError, TypeError, UnicodeError, KeyError) as error:
        return dict(result, classification="UNRESOLVED_UNPARSEABLE_PREFIX", inspection_error=str(error))
    # This deliberately does not validate engine schemas or certify the journal chain. Even an
    # END marker still needs the authoritative strict reader and replay; no winner is inspected.
    classification = "UNRESOLVED_OBSERVED_UNMATCHED_INTENT" if pending is not None else (
        "OBSERVED_END_REQUIRES_ENGINE_REPLAY" if last == "END" else "UNRESOLVED_OBSERVED_PREFIX")
    return dict(result, classification=classification, observed_last_type=last, unmatched_submission=pending)


class SupervisorInterrupted(Exception):
    pass


def resource_preflight(spec: WatchdogSpec, evidence_directory: Path) -> list[dict[str, Any]]:
    """Inspect each destination filesystem before launching; this does not reserve free space."""
    if spec.minimum_free_bytes is None:
        return []
    destinations = [evidence_directory]
    if spec.journal_path is not None:
        parent = Path(spec.journal_path).parent
        while not parent.exists():
            parent = parent.parent
        destinations.append(parent)
    facts = []
    for destination in dict.fromkeys(path.resolve() for path in destinations):
        fs = os.statvfs(destination)
        available = fs.f_bavail * fs.f_frsize
        facts.append({"path": str(destination), "available_bytes": available,
                      "required_bytes": spec.minimum_free_bytes,
                      "sufficient": available >= spec.minimum_free_bytes})
    return facts


def apply_child_file_limit(limit: int) -> None:
    # The supervisor is a single-threaded process. This runs in its owned POSIX child,
    # before exec; both limits are lowered so the Java process cannot raise its own cap.
    resource.setrlimit(resource.RLIMIT_FSIZE, (limit, limit))


def supervise(spec: WatchdogSpec, output_root: Path) -> dict[str, Any]:
    if os.name != "posix":
        raise RuntimeError("This reviewed watchdog requires POSIX process groups and durable directory writes")
    spec = WatchdogSpec.parse(dataclasses.asdict(spec))
    executable = Path(spec.argv[0]).resolve()
    claim = {"schema_version": 1, "version": VERSION, "spec": dataclasses.asdict(spec),
             "spec_sha256": digest(canonical(dataclasses.asdict(spec))), "supervisor_source_sha256": file_digest(SOURCE),
             "python_executable": str(Path(sys.executable).resolve()), "python_executable_sha256": file_digest(Path(sys.executable).resolve()),
             "python_version": sys.version, "platform": platform.platform(), "child_executable": str(executable),
             "child_executable_sha256": file_digest(executable), "created_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
             "scope": "PROCESS_SUPERVISION_ONLY", "game_outcome": None}
    directory = output_root.resolve() / spec.run_id
    writer = EvidenceWriter(directory, claim)
    child: subprocess.Popen[bytes] | None = None
    start = time.monotonic_ns()
    signals: list[str] = []
    timed_out = False
    descendants_found = False
    group_gone: bool | None = None
    previous_handlers: dict[int, Any] = {}
    launching = False
    deferred_interrupt: int | None = None

    def interrupt(signum: int, _frame: Any) -> None:
        nonlocal deferred_interrupt
        # Popen can create the OS child before returning its owned Python handle. Raising in
        # that interval would strand the new group. Defer soft cancellation until assignment;
        # do not block/inherit a signal mask in the child.
        if launching:
            if deferred_interrupt is None:
                deferred_interrupt = signum
            return
        raise SupervisorInterrupted(f"Supervisor received signal {signum}")

    def send(sig: signal.Signals) -> None:
        assert child is not None
        # A broken evidence disk must not prevent cleanup of the already-owned process group.
        # The original write failure remains fatal; no successful process result is fabricated.
        try:
            writer.append("SIGNAL_INTENT", process_group=child.pid, signal=sig.name)
        except OSError:
            pass
        try:
            os.killpg(child.pid, sig)
            signals.append(sig.name)
            if not writer.poisoned:
                writer.append("SIGNAL_SENT", process_group=child.pid, signal=sig.name)
        except ProcessLookupError:
            if not writer.poisoned:
                writer.append("SIGNAL_GROUP_ABSENT", process_group=child.pid, signal=sig.name)

    def group_exists() -> bool:
        assert child is not None
        child.poll()  # Reap the direct child before testing whether its group remains.
        try:
            os.killpg(child.pid, 0)
            return True
        except ProcessLookupError:
            return False

    def terminate() -> bool:
        assert child is not None
        send(signal.SIGTERM)
        deadline = time.monotonic() + spec.term_grace_seconds
        while time.monotonic() < deadline:
            if not group_exists():
                child.wait(timeout=spec.term_grace_seconds)
                return True
            time.sleep(min(0.01, max(0, deadline - time.monotonic())))
        send(signal.SIGKILL)
        child.wait(timeout=spec.term_grace_seconds)
        deadline = time.monotonic() + spec.term_grace_seconds
        while time.monotonic() < deadline:
            if not group_exists():
                return True
            time.sleep(min(0.01, max(0, deadline - time.monotonic())))
        return not group_exists()

    try:
        for signum in (signal.SIGTERM, signal.SIGINT):
            previous_handlers[signum] = signal.signal(signum, interrupt)
        if spec.minimum_free_bytes is not None:
            facts = resource_preflight(spec, directory)
            writer.append("RESOURCE_PREFLIGHT", filesystems=facts,
                          child_file_size_limit_bytes=spec.file_size_limit_bytes)
            if not facts or any(not row["sufficient"] for row in facts):
                raise OSError("Insufficient evidence space: 768 MiB free is required before child launch")
        writer.append("LAUNCH_INTENT", argv=list(spec.argv), cwd=spec.cwd)
        with (directory / "stdout.log").open("xb", buffering=0) as stdout, (directory / "stderr.log").open("xb", buffering=0) as stderr:
            force_directory(directory)
            launching = True
            try:
                child = subprocess.Popen(list(spec.argv), cwd=spec.cwd, stdin=subprocess.DEVNULL,
                                         stdout=stdout, stderr=stderr, shell=False, start_new_session=True,
                                         preexec_fn=(None if spec.file_size_limit_bytes is None else
                                                     lambda: apply_child_file_limit(spec.file_size_limit_bytes)))
            finally:
                launching = False
            if deferred_interrupt is not None:
                raise SupervisorInterrupted(f"Supervisor received signal {deferred_interrupt} during child creation")
            writer.append("CHILD_STARTED", pid=child.pid, process_group=child.pid)
            remaining = max(0.000001, spec.wall_seconds - (time.monotonic_ns() - start) / 1e9)
            try:
                child.wait(timeout=remaining)
            except subprocess.TimeoutExpired:
                timed_out = True
                writer.append("WALL_LIMIT_REACHED", elapsed_ns=time.monotonic_ns() - start)
                group_gone = terminate()
            if not timed_out and group_exists():
                descendants_found = True
                writer.append("LEADER_EXITED_WITH_PROCESS_GROUP", pid=child.pid, returncode=child.returncode)
                group_gone = terminate()
            os.fsync(stdout.fileno())
            os.fsync(stderr.fileno())
        exit_code = child.returncode
        classification = ("TIMEOUT_KILLED" if "SIGKILL" in signals else "TIMEOUT_TERMINATED") if timed_out else (
            "EXIT_ZERO_REQUIRES_ENGINE_REPLAY" if exit_code == 0 else "EXIT_NONZERO_UNRESOLVED")
        pins_unchanged = file_digest(SOURCE) == spec.supervisor_sha256 and all(
            file_digest(Path(name)) == expected for name, expected in spec.pinned_files.items())
        result = {"classification": classification, "pid": child.pid, "returncode": exit_code,
                  "elapsed_ns": time.monotonic_ns() - start, "signals": signals, "timed_out": timed_out,
                  "descendants_after_leader_exit": descendants_found, "process_group_absent_after_cleanup": group_gone,
                  "input_pins_unchanged": pins_unchanged, "journal": inspect_journal(spec.journal_path, spec.inspection_limit_bytes),
                  "stdout": inspect_file(directory / "stdout.log", spec.inspection_limit_bytes)[0],
                  "stderr": inspect_file(directory / "stderr.log", spec.inspection_limit_bytes)[0],
                  "game_outcome": None, "new_gameplay_games": 0, "admission": "REQUIRES_SEPARATE_ENGINE_REPLAY_AND_PROTOCOL_ACCEPTANCE"}
        if not pins_unchanged:
            result["classification"] = "SOURCE_OR_INPUT_CHANGED_UNRESOLVED"
        elif descendants_found:
            result["classification"] = "LEADER_EXITED_WITH_DESCENDANTS_UNRESOLVED"
        writer.append("PROCESS_RESULT", **result)
        return result
    except BaseException as error:
        # Once cancellation has begun, repeated soft cancellation cannot interrupt group cleanup.
        for signum in previous_handlers:
            signal.signal(signum, signal.SIG_IGN)
        if child is not None and group_exists():
            terminate()
        writer.append("SUPERVISOR_FAILURE", failure_type=type(error).__name__, message=str(error),
                      pid=child.pid if child else None, returncode=child.returncode if child else None,
                      elapsed_ns=time.monotonic_ns() - start, signals=signals, game_outcome=None)
        raise
    finally:
        for signum, handler in previous_handlers.items():
            signal.signal(signum, handler)
        writer.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--spec", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    args = parser.parse_args()
    spec = WatchdogSpec.parse(strict_json(args.spec.read_bytes()))
    result = supervise(spec, args.output_root)
    print(json.dumps(result, sort_keys=True))
    return 0 if result["classification"] == "EXIT_ZERO_REQUIRES_ENGINE_REPLAY" else 2


if __name__ == "__main__":
    raise SystemExit(main())
