#!/usr/bin/env python3
"""Recover exact accepted source08 watchdog process identity. No Java, entropy, allocation or game."""
from __future__ import annotations
import hashlib, json, pathlib, sys, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[3]
ZIP = ROOT / "ferocity-recycling/evidence/build/m1-watchdog35-publication/watchdog35-actual-evidence.zip"
EXPECTED_ZIP_SHA = "86272408a5d4ea48e7cefcb20ad6833ee5889c452d0a5c5b7af7c3c1c9784e0f"
EXPECTED_ACCEPTANCE_SHA = "71eacdddb46c48ba7c3856391d0ebef175cf7a2d9e247b15b293c789fbcce27a"
EXPECTED_RUN_SHA = "d595b0c28041325378786eb39537073a7c029a993472e9d80c5b363b0bbb176d"

def sha_bytes(b: bytes) -> str: return hashlib.sha256(b).hexdigest()
def file_sha(p: pathlib.Path) -> str:
    with p.open("rb") as f: return hashlib.file_digest(f, "sha256").hexdigest()

assert file_sha(ZIP) == EXPECTED_ZIP_SHA
with zipfile.ZipFile(ZIP) as z:
    assert z.testzip() is None
    names = z.namelist()
    by_sha = {sha_bytes(z.read(n)): n for n in names if not n.endswith("/")}
    acceptance_name = by_sha[EXPECTED_ACCEPTANCE_SHA]
    run_name = by_sha[EXPECTED_RUN_SHA]
    acceptance = json.loads(z.read(acceptance_name))
    run = json.loads(z.read(run_name))
    author_sha = run["author_receipt_sha256"]
    author_name = by_sha[author_sha]
    author = json.loads(z.read(author_name))
    assert acceptance["run_receipt_sha256"] == EXPECTED_RUN_SHA
    assert acceptance["source_guard_unchanged"] is True and run["source_guard_unchanged"] is True
    assert run["returncode"] == 0
    totals = acceptance["fixed_process_cases"]
    assert totals == {"executed":13,"passed":13,"failed":0,"errors":0,"skipped":0}

    accepted_files = {
        str(pathlib.PurePosixPath(acceptance_name).parent / row["path"]): row["sha256"]
        for row in acceptance["files"]
    }
    claims = []
    for n, expected in accepted_files.items():
        if n not in names or sha_bytes(z.read(n)) != expected: continue
        try: obj = json.loads(z.read(n))
        except Exception: continue
        if obj.get("schema_version") == 1 and obj.get("scope") == "PROCESS_SUPERVISION_ONLY":
            claims.append((n, expected, obj))
    assert claims, "No accepted process-supervision claim"
    # Require one claim whose source is the exact tested author-owned repository watchdog source.
    owned = {row["path"]: row["sha256"] for row in author["owned_files"]}
    source_path = "ferocity-recycling/tools/trial_watchdog.py"
    assert source_path in owned
    source_sha = owned[source_path]
    assert file_sha(ROOT / source_path) == source_sha
    matching = [(n,h,o) for n,h,o in claims if o.get("supervisor_source_sha256") == source_sha]
    assert matching
    matching.sort(key=lambda x: x[0])
    claim_name, claim_sha, claim = matching[0]
    py_path = pathlib.Path(claim["python_executable"])
    py_expected = claim["python_executable_sha256"]
    observed = {
        "pathExists": py_path.is_file(),
        "sha256": file_sha(py_path) if py_path.is_file() else None,
        "matchesAccepted": py_path.is_file() and file_sha(py_path) == py_expected,
    }
    current = pathlib.Path(sys.executable).resolve()
    result = {
      "schema":"ferocity-source08-watchdog-runtime-recovery-v1",
      "source08Commit":"3a4f99a7653839506e96d19e6639f58d9e8c5ced",
      "watchdogArchive":{"path":str(ZIP.relative_to(ROOT)),"sha256":EXPECTED_ZIP_SHA,"members":len(names),"crcClean":True},
      "acceptance":{"member":acceptance_name,"sha256":EXPECTED_ACCEPTANCE_SHA,"fixedProcessCases":totals},
      "runReceipt":{"member":run_name,"sha256":EXPECTED_RUN_SHA},
      "authorReceipt":{"member":author_name,"sha256":author_sha},
      "watchdogSource":{"path":source_path,"sha256":source_sha},
      "selectedAcceptedProcessClaim":{"member":claim_name,"sha256":claim_sha},
      "acceptedPython":{"path":str(py_path),"sha256":py_expected},
      "runnerObservationAtAcceptedPath":observed,
      "workflowPython":{"path":str(current),"sha256":file_sha(current)},
      "javaStarted":False,"gradleStarted":False,"entropyRead":False,"allocationRead":False,"gameInitialized":False
    }
    out=ROOT/"build/reports/ferocity-watchdog-recovery"
    out.mkdir(parents=True,exist_ok=True)
    (out/"recovery.json").write_text(json.dumps(result,indent=2,sort_keys=True)+"\n")
    print(json.dumps(result,sort_keys=True))
