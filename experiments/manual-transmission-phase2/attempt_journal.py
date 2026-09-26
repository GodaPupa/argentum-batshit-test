"""Phase 2 local attempt durability and exact private-evidence binding.

This is a dormant component, not a gameplay entry point or admission decision. The
production owner must separately freeze its canonical store, acquire the unique
repository claim, and qualify the real initializer/engine adapter and pilots.
Changing an output path is never a substitute for that global reservation.
"""
from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path
import sys
import threading

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from evidence_durability import (  # noqa: E402
    append_journal, canonical, create_journal, create_json_once, verify_journal,
)
from import_engine_trace import import_trace
from metrics_contract import _hash, audit_record, require, validate_bindings


SCHEMA = "manual-transmission-phase2-local-attempt-v1"
FINAL_SCHEMA = "manual-transmission-phase2-bound-final-evidence-v1"
TRACE_BYTES_SCHEMA = "manual-transmission-phase2-private-trace-bytes-v1"
_META = {"journal_key", "journal_sequence", "previous_sha256", "record_sha256"}


def _detached(value: dict) -> dict:
    return json.loads(canonical(value))


def _digest(value: dict) -> str:
    return hashlib.sha256(canonical(value)).hexdigest()


def _identity(bindings: dict) -> dict:
    # Source, policies and output names must not permit a retry of the same member.
    return {key: bindings[key] for key in ("protocol_id", "pod_id", "allocation_id", "gear")}


def _snapshot(value: dict) -> dict:
    value = _detached(value)
    require(set(value) == {"initialState", "initialStateSha256"}, "Initial snapshot fields mismatch")
    require(isinstance(value["initialState"], dict) and value["initialState"], "Missing initial engine state")
    # This digest uses the engine's serialization, not Python's canonical JSON.
    # Exact deterministic engine replay must independently verify its semantics.
    _hash(value["initialStateSha256"])
    return value


def _decode_trace(envelope: dict) -> tuple[dict, bytes]:
    require(set(envelope) == {"schema", "encoding", "byte_count", "sha256", "payload"} and
            envelope["schema"] == TRACE_BYTES_SCHEMA and envelope["encoding"] == "base64",
            "Exact private trace byte envelope required")
    raw = base64.b64decode(envelope["payload"], validate=True)
    require(type(envelope["byte_count"]) is int and len(raw) == envelope["byte_count"] and
            hashlib.sha256(raw).hexdigest() == envelope["sha256"] and
            base64.b64encode(raw).decode("ascii") == envelope["payload"], "Private trace bytes mismatch")
    def unique_object(pairs):
        value = {}
        for key, item in pairs:
            require(key not in value, "Duplicate JSON key in private trace")
            value[key] = item
        return value
    trace = json.loads(raw, object_pairs_hook=unique_object)
    require(isinstance(trace, dict), "Private engine trace must be a JSON object")
    return trace, raw


def _read_exact(path: Path, expected_sha: str) -> dict:
    _hash(expected_sha)
    require(not path.is_symlink() and path.resolve(strict=True) == path.absolute(),
            "Evidence file must have a normalized nonsymlink path")
    raw = path.read_bytes()
    require(hashlib.sha256(raw).hexdigest() == expected_sha, "Evidence bytes differ from bound digest")
    value = json.loads(raw)
    require(canonical(value) == raw, "Evidence JSON is not canonical")
    return value


class AttemptJournal:
    """One local member claim, intent barrier, and create-only final evidence.

    Construct only with ``create``. The callback in ``initialize`` is never invoked
    before the intent record and parent directory are fsynced. A failed callback,
    write, import or audit consumes this object and its pathname; no resume/retry
    method exists. Remote, cross-workspace exclusivity is a separate prerequisite.
    """

    @classmethod
    def create(cls, directory: Path, bindings: dict) -> "AttemptJournal":
        b = _detached(bindings)
        validate_bindings(b)
        root = Path(directory).absolute()
        require(root.resolve(strict=True) == root and root.is_dir(),
                "Pre-existing canonical attempt directory required")
        instance = object.__new__(cls)
        instance._directory = root
        instance._bindings = b
        instance._key = _digest(_identity(b))
        instance._path = root / (instance._key + ".jsonl")
        instance._stage = "CREATED"
        instance._lock = threading.Lock()
        instance._initial = None
        instance._initial_artifact_sha = None
        instance._tail = create_journal(instance._path, {
            "schema": SCHEMA, "kind": "ATTEMPT_BEFORE_INITIALIZATION",
            "identity": _identity(b), "bindings": b,
            "execution_authorized_by_this_component": False,
        }, key="attempt")
        return instance

    def __init__(self):
        raise TypeError("Use AttemptJournal.create; existing attempts cannot be reopened")

    @property
    def journal_path(self) -> Path:
        return self._path

    @property
    def tail(self) -> str:
        """Caller must durably retain the tail outside the mutable journal."""
        return self._tail

    def _append(self, kind: str, **payload) -> None:
        self._tail = append_journal(self._path, {"kind": kind, **payload},
            key=kind, expected_tail=self._tail)

    def _preserve_failure(self, phase: str, error: BaseException) -> None:
        try:
            self._append("INTEGRITY_FAILURE", phase=phase,
                reason=f"{type(error).__name__}: {error}", outcome_status="INVALID")
        except Exception as journal_error:
            # Never repair or retry a partial write. The durable intent and any
            # partial bytes still identify a consumed, incomplete attempt.
            error.add_note(f"Failure evidence write also failed: {type(journal_error).__name__}")

    def initialize(self, initializer) -> dict:
        """Call the trusted initializer once after the durable intent barrier.

        It returns the private engine snapshot, not a pilot view. This callback
        seam is not evidence that a particular production initializer is qualified.
        """
        with self._lock:
            require(self._stage == "CREATED", "Initialization already consumed")
            self._stage = "CONSUMED"
            try:
                self._append("INITIALIZATION_ATTEMPT")
                initial = _snapshot(initializer())
                sha = create_json_once(self._directory / (self._key + "-initial.json"), initial)
                self._append("INITIALIZED", initial_artifact_sha256=sha,
                    initial_state_sha256=initial["initialStateSha256"])
                self._initial = initial
                self._initial_artifact_sha = sha
                self._stage = "INITIALIZED"
                return _detached(initial)
            except BaseException as error:
                self._preserve_failure("INITIALIZATION", error)
                raise

    def finalize(self, trace_bytes: bytes) -> dict:
        """Bind the full trace, independently rederived metrics and final journal.

        No arbitrary output pathname or outcome label is accepted. The returned
        receipt's digest must be retained outside this directory for later audit.
        """
        with self._lock:
            require(self._stage == "INITIALIZED", "Finalization requires one unused initialized attempt")
            self._stage = "CONSUMED"
            try:
                require(type(trace_bytes) is bytes, "Original serialized private trace bytes required")
                # Kotlin hashes the initial JsonObject in its original field order.
                # Canonicalizing the replay JSON would change that order and invalidate
                # engine replay. Keep the original bytes inside a canonical envelope.
                envelope = {"schema": TRACE_BYTES_SCHEMA, "encoding": "base64",
                    "byte_count": len(trace_bytes), "sha256": hashlib.sha256(trace_bytes).hexdigest(),
                    "payload": base64.b64encode(trace_bytes).decode("ascii")}
                trace, _ = _decode_trace(envelope)
                require(_snapshot({key: trace[key] for key in ("initialState", "initialStateSha256")}) ==
                        self._initial, "Final trace differs from the journaled initialization")
                record = import_trace(self._bindings, trace)
                audit = audit_record(record)
                files = {
                    "initial": {"name": self._key + "-initial.json", "sha256": self._initial_artifact_sha},
                    "trace": {"name": self._key + "-trace.json",
                        "sha256": create_json_once(self._directory / (self._key + "-trace.json"), envelope)},
                    "record": {"name": self._key + "-record.json",
                        "sha256": create_json_once(self._directory / (self._key + "-record.json"), record)},
                }
                self._append("FINALIZED", files=files, outcome_status=audit["outcome_status"],
                    record_sha256_bound=record["record_sha256"])
                receipt = {"schema": FINAL_SCHEMA, "attempt_key": self._key,
                    "identity": _identity(self._bindings), "bindings_sha256": _digest(self._bindings),
                    "journal_tail_sha256": self._tail, "files": files,
                    "execution_authorized_by_this_component": False,
                    "engine_replay_qualified_by_this_component": False}
                receipt_sha = create_json_once(self._directory / (self._key + "-final.json"), receipt)
                audited = audit_attempt(self._directory, self._key, receipt_sha)
                self._stage = "FINALIZED"
                return audited
            except BaseException as error:
                self._preserve_failure("FINALIZATION", error)
                raise


def audit_attempt(directory: Path, attempt_key: str, expected_receipt_sha256: str) -> dict:
    """Read-only bundle audit against a separately retained receipt digest.

    This verifies bytes, local execution order and contract consistency. It does
    not replace deterministic engine replay, lawful policy qualification, remote
    one-shot authority or independent admission of any capability/primary game.
    """
    _hash(attempt_key)
    root = Path(directory).absolute()
    receipt = _read_exact(root / (attempt_key + "-final.json"), expected_receipt_sha256)
    require(set(receipt) == {"schema", "attempt_key", "identity", "bindings_sha256",
        "journal_tail_sha256", "files", "execution_authorized_by_this_component",
        "engine_replay_qualified_by_this_component"} and receipt["schema"] == FINAL_SCHEMA,
        "Final receipt fields mismatch")
    require(receipt["attempt_key"] == attempt_key and
            receipt["execution_authorized_by_this_component"] is False and
            receipt["engine_replay_qualified_by_this_component"] is False,
            "Final evidence cannot self-authorize gameplay or engine replay")
    records = verify_journal(root / (attempt_key + ".jsonl"), expected_tail=receipt["journal_tail_sha256"])
    rows = [{k: v for k, v in record.items() if k not in _META} for record in records]
    require([row.get("kind") for row in rows] == ["ATTEMPT_BEFORE_INITIALIZATION",
        "INITIALIZATION_ATTEMPT", "INITIALIZED", "FINALIZED"], "Incomplete or invalid attempt sequence")
    b = rows[0]["bindings"]
    validate_bindings(b)
    require(rows[0] == {"schema": SCHEMA, "kind": "ATTEMPT_BEFORE_INITIALIZATION",
        "identity": _identity(b), "bindings": b, "execution_authorized_by_this_component": False},
        "Initial attempt claim mismatch")
    require(_digest(_identity(b)) == attempt_key and receipt["identity"] == _identity(b) and
            receipt["bindings_sha256"] == _digest(b), "Attempt identity or source binding mismatch")
    require(rows[1] == {"kind": "INITIALIZATION_ATTEMPT"}, "Initialization intent fields mismatch")
    files = receipt["files"]
    require(isinstance(files, dict) and set(files) == {"initial", "trace", "record"}, "Exact evidence bundle required")
    values = {}
    for role, binding in files.items():
        require(isinstance(binding, dict) and set(binding) == {"name", "sha256"} and
                binding["name"] == attempt_key + "-" + role + ".json", "Evidence filename binding mismatch")
        values[role] = _read_exact(root / binding["name"], binding["sha256"])
    initial = _snapshot(values["initial"])
    require(rows[2] == {"kind": "INITIALIZED", "initial_artifact_sha256": files["initial"]["sha256"],
        "initial_state_sha256": initial["initialStateSha256"]}, "Initialized record binding mismatch")
    trace, _ = _decode_trace(values["trace"])
    require(_snapshot({key: trace[key] for key in ("initialState", "initialStateSha256")}) == initial,
            "Trace initialization differs from the durable snapshot")
    record = import_trace(b, trace)
    require(record == values["record"], "Stored metrics differ from the exact trace import")
    audit = audit_record(record)
    require(rows[3] == {"kind": "FINALIZED", "files": files, "outcome_status": audit["outcome_status"],
        "record_sha256_bound": record["record_sha256"]}, "Final journal artifact or outcome binding mismatch")
    return {"status": "LOCAL_ATTEMPT_AND_TRACE_BINDING_VALID_ADMISSION_STILL_REQUIRED",
        "attempt_key": attempt_key, "receipt_sha256": expected_receipt_sha256,
        "journal_tail_sha256": receipt["journal_tail_sha256"], "outcome_status": audit["outcome_status"],
        "missing_mandatory_integrity": audit["missing_mandatory_integrity"],
        "engine_replay_required": True, "execution_allowed": False}
