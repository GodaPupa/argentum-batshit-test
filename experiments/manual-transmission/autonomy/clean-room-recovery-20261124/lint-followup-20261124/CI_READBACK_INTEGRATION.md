# Prospective CI original-artifact readback

The draft helper is `immutable_ci_readback.py`, 51555 bytes, SHA256 `afe89b678e00dfb231fc02fbb76fc6cdc777ec1ae3fa28ab2f13d23a10f32410`. It has been parsed and reviewed as source only. No invocation, raw auditor execution, receiving, qualification, artifact download, CI activation, repository mutation, publication, or Git-object creation occurred during drafting.

## Freeze before conditional CI activation

Copy the reviewed helper into the new operational namespace at:

`experiments/manual-transmission/autonomy/clean-room-recovery-20261124/lint-followup-20261124/immutable_ci_readback.py`

Its exact executing bytes must be present in the original CI carrier C. This proves prospective freezing without adding another `source_freeze` field. The CI freeze still adds exactly the existing eight fields to the original local qualification object.

The helper already contains the actual S2, tree, pins, auditor, verifier, original qualifier/receiver, new receiver, CI template, local freeze and charter identities supplied by root. No placeholders remain. Future C/V/E/D, run/job/artifact IDs, digest/size, receipt publication commit and live head come from independently published metadata and explicit CLI arguments.

| Fixed input | Exact value |
|---|---|
| S2 | 82ec335573572edfd2a2e4d734d2f0d81801139f |
| S2 tree | 923ec79e1c867eedf35b408153bf60988fbd26b6 |
| S2 pins blob | 7e30df16c53a59358d507733b2a5443e2a3c9075 |
| New receiver SHA256 | 93a44ed39d2d75996cda6629f9b477ae343c06c1c633d744325928354a5d3ee5 |
| Workflow SHA256 | 27952f8595294538394573675f6f41c25c7487996aabbd622a390614810b83ce |
| Original local-freeze SHA256 | 9311d1579f8de17772aa139073602999999d343bc1e9083b7d45f0719828902d |

The source commit remains the exact five-file successor. This helper is an operational file and is not added to S2 or its378 binding set.

## Required publication records

Use the agreed new namespace N and original received directory `N/RECEIVED_CI_01`. Publish the exact original receiver outputs:

- `ARCHIVE_PARTS.json`, with the original `{bytes,sha256,parts:[{file,bytes,sha256}]}` schema.
- Ordered `original.partNNNN.b64` files, each450000 decoded bytes except the last, with canonical base64 and one final newline.
- Original `RECEIPT.json`, unchanged, with schema `manual-transmission/surge-original-receipt/v1`.
- `META.json` and `EXPECTED.json`, each containing exactly the agreed38 metadata fields.
- Full REST-shaped original `GITHUB_RUN.json`, `GITHUB_JOB.json` and `GITHUB_ARTIFACT.json` observations.
- `RECEIVING_PROVENANCE.json`, using the agreed `manual-transmission/lint-ci-receiving-provenance/v1` contract.

The metadata, observations and provenance are outside the untouched original artifact ZIP. Additional transport logs and publication records may also be preserved. The helper verifies that every file present at the immutable receipt publication remains unchanged at the expected live head; later additive readback records are reported separately.

## Independent pre-transport expectation

Before transport, the single download-claim commit D must pin `EXPECTED.json` and the three full API observation files under `N/CI_RECEIVE_PLAN_01`. RECEIVING_PROVENANCE binds each file's exact commit/path/SHA256; the copies in RECEIVED_CI_01 must be byte-identical. The newly received directory must be absent at D.

D CURRENT must have status `ORIGINAL_ARTIFACT_DOWNLOAD_RESERVED`, all preflight/local counters1, CI activated1, downloads1, the exact CI source freeze, the approved foreground lease, and unchanged safeguards. Its exact agreed tuple is:

```json
{
  "ci_receiving_reservation": {
    "attempt_id": "manual-lint-followup-20261124-01/ci-01",
    "run_id": "ACTUAL_POSITIVE_INTEGER",
    "job_id": "ACTUAL_POSITIVE_INTEGER",
    "artifact_id": "ACTUAL_POSITIVE_INTEGER",
    "artifact_digest": "ACTUAL_API_SHA256_PREFIXED_DIGEST",
    "artifact_size": "ACTUAL_POSITIVE_INTEGER",
    "prior_receipt_count": 0,
    "original_artifact_downloads": 1,
    "expected_path": "N/CI_RECEIVE_PLAN_01/EXPECTED.json",
    "expected_sha256": "ACTUAL_EXPECTED_FILE_SHA256"
  }
}
```

The strings marking numeric placeholders above must be replaced by actual JSON integers. Provenance separately binds D's CURRENT hash and run/job/artifact tuple, and C's exact new receiver commit/path/blob/SHA256. Keep any additional transport details in other fields of provenance or separate records; the CURRENT tuple and38 metadata fields are exact contracts.

## Readback checks

The helper verifies immutable S2's sole parent, exact five-file delta, pins and all378 Git source bindings. It then checks the original ordered E parents `[C,V]`, V's sole S2 parent, workflow-only activation/validation differences, exact workflow and receiver bytes, original local freeze, and actual local pass/readback prerequisite. The latter is read from `N/LOCAL_QUALIFICATION_01/LOCAL_RECEIPT.json` and `READBACK_VERIFICATION.json` in preserved carrier history; its schema, state, flags, source, receipt commit and archive hash must agree.

Run/job/artifact identity is checked against the independently pinned observations. The run's repository, branch, event SHA, workflow path, push event, attempt1 and run number1 are mandatory. The job must be the exact `verify` job of that run and event. If REST also supplies the redundant job branch or attempt fields, they must agree; absence of those redundant fields is covered by mandatory job.run_id/head_sha and the exact run's branch/sole-attempt proof. The artifact must have the actual run-linked name, digest, size, event, branch and available-at-observation status. Successful admission readback requires actual run and job conclusions both `success`.

The original ZIP is reconstructed from the published parts without repacking. It must match both the exact index and the independently published API digest/size. ZIP inspection enforces unique safe paths, regular files or empty directory entries, no symlinks/encryption or file-prefix collisions, exact member sizes and CRC. Both unchanged manifests must describe exactly their original file sets and hashes.

The helper verifies the original receiver's receipt/member map, all378 archived frozen-source files, exact S2 pins, the exclusive `lint_followup` envelope, final restoration,15 compile forms and original qualifier result. It requires the original qualifier audit to equal the receiver audit before a fresh audit.

For fresh semantic readback, a separate temporary module tree is built from the six exact immutable S2 inputs: the auditor, test matrix, compile witnesses, baseline inventory, sibling R2 protocol and sibling client64 contract. The R2 protocol is not among the378 bindings and must be read directly from immutable S2. No active source or archived executable is used. The original staged qualification bytes stay unchanged. Running only the exact S2 `audit_all` must produce the same19-gate result as both `RECEIPT.audit` and `qualification/AUDIT.json`.

The helper checks that E→D, D→receipt publication and receipt publication→expected live head change paths only within N. Its final external observation is an exact `git ls-remote` comparison to the caller's expected head. Git replacement objects and lazy fetching are disabled; missing local objects cause an explicit incomplete readback. Root may separately hydrate exact reachable Git objects and run a new readback report without another artifact download, receiving invocation or qualification.

## Invocation and result

Run only after original receiving and immutable publication are complete:

```text
python <exact-frozen-helper> \
  --repo <carrier-clone> \
  --receipt-commit <actual-full-receipt-publication-SHA> \
  --directory experiments/manual-transmission/autonomy/clean-room-recovery-20261124/lint-followup-20261124/RECEIVED_CI_01 \
  --expected-live-head <actual-full-observed-live-SHA> \
  --output-json <fresh-scratch-report-path>
```

Exit0 requires `state=ORIGINAL_CI_PASS_READBACK_VERIFIED_ADMISSION_PENDING`, `complete_readback=true` and `original_ci_pass_readback_verified=true`. The report still has `admitted=false`, `scientific_admission=false` and zero new downloads, receiving calls, qualifications, builds, CI activations or Phase2 games/seeds. It records one read-only auditor invocation.

Failure returns nonzero and preserves all successful partial checks in the report. `exact_original_archive_bytes_verified` reports exact reconstruction against the saved index/metadata. The separate `independent_api_bound_archive_bytes_verified` is true only when the original-byte, API and independent pre-transport provenance stages all succeed. A partial failure cannot claim upstream authentication merely because two local metadata fields agree.

After success, root owns any separate GitHub-file readback, final reconciliation and bounded capability admission record. The helper itself never changes a ledger, consumes another artifact receipt, activates CI or admits the broader experiment.
