#!/usr/bin/env python3
"""Phase-32 execution-readiness contracts.

This module can validate the exact frozen Phase-31 quarantine identity and can
exercise durable attempt/recovery semantics with synthetic fixtures. It contains
no official game initializer and no outcome writer.
"""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
from typing import Tuple

OFFICIAL_ARTIFACT_ID = 10627816776
OFFICIAL_ARTIFACT_ZIP_SHA256 = "83c0c75363ff0d9030cd1ed6f3441a201514ddb8920562aa2791b04fc4651f6a"
OFFICIAL_VECTOR_FILE_SHA256 = "9c9089fbe256652ffcb30363258409fe8fff9df33414216386faedb4be2d745f"
OFFICIAL_VECTOR_SHA256 = "5d8f9f758ed87286efb0ca07b74cec652a44304158e867f4c1aa6fc8a3bb824f"
OFFICIAL_ASSIGNMENT_SHA256 = "b2da95af015d1acd84bebd6794acad5c8a81530ccae74f41e6f925ef301e5be3"
CONTROL_SHA256 = "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
RUNNER_SHA256 = "864d46745c7ffd1c1e9c8eef48c5a0ad14b34938aa0a6066d94ab7a394516fc3"
OPPONENT_ID = "veteran-beastrider-commander-clash-2025-v1"
POSITION_COUNT = 12

class OpaqueSeed:
    __slots__ = ("_value",)
    def __init__(self, value: int):
        self._value = value
    def __repr__(self) -> str:
        return "OpaqueSeed(<redacted>)"
    __str__ = __repr__
    def reveal_for_authorized_execution(self, authorization: object) -> int:
        raise RuntimeError("Phase 32 does not authorize seed consumption")

@dataclass(frozen=True)
class FrozenPositionBinding:
    position: int
    assignment: str
    opaque_seed: OpaqueSeed

@dataclass(frozen=True)
class FreezeIdentity:
    artifact_id: int = OFFICIAL_ARTIFACT_ID
    artifact_zip_sha256: str = OFFICIAL_ARTIFACT_ZIP_SHA256
    vector_file_sha256: str = OFFICIAL_VECTOR_FILE_SHA256
    vector_sha256: str = OFFICIAL_VECTOR_SHA256
    assignment_sha256: str = OFFICIAL_ASSIGNMENT_SHA256
    control_sha256: str = CONTROL_SHA256
    runner_sha256: str = RUNNER_SHA256
    opponent_identity: str = OPPONENT_ID

def _sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def _digest_json(value) -> str:
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()

def validate_quarantine_payload(
    *,
    artifact_id: int,
    artifact_zip_sha256: str,
    vector_file_bytes: bytes,
    expected: FreezeIdentity = FreezeIdentity(),
) -> Tuple[FrozenPositionBinding, ...]:
    if artifact_id != expected.artifact_id:
        raise ValueError("wrong quarantine artifact id")
    if artifact_zip_sha256 != expected.artifact_zip_sha256:
        raise ValueError("wrong quarantine artifact zip digest")
    if _sha_bytes(vector_file_bytes) != expected.vector_file_sha256:
        raise ValueError("wrong quarantined vector file digest")
    payload=json.loads(vector_file_bytes)
    if payload.get("schema")!="izzet-v09-phase31-quarantined-seed-vector-v1":
        raise ValueError("wrong quarantine payload schema")
    values=payload.get("values")
    assignments=payload.get("assignments")
    if not isinstance(values,list) or not isinstance(assignments,list):
        raise ValueError("quarantine payload malformed")
    if len(values)!=POSITION_COUNT or len(assignments)!=POSITION_COUNT:
        raise ValueError("quarantine payload must bind exactly 12 positions")
    if len(set(values))!=POSITION_COUNT:
        raise ValueError("duplicate seed in frozen vector")
    if any(type(v) is not int or v==0 or v<-(1<<63) or v>(1<<63)-1 for v in values):
        raise ValueError("invalid signed-64 seed in frozen vector")
    if assignments.count("play")!=6 or assignments.count("draw")!=6:
        raise ValueError("frozen assignment vector is not 6/6")
    if any(v not in {"play","draw"} for v in assignments):
        raise ValueError("invalid assignment label")
    vector_sha=hashlib.sha256(json.dumps(values,separators=(",",":")).encode()).hexdigest()
    if vector_sha!=expected.vector_sha256:
        raise ValueError("wrong frozen vector digest")
    if _digest_json(assignments)!=expected.assignment_sha256:
        raise ValueError("wrong frozen assignment digest")
    return tuple(
        FrozenPositionBinding(i+1,assignments[i],OpaqueSeed(values[i]))
        for i in range(POSITION_COUNT)
    )

def validate_binding_set(bindings: Tuple[FrozenPositionBinding, ...]) -> None:
    if tuple(b.position for b in bindings)!=tuple(range(1,POSITION_COUNT+1)):
        raise ValueError("missing or duplicate frozen positions")
    if tuple(b.assignment for b in bindings).count("play")!=6:
        raise ValueError("binding set play count changed")
    if tuple(b.assignment for b in bindings).count("draw")!=6:
        raise ValueError("binding set draw count changed")

class DurableAttemptJournal:
    """Synthetic/opaque readiness journal. No game execution is implemented."""

    def __init__(self, root: Path):
        self.root=Path(root)
        self.root.mkdir(parents=True,exist_ok=True)
        self._fsync_dir()

    def _fsync_dir(self):
        fd=os.open(self.root,os.O_RDONLY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)

    def _write_once(self, name: str, content: str):
        path=self.root/name
        fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        try:
            data=content.encode()
            os.write(fd,data)
            os.fsync(fd)
        finally:
            os.close(fd)
        self._fsync_dir()

    def attempt(self, position: int):
        if position not in range(1,POSITION_COUNT+1):
            raise ValueError("position out of range")
        if self.has_terminal_rejection():
            raise RuntimeError("journal is terminally rejected")
        if self.is_attempted(position):
            raise FileExistsError("position already attempted")
        expected=1+sum(self.is_attempted(i) for i in range(1,POSITION_COUNT+1))
        if position!=expected:
            raise RuntimeError("positions must be attempted sequentially")
        self._write_once(f"attempt-{position:02d}.marker",f"phase32-opaque-attempt|position={position}\n")

    def consume_marker(self, position: int):
        if not self.is_attempted(position):
            raise RuntimeError("cannot consume before durable attempt")
        if self.is_consumed(position):
            raise FileExistsError("position already consumed")
        self._write_once(f"consumed-{position:02d}.marker",f"phase32-opaque-consumed|position={position}\n")

    def complete(self, position: int):
        if not self.is_consumed(position):
            raise RuntimeError("cannot complete before consumption marker")
        self._write_once(f"complete-{position:02d}.marker",f"phase32-opaque-complete|position={position}\n")

    def reject_terminal(self, position: int, reason: str):
        if not self.is_attempted(position):
            raise RuntimeError("cannot reject before attempt")
        if self.has_terminal_rejection():
            raise FileExistsError("terminal rejection already exists")
        self._write_once("terminal-rejection.marker",f"position={position}|reason={reason}\n")

    def recover(self):
        attempted=[i for i in range(1,POSITION_COUNT+1) if self.is_attempted(i)]
        complete=[i for i in range(1,POSITION_COUNT+1) if self.is_complete(i)]
        incomplete=[i for i in attempted if i not in complete]
        if not incomplete:
            return "clean"
        if len(incomplete)!=1 or incomplete[0]!=attempted[-1]:
            raise RuntimeError("journal corruption: nonterminal incomplete attempt")
        pos=incomplete[0]
        if not self.has_terminal_rejection():
            self.reject_terminal(pos,"crash-recovery")
        return "terminal-rejected"

    def is_attempted(self,p): return (self.root/f"attempt-{p:02d}.marker").exists()
    def is_consumed(self,p): return (self.root/f"consumed-{p:02d}.marker").exists()
    def is_complete(self,p): return (self.root/f"complete-{p:02d}.marker").exists()
    def has_terminal_rejection(self): return (self.root/"terminal-rejection.marker").exists()

def synthetic_initialize_after_attempt(
    journal: DurableAttemptJournal,
    position: int,
    synthetic_initializer,
):
    """Qualification-only hook proving attempt exists before synthetic init callback."""
    journal.attempt(position)
    assert journal.is_attempted(position)
    return synthetic_initializer(position)
