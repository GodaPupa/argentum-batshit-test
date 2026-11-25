# Hosted receiver and immutable readback integration

These operational files are finalized for the new authorized `B_HOSTED_ONCE` grant and frozen in carrier C before activation. They belong only at the new namespace; all earlier helpers remain unchanged.

## Helper files

- `receive_hosted.py`: run exactly once after original transport, with the restored S3 worktree. The original `receive()` body is byte-identical to the frozen original source. The adapter checks the new authority, the exact original runtime, readiness, and actual hosted semantics before delegating the raw audit. It produces original `RECEIPT.json` plus `HOSTED_RECEIPT.json`; never change either after creation.
- `immutable_hosted_readback.py`: read-only Git object and live-ref verification. It reconstructs original bytes from canonical parts, validates independently pinned REST metadata, raw manifests and XML, and executes only frozen read-only audits. It never invokes a qualifier, receiver, build or download.

## Metadata and publication

META and EXPECTED are the original 38 fields plus `selected_plan: B_HOSTED_ONCE`, `execution_environment: github_hosted`, `local_pass: false`. Each must have exactly 41 fields. The actual lane is `ci`, and attempt ID is `manual-host-requalification-20261124-01/ci-01`.

Before the only transport, commit D adds `CI_RECEIVE_PLAN_01/EXPECTED.json`, `GITHUB_RUN.json`, `GITHUB_JOB.json`, and `GITHUB_ARTIFACT.json` and sets new CURRENT status `ORIGINAL_ARTIFACT_DOWNLOAD_RESERVED`. D counters are activation/readiness_reserved/qualification_reserved/downloads one, original_artifact_receives zero, local_started zero; runtime counters are observed bounded zero-or-one values. Preserve C's `source_freeze` exactly. D's `ci_receiving_reservation` has exactly these keys:

```
run_id, job_id, artifact_id, artifact_digest, artifact_size,
attempt_id, prior_receipt_count, original_artifact_downloads,
expected_path, expected_sha256
```

`expected_path` is newNS/CI_RECEIVE_PLAN_01/EXPECTED.json. `prior_receipt_count` is integer zero; the one original artifact download is reserved as integer one. Actual REST data is captured before download and not derived from the ZIP.

### Honest unobserved runtime counts before transport

If the actual runtime cannot yet be established before the original artifact download, D may set any of `attempts.readiness_started`, `readiness_completed`, `qualification_started`, and `qualification_completed` to JSON `null`, **only with** top-level `runtime_counts_pending_original_artifact: true`. Null means unobserved, never zero or a refunded allowance. Non-null values remain strict integer zero or one; booleans and other numbers are rejected. All activation/reservation/download/receive/local counters retain their exact requirements above.

After the original artifact is received, the published CURRENT must set `runtime_counts_pending_original_artifact: false` and all four actual fields to strict integers matching the genuine original `HOSTED_RUN.json`: `readiness_sequences_started`, `readiness_sequences_completed`, `qualification_starts`, and `qualification_completed`, respectively. Readback checks this reconciliation and also checks every already-known non-null D value against the original receipt.

Publish newNS/RECEIVED_CI_01 with at least `ARCHIVE_PARTS.json`, `RECEIPT.json`, `HOSTED_RECEIPT.json`, `META.json`, `EXPECTED.json`, `GITHUB_RUN.json`, `GITHUB_JOB.json`, `GITHUB_ARTIFACT.json`, `RECEIVING_PROVENANCE.json`, and original part files. Publish only the exact original ZIP parts, never repack it. The final published CURRENT has hosted_activations/original_artifact_downloads/original_artifact_receives one, local_started zero and admitted false.

`RECEIVING_PROVENANCE.json` schema is `manual-transmission/hosted-ci-receiving-provenance/v1`; retain the former structure:

- `download_reservation`: D commit, current_path, exact current_sha256, actual run/job/artifact IDs, original_artifact_downloads=1.
- `expected`: `{commit: D, path: newNS/CI_RECEIVE_PLAN_01/EXPECTED.json, sha256: actual SHA}`.
- `api_observations`: `run`, `job`, `artifact`, each the same `{commit,path,sha256}` binding for its full REST-shaped file in D.
- `receiver`: `{commit: C, path: newNS/receive_hosted.py, blob: actual frozen receiver blob, sha256: actual frozen receiver SHA}`.

Readback requires the actual job labels to include `ubuntu-24.04` and exclude `self-hosted`; no made-up provider fields.

## Outcomes

A successful hosted readback is `ORIGINAL_HOSTED_PASS_READBACK_VERIFIED_ADMISSION_PENDING`; local_pass always remains false. Original historical `ALL_LOCAL_GATES_PASS_ORIGINAL_RECEIVING_REQUIRED` inside qualification/RESULT.json is preserved and used solely as the frozen qualifier's completion value.

Readiness failure before qualification retains genuine original runtime evidence under the outer manifest and must have no fabricated qualification directory or inner manifest. Raw receiver state remains failed. Successful integrity readback of this failure is `ORIGINAL_HOSTED_FAILURE_EVIDENCE_READBACK_VERIFIED`, still exit 1, still no admission.

An activation/setup failure before readiness is also preservable as `ACTIVATION_OR_SETUP_FAILED_ORIGINAL_RUNTIME_EVIDENCE_PRESERVED`: zero readiness/qualification starts and completions, original event/run bound to actual provider metadata, available source/job/envelope/contract fields verified, genuinely missing fields listed in `missing_context`, and no invented envelope, readiness PASS, qualification directory or inner manifest.

After a real qualification starts, both genuine manifests are mandatory. The independent immutable raw auditor is attempted even if original qualification fails or its reports are partial. A failed audit is retained in the readback report. It cannot become a qualification pass.

The raw ZIP must already be preserved outside the new receiving directory before configure/metadata checks, because original orchestration validates identity before creating the receipt destination. No retry follows any failure.
