"""Reusable create-only repository claim, extracted from the qualified Pest pattern.

This module has no CLI, credentials, seed access, or gameplay entry point. The
project owns its frozen payload, authorization and unique claim namespace. A
confirmed claim is a reservation only; it does not grant execution permission.
The historical Pest one-shot implementation remains byte-for-byte unchanged.
"""
from __future__ import annotations

import base64
from dataclasses import dataclass
import hashlib
import json
from pathlib import PurePosixPath
import re
from typing import Any, Protocol
from urllib.parse import quote


class ClaimError(RuntimeError):
    pass


class ApiError(ClaimError):
    def __init__(self, status: int):
        self.status = status
        super().__init__(f"HTTP {status}; never retry a claim mutation")


class RepositoryAPI(Protocol):
    """Client bound to exactly one repository; writes must have no retries."""
    repository: str

    def request(self, method: str, path: str, payload: dict | None = None) -> dict: ...


@dataclass(frozen=True)
class ClaimSpec:
    repository: str
    source_branch: str
    source_sha: str
    claim_ref: str
    claim_path: str


def _canonical(value: dict) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"),
                       ensure_ascii=False, allow_nan=False) + "\n").encode("utf-8")


def _sha(value: Any) -> str:
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{40}", value):
        raise ClaimError("full lowercase Git SHA required")
    return value


def _blob_sha(raw: bytes) -> str:
    return hashlib.sha1(f"blob {len(raw)}\0".encode() + raw).hexdigest()


def _validate(api: RepositoryAPI, spec: ClaimSpec) -> None:
    _sha(spec.source_sha)
    if api.repository != spec.repository or not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", spec.repository):
        raise ClaimError("repository binding mismatch")
    for value in (spec.source_branch, spec.claim_ref.removeprefix("refs/heads/")):
        if (not value or value.startswith("/") or value.endswith("/") or ".." in value
                or "@{" in value or "//" in value or re.search(r"[\s~^:?*\[\\]", value)
                or any(part.startswith(".") or part.endswith(".lock") for part in value.split("/"))):
            raise ClaimError("invalid branch or claim reference")
    if not spec.claim_ref.startswith("refs/heads/") or "/official-attempts/" not in spec.claim_ref:
        raise ClaimError("claim must use a project-scoped official-attempts reference")
    if spec.claim_ref == "refs/heads/" + spec.source_branch:
        raise ClaimError("claim cannot replace its source branch")
    path = PurePosixPath(spec.claim_path)
    if (not spec.claim_path or path.is_absolute() or ".." in path.parts
            or str(path) != spec.claim_path or "\\" in spec.claim_path):
        raise ClaimError("claim file must have a normalized repository-relative path")


def _live_source(api: RepositoryAPI, spec: ClaimSpec) -> None:
    ref = api.request("GET", "/git/ref/heads/" + quote(spec.source_branch, safe="/"))
    if ref.get("object", {}).get("sha") != spec.source_sha:
        raise ClaimError("source branch moved; no reservation or execution allowed")


def reserve_claim(api: RepositoryAPI, spec: ClaimSpec, payload: dict) -> dict:
    """Create the one canonical reservation reference without update/force/retry."""
    _validate(api, spec)
    if not isinstance(payload, dict) or not payload:
        raise ClaimError("project must supply its frozen nonempty claim payload")
    raw = _canonical(payload)
    endpoint = "/git/ref/" + spec.claim_ref.removeprefix("refs/")
    try:
        api.request("GET", endpoint)
    except ApiError as exc:
        if exc.status != 404:
            raise
    else:
        raise ClaimError("claim already exists, including failed or abandoned attempts")
    _live_source(api, spec)
    source = api.request("GET", f"/git/commits/{spec.source_sha}")
    if source.get("sha") != spec.source_sha:
        raise ClaimError("source commit mismatch")
    source_tree = _sha(source.get("tree", {}).get("sha"))
    blob = api.request("POST", "/git/blobs", {"content": raw.decode(), "encoding": "utf-8"})
    blob_sha = _sha(blob.get("sha"))
    if blob_sha != _blob_sha(raw):
        raise ClaimError("created blob differs from exact intended bytes")
    tree = api.request("POST", "/git/trees", {"base_tree": source_tree, "tree": [
        {"path": spec.claim_path, "mode": "100644", "type": "blob", "sha": blob_sha}]})
    tree_sha = _sha(tree.get("sha"))
    commit = api.request("POST", "/git/commits", {
        "message": "Reserve one frozen experimental attempt; no retry",
        "tree": tree_sha, "parents": [spec.source_sha]})
    commit_sha = _sha(commit.get("sha"))
    _live_source(api, spec)
    created = api.request("POST", "/git/refs", {"ref": spec.claim_ref, "sha": commit_sha})
    if created.get("ref") != spec.claim_ref or created.get("object", {}).get("sha") != commit_sha:
        raise ClaimError("ambiguous claim creation; preserve attempt and do not retry")
    receipt = {"schema": "argentum-repository-claim-receipt-v1", **spec.__dict__,
               "source_tree_sha": source_tree, "claim_commit_sha": commit_sha,
               "claim_tree_sha": tree_sha, "claim_blob_sha": blob_sha,
               "payload_sha256": hashlib.sha256(raw).hexdigest(), "execution_allowed": False}
    verify_claim(api, spec, payload, receipt)
    return receipt


def verify_claim(api: RepositoryAPI, spec: ClaimSpec, payload: dict, receipt: dict) -> None:
    """Read back source, ref, parent and exact payload bytes; never mutates."""
    _validate(api, spec)
    for key, expected in {"schema": "argentum-repository-claim-receipt-v1", **spec.__dict__,
                          "execution_allowed": False}.items():
        if type(receipt.get(key)) is not type(expected) or receipt[key] != expected:
            raise ClaimError(f"receipt binding mismatch: {key}")
    commit_sha = _sha(receipt.get("claim_commit_sha"))
    tree_sha = _sha(receipt.get("claim_tree_sha"))
    blob_sha = _sha(receipt.get("claim_blob_sha"))
    source_tree = _sha(receipt.get("source_tree_sha"))
    _live_source(api, spec)
    ref = api.request("GET", "/git/ref/" + spec.claim_ref.removeprefix("refs/"))
    if ref.get("ref") != spec.claim_ref or ref.get("object", {}).get("sha") != commit_sha:
        raise ClaimError("canonical claim reference mismatch")
    commit = api.request("GET", f"/git/commits/{commit_sha}")
    if (commit.get("sha") != commit_sha or commit.get("tree", {}).get("sha") != tree_sha
            or [p.get("sha") for p in commit.get("parents", [])] != [spec.source_sha]):
        raise ClaimError("claim commit, tree or parent mismatch")
    source = api.request("GET", f"/git/commits/{spec.source_sha}")
    if source.get("sha") != spec.source_sha or source.get("tree", {}).get("sha") != source_tree:
        raise ClaimError("source identity mismatch")
    file = api.request("GET", f"/contents/{quote(spec.claim_path, safe='/')}?ref={commit_sha}")
    if file.get("encoding") != "base64" or file.get("sha") != blob_sha or file.get("path") != spec.claim_path:
        raise ClaimError("claim file identity mismatch")
    try:
        raw = base64.b64decode("".join(file["content"].split()), validate=True)
    except (ValueError, KeyError, TypeError) as exc:
        raise ClaimError("invalid claim file bytes") from exc
    if (raw != _canonical(payload) or _blob_sha(raw) != blob_sha
            or hashlib.sha256(raw).hexdigest() != receipt.get("payload_sha256")):
        raise ClaimError("claim payload bytes mismatch")
    _live_source(api, spec)
