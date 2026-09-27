"""Create-only files and serialized, hash-linked local evidence journals.

These primitives provide local filesystem durability, not remote execution authority.
A project must first validate its own frozen authorization and acquire its unique
repository claim. Retain the returned tail outside the journal to detect truncation.
No function retries a failed write, replaces a file, or repairs an incomplete attempt.
"""
from __future__ import annotations

import fcntl
import hashlib
import json
import os
from pathlib import Path
import stat
from typing import Any


class EvidenceError(RuntimeError):
    pass


_META = {"journal_sequence", "journal_key", "previous_sha256", "record_sha256"}


def canonical(value: dict[str, Any]) -> bytes:
    if not isinstance(value, dict):
        raise EvidenceError("evidence record must be a JSON object")
    return (json.dumps(value, sort_keys=True, separators=(",", ":"),
                       ensure_ascii=False, allow_nan=False) + "\n").encode("utf-8")


def _digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _write_all(fd: int, raw: bytes) -> None:
    view = memoryview(raw)
    while view:
        written = os.write(fd, view)
        if written <= 0:
            raise EvidenceError("incomplete evidence write; attempt remains consumed")
        view = view[written:]
    os.fsync(fd)


def _open_parent(path: Path) -> int:
    # A journal lives in a caller-owned directory. Reject path aliasing instead
    # of silently accepting a different storage location through a symlink.
    if path.parent.resolve(strict=True) != path.parent.absolute():
        raise EvidenceError("evidence parent must be a normalized nonsymlink directory")
    return os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)


def _check_link(path: Path, parent: int, fd: int) -> None:
    opened = os.fstat(fd)
    linked = os.stat(path.name, dir_fd=parent, follow_symlinks=False)
    parent_opened = os.fstat(parent)
    parent_linked = os.stat(path.parent, follow_symlinks=False)
    if (not stat.S_ISREG(opened.st_mode)
            or (opened.st_dev, opened.st_ino) != (linked.st_dev, linked.st_ino)
            or (parent_opened.st_dev, parent_opened.st_ino)
            != (parent_linked.st_dev, parent_linked.st_ino)):
        raise EvidenceError("evidence pathname changed while in use; do not retry")


def create_json_once(path: Path, payload: dict[str, Any]) -> str:
    """Persist exact JSON with O_EXCL; parent directory must already exist.

    Any error after exclusive creation leaves the file in place, preventing another
    attempt from silently replacing a partially written claim or artifact.
    """
    path = Path(path)
    raw = canonical(payload)
    parent = _open_parent(path)
    try:
        fd = os.open(path.name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                     0o600, dir_fd=parent)
        try:
            _check_link(path, parent, fd)
            _write_all(fd, raw)
            os.fsync(parent)
            _check_link(path, parent, fd)
        finally:
            os.close(fd)
    finally:
        os.close(parent)
    return _digest(raw)


def _record(payload: dict[str, Any], key: str, sequence: int,
            previous: str | None) -> dict[str, Any]:
    if not isinstance(key, str) or not key.strip():
        raise EvidenceError("a stable nonempty journal key is required")
    if not isinstance(payload, dict) or _META.intersection(payload):
        raise EvidenceError("payload must be an object without reserved journal fields")
    value = {**payload, "journal_sequence": sequence, "journal_key": key,
             "previous_sha256": previous}
    return {**value, "record_sha256": _digest(canonical(value))}


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise EvidenceError("duplicate JSON key in journal")
        result[key] = value
    return result


def _decode(raw: bytes, expected_tail: str) -> list[dict[str, Any]]:
    if not raw or not raw.endswith(b"\n"):
        raise EvidenceError("empty or incomplete journal; do not repair or retry")
    records = []
    previous = None
    keys: set[str] = set()
    for sequence, line in enumerate(raw.splitlines(), 1):
        try:
            record = json.loads(line, object_pairs_hook=_unique_object)
        except (ValueError, UnicodeError) as exc:
            raise EvidenceError("invalid journal JSON") from exc
        if not isinstance(record, dict):
            raise EvidenceError("journal record must be an object")
        key = record.get("journal_key")
        digest = record.get("record_sha256")
        if (type(record.get("journal_sequence")) is not int
                or record["journal_sequence"] != sequence
                or record.get("previous_sha256") != previous
                or not isinstance(key, str) or not key.strip() or key in keys):
            raise EvidenceError("journal order, chain, or unique-key violation")
        unsigned = {k: v for k, v in record.items() if k != "record_sha256"}
        if digest != _digest(canonical(unsigned)) or canonical(record) != line + b"\n":
            raise EvidenceError("journal record bytes or digest mismatch")
        keys.add(key)
        previous = digest
        records.append(record)
    if not isinstance(expected_tail, str) or previous != expected_tail:
        raise EvidenceError("journal tail differs from the caller's durable checkpoint")
    return records


def create_journal(path: Path, first_record: dict[str, Any], *, key: str) -> str:
    record = _record(first_record, key, 1, None)
    create_json_once(path, record)
    return record["record_sha256"]


def _read_all(fd: int) -> bytes:
    os.lseek(fd, 0, os.SEEK_SET)
    chunks = []
    while chunk := os.read(fd, 65536):
        chunks.append(chunk)
    return b"".join(chunks)


def verify_journal(path: Path, *, expected_tail: str) -> list[dict[str, Any]]:
    path = Path(path)
    parent = _open_parent(path)
    try:
        fd = os.open(path.name, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=parent)
        try:
            fcntl.flock(fd, fcntl.LOCK_SH)
            _check_link(path, parent, fd)
            records = _decode(_read_all(fd), expected_tail)
            _check_link(path, parent, fd)
            return records
        finally:
            os.close(fd)
    finally:
        os.close(parent)


def append_journal(path: Path, payload: dict[str, Any], *, key: str,
                   expected_tail: str) -> str:
    """Append once under an OS lock after verifying the exact caller-known tail.

    Concurrent callers starting from one tail cannot both append successfully.
    A duplicate allocation/phase key is rejected even with the current tail.
    """
    path = Path(path)
    parent = _open_parent(path)
    try:
        fd = os.open(path.name, os.O_RDWR | os.O_APPEND | os.O_NOFOLLOW, dir_fd=parent)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX)
            _check_link(path, parent, fd)
            existing = _decode(_read_all(fd), expected_tail)
            if any(record["journal_key"] == key for record in existing):
                raise EvidenceError("journal key already consumed; no retry permitted")
            record = _record(payload, key, len(existing) + 1, expected_tail)
            _write_all(fd, canonical(record))
            _check_link(path, parent, fd)
            return record["record_sha256"]
        finally:
            os.close(fd)
    finally:
        os.close(parent)
