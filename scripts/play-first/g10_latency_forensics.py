#!/usr/bin/env python3
"""Read-only G10 original-artifact forensics. Never imports or initializes an engine.

This is an excluded diagnostic utility. It verifies the original artifact's
integrity and infers timing boundaries from the durable outer-call journal;
those boundaries CANNOT attribute time inside legal enumeration or search.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import re
import stat
import sys
import zipfile

ARTIFACT_ID = 11931191541
ARTIFACT_SHA256 = "6e53c39bc1b603d2b4421d774ee8538e00fb3e511cd38ac6d3940ade40126b86"
RUN_ID = 38628025826
SOURCE_SHA = "6d5c84b4e892603b356b68e29010fa41eebfe72d"
CASE_SHA256 = "c1168d447f1160058ac3fd75cb4018c66ce8a7046c99ecfef8d991c1bfb55d9b"
MANIFEST_NAME_RE = re.compile(r"manifest-[0-9a-f]{64}\.json\Z")


class EvidenceError(ValueError):
    """Refusal: artifact or chronology cannot support the requested conclusion."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise EvidenceError(message)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def jsonl(data: bytes, label: str) -> list[dict]:
    require(data.endswith(b"\n"), f"{label}: missing final newline (possible truncation)")
    try:
        parsed = [json.loads(x) for x in data.splitlines()]
    except (ValueError, UnicodeDecodeError) as exc:
        raise EvidenceError(f"{label}: invalid JSONL") from exc
    require(bool(parsed) and all(type(x) is dict for x in parsed), f"{label}: empty/non-object record")
    return parsed


def verify_zip_members(z: zipfile.ZipFile) -> dict:
    entries = z.infolist()
    names = [item.filename for item in entries]
    require(len(names) == len(set(names)), "duplicate archive member")
    for i in entries:
        p = i.filename
        require(p and not p.startswith("/") and "\\" not in p and not any(x in (".", "..", "") for x in p.split("/")), f"unsafe member path: {p!r}")
        require(not i.is_dir(), f"unexpected directory member: {p}")
        require(not stat.S_ISLNK(i.external_attr >> 16), f"symlink refused: {p}")
    require(z.testzip() is None, "archive CRC mismatch")
    manifests = [x for x in names if MANIFEST_NAME_RE.fullmatch(x)]
    require(len(manifests) == 1, "expected exactly one outer manifest")
    manifest = json.loads(z.read(manifests[0]))
    require(type(manifest) is dict and type(manifest.get("payloads")) is list, "invalid outer manifest schema")
    entries = manifest["payloads"]
    seen = set()
    for row in entries:
        require(type(row) is dict and set(row) == {"path", "bytes", "sha256"}, "invalid payload record")
        path = row["path"]
        require(type(path) is str and path not in seen and path in names, f"missing/duplicate manifest payload: {path}")
        seen.add(path)
        content = z.read(path)
        require(len(content) == row["bytes"], f"payload length mismatch: {path}")
        require(digest(content) == row["sha256"], f"payload SHA-256 mismatch: {path}")
    # The original outer inventory deliberately does not hash the nested preflight
    # manifest itself. Verify the nested manifest's seven payloads independently.
    nested = [x for x in names if re.fullmatch(r"preflight-received/manifest-[0-9a-f]{64}\.json", x)]
    require(len(nested) == 1 and seen == set(names) - set(manifests) - set(nested),
            "unmanifested/omitted archive members (except required nested preflight manifest)")
    child = json.loads(z.read(nested[0]))
    require(type(child) is dict and type(child.get("payloads")) is list and len(child["payloads"]) == 7,
            "invalid nested preflight manifest")
    seen_child = set()
    for row in child["payloads"]:
        require(type(row) is dict and set(row) == {"path", "bytes", "sha256"}, "invalid nested manifest record")
        nested_path = "preflight-received/" + row["path"]
        require(nested_path in seen and nested_path not in seen_child, f"nested payload absent or duplicate: {nested_path}")
        seen_child.add(nested_path)
        payload = z.read(nested_path)
        require(len(payload) == row["bytes"] and digest(payload) == row["sha256"], f"nested payload mismatch: {nested_path}")
    require(child.get("stage") == "ORIGINAL_EVIDENCE_INVENTORY_ONLY" and child.get("gameplayAuthorized") is False,
            "invalid nested preflight scope")
    require(manifest.get("gameplayAuthorized") is False and manifest.get("stage") == "ORIGINAL_EVIDENCE_INVENTORY_ONLY", "unexpected inventory scope")
    return {"manifest": manifests[0], "payloads_verified": len(entries), "nested_manifest": nested[0], "nested_payloads_verified": len(seen_child), "crc_ok": True}


def pair_spans(timing: list[dict]) -> tuple[list[dict], list[dict]]:
    starts: dict[int, dict] = {}
    finished: set[int] = set()
    complete: list[dict] = []
    for row in timing:
        kind = row.get("recordType")
        span = row.get("span")
        require(type(span) is int and span >= 1, "invalid timing span")
        if kind == "CALL_START":
            require(span not in starts and span not in finished, f"duplicate call start: {span}")
            starts[span] = row
        else:
            require(kind in ("CALL_END", "CALL_ERROR"), f"unknown timing record: {kind}")
            require(span in starts and span not in finished, f"orphan/duplicate completion: {span}")
            prior = starts[span]
            require(row.get("phase") == prior.get("phase") and row.get("context") == prior.get("context"), f"span {span} boundary identity mismatch")
            elapsed = row.get("elapsedNanos")
            require(type(elapsed) is int and elapsed >= 0, "negative/invalid elapsed duration")
            require(row.get("monotonicNanos", 0) >= prior.get("monotonicNanos", 0), "negative monotonic interval")
            require(row["monotonicNanos"] - prior["monotonicNanos"] == elapsed, "elapsed/monotonic mismatch")
            finished.add(span)
            complete.append({"span": span, "phase": row["phase"], "context": prior["context"], "seconds": round(elapsed / 1e9, 6), "outcome": kind, "started_at": prior["wallTimeUtc"], "finished_at": row["wallTimeUtc"]})
    pending = [v for k,v in starts.items() if k not in finished]
    require(len(starts) == len(complete) + len(pending), "timing accounting mismatch")
    return complete, pending


def audit_actions(records: list[dict]) -> dict:
    intent = [r for r in records if r.get("recordType") == "ACTION_INTENT"]
    result = [r for r in records if r.get("recordType") == "ACTION_RESULT"]
    seq_intent = [r.get("sequence") for r in intent]
    seq_result = [r.get("sequence") for r in result]
    require(seq_intent == list(range(1, len(intent)+1)), "action intent sequence discontinuity")
    require(seq_result == seq_intent, "action result missing/out of order")
    require(all(x.get("accepted") is True for x in result), "rejected action in original")
    require(not any(x.get("recordType") == "EXHIBITION_TERMINAL" for x in records), "terminal result exists; do not classify incomplete")
    require(records[0].get("recordType") == "EXHIBITION_INTENT", "no original scope record")
    require(any(x.get("recordType") == "INITIALIZED" for x in records), "engine initialization not evidenced")
    original = records[0]
    require(original.get("gameId") == "G10" and original.get("sourceCommit") == SOURCE_SHA and original.get("scope") == "NONOFFICIAL_PLAY_FIRST_EXHIBITION_ONLY", "original identity/scope mismatch")
    require(original.get("caseInputSha256") == CASE_SHA256, "original case identity mismatch")
    require(len(intent) == 688, "unexpected G10 action count")
    require(result[-1].get("gameOverAfter") is False and result[-1].get("turnAfter") == 26, "last result inconsistent with incomplete state")
    return {"accepted_action_count": len(result), "last_sequence": result[-1]["sequence"], "last_turn": result[-1]["turnAfter"], "last_life_totals": result[-1].get("lifeAfter"), "terminal_record_present": False, "initialized_record_present": True}


def analyze_archive(path: Path, *, expected_sha: str = ARTIFACT_SHA256) -> dict:
    data = path.read_bytes()
    require(digest(data) == expected_sha, "ZIP SHA-256 differs from original GitHub artifact")
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        zip_receipt = verify_zip_members(z)
        require(digest(z.read("preflight-received/case.json")) == CASE_SHA256, "case bytes mismatch")
        trace = jsonl(z.read("original.jsonl"), "original.jsonl")
        timing = jsonl(z.read("timing.jsonl"), "timing.jsonl")
        actions = audit_actions(trace)
        complete, pending = pair_spans(timing)
    require(len(complete) == 1380 and len(pending) == 1, "unexpected G10 timing span counts")
    last = pending[0]
    require(last.get("phase") == "CHOOSE_ACTION" and last.get("context", {}).get("nextSequence") == "689" and last.get("context", {}).get("turn") == "26" and last.get("context", {}).get("seat") == "1", "final unmatched span is not selected G10 decision")
    require(last.get("span") == 1381, "unexpected last timing span ID")
    slow = sorted((x for x in complete if x["phase"] == "CHOOSE_ACTION"), key=lambda x:x["seconds"], reverse=True)
    expected_slow = {"491":(20,39,41),"566":(22,1200,1203),"620":(24,611,614)}
    for sequence,(turn,lower,upper) in expected_slow.items():
        hits = [x for x in slow if x["context"].get("nextSequence") == sequence]
        require(len(hits)==1 and x_context_turn(hits[0])==turn and lower <= hits[0]["seconds"] <= upper, f"slow decision {sequence} mismatch")
    phases = Counter(x["phase"] for x in complete)
    return {
        "classification": "CANCELLED_INCOMPLETE_NONOFFICIAL_NO_WINNER",
        "original": {"run_id":RUN_ID,"attempt":1,"artifact_id":ARTIFACT_ID,"artifact_zip_sha256":digest(data),"source_sha":SOURCE_SHA,"case_sha256":CASE_SHA256},
        "integrity":zip_receipt,
        "game":actions,
        "outer_timing": {"completed_spans":len(complete),"pending_spans":len(pending),"completed_phases":dict(sorted(phases.items())),"slowest_completed_choose_action":[{"sequence":int(x["context"].get("nextSequence",0)),"turn":x_context_turn(x),"seat":int(x["context"].get("seat",-1)),"seconds":x["seconds"]} for x in slow[:6]],"unfinished_span":{"span":last["span"],"started_at":last["wallTimeUtc"],"phase":last["phase"],"context":last["context"]}},
        "attribution_limit":"Outer call boundaries cannot distinguish enumeration, mana calculation, target selection, candidate simulation, or rollout; root cause remains unproven.",
        "execution_scope":"READ_ONLY_OFFLINE_FORENSICS; NO_ENGINE_OR_GAMEPLAY",
    }


def x_context_turn(x: dict) -> int:
    return int(x["context"].get("turn",-1))


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--artifact",type=Path,required=True,help="The exact original GitHub ZIP for artifact 11931191541")
    ap.add_argument("--output",type=Path,help="Optional local JSON receipt (never uploads to GitHub)")
    args = ap.parse_args(argv)
    try:
        result = analyze_archive(args.artifact)
    except (EvidenceError, OSError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
        print(f"REFUSED: {exc}",file=sys.stderr)
        return 2
    body = json.dumps(result,indent=2,sort_keys=True) + "\n"
    if args.output:
        args.output.write_text(body,encoding="utf-8")
    sys.stdout.write(body)
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
