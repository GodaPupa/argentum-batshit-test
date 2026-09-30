#!/usr/bin/env python3
"""Recover exact accepted source08 watchdog process/Python identity. No Java, entropy, allocation or game."""
from __future__ import annotations
import hashlib, json, pathlib, sys, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[3]
ZIP = ROOT / "ferocity-recycling/evidence/build/m1-watchdog35-publication/watchdog35-actual-evidence.zip"
EXPECTED_ZIP_SHA = "86272408a5d4ea48e7cefcb20ad6833ee5889c452d0a5c5b7af7c3c1c9784e0f"
EXPECTED_ACCEPTANCE_SHA = "71eacdddb46c48ba7c3856391d0ebef175cf7a2d9e247b15b293c789fbcce27a"
EXPECTED_RUN_SHA = "d595b0c28041325378786eb39537073a7c029a993472e9d80c5b363b0bbb176d"
EXPECTED_NORMAL_CLAIM_SHA = "f9c8d6c0b33473b3856e990c2adbda50fedd1c4b67affe28982c2066e4a82fc2"
SOURCE_PATH = "ferocity-recycling/tools/trial_watchdog.py"

def sha_bytes(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()

def file_sha(p: pathlib.Path) -> str:
    with p.open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()

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

    assert acceptance["run_receipt_sha256"] == EXPECTED_RUN_SHA
    assert acceptance["source_guard_unchanged"] is True
    assert run["source_guard_unchanged"] is True
    assert run["returncode"] == 0
    totals = acceptance["fixed_process_cases"]
    assert totals == {"executed":13,"passed":13,"failed":0,"errors":0,"skipped":0}

    accepted_files = {
        str(pathlib.PurePosixPath(acceptance_name).parent / row["path"]): row["sha256"]
        for row in acceptance["files"]
    }
    claim_name = by_sha[EXPECTED_NORMAL_CLAIM_SHA]
    assert accepted_files.get(claim_name) == EXPECTED_NORMAL_CLAIM_SHA
    claim = json.loads(z.read(claim_name))
    assert claim["schema_version"] == 1
    assert claim["scope"] == "PROCESS_SUPERVISION_ONLY"

    source_sha = claim["supervisor_source_sha256"]
    source_file = ROOT / SOURCE_PATH
    assert source_file.is_file()
    assert file_sha(source_file) == source_sha

    py_path = pathlib.Path(claim["python_executable"])
    py_expected = claim["python_executable_sha256"]
    path_exists = py_path.is_file()
    observed_sha = file_sha(py_path) if path_exists else None
    accepted_python_matches = path_exists and observed_sha == py_expected

    current = pathlib.Path(sys.executable).resolve()
    current_sha = file_sha(current)
    author_in_current_archive = author_sha in by_sha

    status = (
        "WATCHDOG_PYTHON_IDENTITY_AVAILABLE_AUTHOR_RECEIPT_EXTERNAL"
        if accepted_python_matches
        else "BLOCKED_ACCEPTED_WATCHDOG_PYTHON_NOT_PRESENT_OR_MISMATCH"
    )
    result = {
      "schema":"ferocity-source08-watchdog-runtime-recovery-v2",
      "status":status,
      "source08Commit":"3a4f99a7653839506e96d19e6639f58d9e8c5ced",
      "watchdogArchive":{
        "path":str(ZIP.relative_to(ROOT)),
        "sha256":EXPECTED_ZIP_SHA,
        "members":len(names),
        "crcClean":True
      },
      "acceptance":{
        "member":acceptance_name,
        "sha256":EXPECTED_ACCEPTANCE_SHA,
        "fixedProcessCases":totals
      },
      "runReceipt":{
        "member":run_name,
        "sha256":EXPECTED_RUN_SHA,
        "authorReceiptSha256":author_sha
      },
      "authorReceipt":{
        "sha256":author_sha,
        "locatedInCurrentWatchdogArchive":author_in_current_archive,
        "disposition":"EXTERNAL_PRESERVED_EVIDENCE_MUST_BE_LOCATED_BEFORE_FINAL_MANIFEST"
      },
      "watchdogSource":{"path":SOURCE_PATH,"sha256":source_sha},
      "selectedAcceptedProcessClaim":{"member":claim_name,"sha256":EXPECTED_NORMAL_CLAIM_SHA},
      "acceptedPython":{"path":str(py_path),"sha256":py_expected},
      "runnerObservationAtAcceptedPath":{
        "pathExists":path_exists,
        "sha256":observed_sha,
        "matchesAccepted":accepted_python_matches
      },
      "workflowPython":{
        "path":str(current),
        "sha256":current_sha,
        "samePathAsAccepted":str(current) == str(py_path),
        "sameBytesAsAccepted":current_sha == py_expected
      },
      "nextGate":(
        "Locate the exact external author receipt, then bind these unchanged watchdog bytes into final manifest assembly."
        if accepted_python_matches
        else "Restore or separately qualify the exact accepted watchdog Python executable identity before final manifest assembly; also locate the external author receipt."
      ),
      "javaStarted":False,
      "gradleStarted":False,
      "entropyRead":False,
      "allocationRead":False,
      "gameInitialized":False
    }
    out=ROOT/"build/reports/ferocity-watchdog-recovery"
    out.mkdir(parents=True,exist_ok=True)
    (out/"recovery.json").write_text(json.dumps(result,indent=2,sort_keys=True)+"\n")
    print(json.dumps(result,sort_keys=True))
