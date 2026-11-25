# Candidate02 host recovery: prospective authority options

Status: **PROPOSED_NOT_AUTHORIZED**. This is a review document, not a launch plan or permission to modify source, run tests, activate a workflow, or receive an artifact. No proposed source revision or new grant exists yet.

## Recommendation

Given the current host's missing Docker capability, the practical option is **one newly authorized GitHub-hosted validation run**, with actual MockK and Flyway readiness checks in that job before one complete qualification. This explicitly replaces the current grant's requirement for a successful local qualification before CI. It preserves every semantic and test gate, but it does **not** preserve the existing two-stage local-plus-CI execution process. The failed local run remains failed; a hosted pass must never be reported as a local pass.

If that ordering change is unacceptable, retain the strict alternative: obtain a Docker-capable foreground host, prove its actual test-worker readiness, run one new full local qualification, and permit the remaining single CI validation only after complete local PASS and immutable/live readback. No suitable external local executor has been identified in this session.

## Evidence the new authority must preserve

The consumed lint-followup attempt used source `82ec335573572edfd2a2e4d734d2f0d81801139f` (S2), tree `923ec79e1c867eedf35b408153bf60988fbd26b6`, and launch `9953c3e78c6f386f4284352f39ccf5d81554e10b`. It ended with exit 1 at the ordinary gate. Its original archive is published at commit `18168564399e67612f3b4e1a4d98c76ccf0f2d38`, SHA-256 `6179df2fed79eea77c5bb7fa696a17e0998923780d4315001667f02a4242c264`, 5,961,295 bytes and 952 members. Independent readback state `ARCHIVE_READBACK_PASS_QUALIFICATION_FAILED` and the host diagnostic are published in checkpoint `d53cbbd783d8252c03bd1747b031c791c0f45e07`; bind that actual checkpoint before any new launch.

Observed ordinary output contains 2,297 rows: 2,130 passed, 118 failed, 49 skipped. There are 17,134 missing expected identities and two unexpected framework identities. The 118 failures share ByteBuddy external attachment failure. Nine Flyway cases were newly skipped because Testcontainers found no valid Docker environment; `/var/run/docker.sock` was absent. The 49 skips are not an acceptable substitute for the frozen 43-skip multiset.

The reviewed standalone diagnostic failed dynamic attachment both with and without the original proxy properties. Starting the same probe with the ByteBuddy startup agent passed, with instrumentation available before and after installation, the same instrumentation instance reused, and redefine/retransform support present. This supports a host mitigation; it does not prove the actual MockK cases or the complete suite pass. The lower-level attachment timeout's operating-system cause remains unresolved. Docker socket, endpoint and executable remain unavailable on this host.

Keep the original lint preflight and local reserved/started/completed counters at 1, CI activations and original artifact downloads at 0, and all earlier ledgers unchanged. Successful readback proves preservation of the failed evidence, not qualification success. No consumed budget is refunded.

## Why a new two-file authority revision is necessary

Frozen S2 `verify_dispatch.py` accepts only its fixed lint recovery ID, immutable charter, namespace, attempt 1, and exact source freeze. Its local lane requires `local_completed == 0`; its CI lane requires a real local PASS/readback. Neither a fresh local attempt nor the hosted alternative fits that authority.

The smallest honest successor, called **S3 here only as a source revision of Candidate02**, is a sole child of S2 changing exactly:

1. `experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114/verify_dispatch.py`
2. `experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114/CANDIDATE_PINS.json`

The first adds an explicit new authority route for the **one selected option**. The second derives the changed verifier binding and adds the exact new grant metadata. All 378 binding paths remain; only the verifier's binding value changes, and the other 377 binding values and all old pin metadata remain identical. Pins themselves remain externally bound by the actual source tree and pins blob.

Every other tree entry must equal S2: all production and tests, the five-line linter correction and eight regressions, the auditor, qualifier, original receiver, mutations, matrices, inventory, protocol, gates, manifests, goldens, historical evidence and existing workflows. The frozen auditor remains SHA-256 `aed497bac5eafd80fc141557768c7697472324d5da087cf1fe3fb66741075e81`. This is a two-file source change; it must not be advertised as the entire S2 commit being unchanged.

Preserve the old verifier helper bodies and historical source proofs. Verify the original S1-to-S2 five-file derivation against immutable S2, then prove the exact S2-to-S3 two-file delta. Historical six-file normalization/fixture proofs still use S1, while actual prospective source/tree/pins and all 378 working bytes use S3. Do not substitute S3 into a historical proof or fake an old authority envelope.

No compliant unchanged-S2 retry exists under the present verifier. Replaying launch `9953…`, resetting its CURRENT, changing imports, or patching/shadowing the verifier at runtime would bypass the consumed grant and is excluded.

## Concrete option comparison

| Decision | A: ready local host, then conditional CI | B: one hosted validation (recommended) |
| --- | --- | --- |
| New execution grant | One new full local qualification; one subsequent CI validation only after local PASS/readback | One GitHub-hosted activation containing readiness checks and, only if they pass, one full qualification |
| Local-pass prerequisite | Retained | Explicitly replaced for this one grant by in-job readiness and full hosted gate proof |
| New full qualifier invocations | At most one local plus one conditional CI | At most one total, hosted |
| New hosted activations | At most one, conditional | At most one, including an activation that fails before qualification |
| New original artifact download/receive | At most one each | At most one each, preserving failure evidence too |
| Readiness | Actual MockK and all nine Flyway cases on the ready foreground host; same host settings for the local run | Actual MockK and all nine Flyway cases in the same hosted job, before full qualification |
| Result meaning | New local result; separate conditional hosted result | Hosted result only; `local_pass = false` |

Option B should use the standard `ubuntu-24.04` GitHub-hosted Linux VM profile, with Docker and real PostgreSQL/Testcontainers readiness checked in the job. The new grant must cover one bounded readiness sequence and one full run, not repeated attempts until green. It creates no general authority for paid capacity, repository policy changes, other branches, or additional scheduled work. Freeze the exact test selectors, commands, runner/toolchain/agent settings and helper bytes before activation. Actual MockK readiness is still an unresolved prerequisite at this proposal stage.

## Fresh namespace, honest counters and required evidence

Proposed new namespace: `experiments/manual-transmission/autonomy/clean-room-recovery-20261124/lint-followup-20261124/host-requalification-20261124`.

Proposed recovery ID: `manual-host-requalification-20261124-01`. Proposed owner: `manual-host-requalification-foreground-20261124`. These are proposed names, not existing authority. Use the same isolated branch, expected-head compare-and-swap publication, and no new refs. The final old checkpoint, failed receipt/readback and host diagnostic must be immutable charter references. Preserve all preexisting files outside the newly authorized namespace and the exact two source paths; a separately authorized new workflow is operational validation content, not part of S3.

Use one new top-level `host_requalification` envelope key and reject mixed legacy/new keys. Freeze only the selected plan in the charter; the verifier must not accept both choices opportunistically. The new CURRENT records its distinct reservation and cumulative references to the consumed old attempts. New counters count new actions and never replace or zero old counts. Reconcile prior Candidate02 CI activations before reserving the one hosted activation. There is no additive second CI allowance from the old unused budget.

For Option B, the immutable pre-event carrier cannot contain a successful readiness result that does not yet exist. It binds the readiness contract and workflow/helper bytes. The actual in-job receipt, commands, raw XML, exact identities, source restoration and host settings belong in original runtime evidence, outside the immutable `qualification` freeze. Bind them to the actual run/job/event and verify them before `qualify.py`; independently verify them again from the single original artifact. Do not fabricate a pre-event readiness PASS or a local receipt.

Reserve the one activation and possible full invocation durably. Record actual readiness and qualifier starts/completion separately. If readiness fails, the activation remains consumed and the actual full invocation count is zero; no automatic rerun or second workflow is allowed. If qualification starts, its invocation count is one regardless of outcome. Preserve and read back the original partial outputs. Missing artifacts or a transport failure are blockers, not permission to retry transport.

The new operational launcher/workflow, receiver and readback copies must bind actual S3, new epoch and actual provider metadata; retain the old prepared helpers unchanged and inactive. Preserve the existing source/validation/event topology and workflow-only validation deltas. The original ZIP, API digest and size, original 450,000-byte base64 parts, CRC, safe unique members, independent expected metadata and immutable/live readback remain mandatory. If readiness fails before qualification starts, preserve genuine runtime and preflight evidence under its outer manifest, record zero qualification starts, and do not invent a qualification directory or inner manifest. Both genuine outer and qualification manifests and the immutable-source raw audit are mandatory after a qualification starts and for every hosted qualification PASS or admission. Preservation of a readiness-only failure is distinct from successful qualification readback.

The unchanged qualifier emits the historical literal `ALL_LOCAL_GATES_PASS_ORIGINAL_RECEIVING_REQUIRED` even when used in CI. Preserve that raw value unchanged. For Option B the outer receipt and final report must explicitly identify `execution_environment = github_hosted`, `local_pass = false`, and a hosted result. That internal literal cannot satisfy an old local prerequisite or be presented as new local evidence.

## Quality boundary and stop rule

Both options retain 15 compile forms; 67 normal and 67 restored cases; all 14 negative witnesses and positive controls with no mutant errors; 401 registry cases; exactly 19,429 ordinary identities, 19,386 passes and the same 43 historical skips; all focused/full client, typecheck, metadata, Assay, serialization/replay, source restoration and raw-evidence checks. Run the frozen qualifier from the beginning. No resume-at-ordinary, skip waiver, changed assertion, altered production/test file, or reuse of prior successful phases replaces a gate.

Option B gives up the current process's additional successful local execution before the hosted execution. Independent artifact audit and readback remain, but they are not a second test execution. This process change must be stated in the user's new grant. Neither option admits Candidate02 or Phase 2 in advance, and neither authorizes another retry after failure.

## Proposed concise approval text

**Option B:** “I authorize one new Candidate02 validation on a Docker-capable GitHub-hosted `ubuntu-24.04` runner: one in-job MockK/Flyway readiness sequence, at most one complete frozen qualification, and at most one original artifact download and receive with independent audit/readback. I authorize only the verifier and matching pins source revision, with all production, tests and semantic gates unchanged. For this attempt I replace local-pass-before-CI with hosted readiness plus full hosted gate proof. Preserve the failed local attempt and all counters; do not claim local PASS, rerun, spend additionally, or broaden scope.”

**Option A:** “I authorize one new Candidate02 full local qualification on a Docker-capable foreground host after actual MockK/Flyway readiness, and only the verifier and matching pins source revision. Keep every production/test byte and semantic gate unchanged. After complete local PASS and immutable/live readback, allow at most one CI validation and one original artifact download/receive. Preserve all consumed attempts and counters; no retries, additional spending or broader scope.”

Only one option should be selected. Final charter/source/helper identities are derived and reviewed after a new grant and before activation; placeholders must never be accepted by executable controls.

## Static evidence references

- S2 commit `82ec335573572edfd2a2e4d734d2f0d81801139f`: `verify_dispatch.py` functions `verify_lint_followup_authority`, `verify_lint_successor` and `verify`; frozen `qualify.py` and `receive.py` under the research record above.
- Charter commit `86450c1ecb227804dbd45acf5ba28689ce0c61bc`: `lint-followup-20261124/CHARTER.json`, especially authorization, budgets, required gates and failure rule.
- Archive commit `18168564399e67612f3b4e1a4d98c76ccf0f2d38`: lint-followup `CURRENT.json` and original `LOCAL_QUALIFICATION_01` evidence.
- Final readback and host-diagnostic results are published at `d53cbbd783d8252c03bd1747b031c791c0f45e07`; bind that checkpoint in the future charter rather than inferring them from the original archive commit, whose CURRENT still says readback pending.

Review scope: static source/authority assessment and scratch-only proposal. No tests, builds, qualification, workflow activation, receiving, transport, repository modification or new Git objects were performed for this proposal.

## Published failure and diagnostic checkpoint

The immutable failed-readback and host-diagnostic checkpoint is `d53cbbd783d8252c03bd1747b031c791c0f45e07`. Its `LOCAL_QUALIFICATION_01/READBACK_VERIFICATION.json` and `HOST_DIAGNOSTIC_01/` records are now published. This is distinct from original archive commit `18168564399e67612f3b4e1a4d98c76ccf0f2d38`. A new charter must bind these actual objects. The proposed namespace and recovery identifier use the preparation date November 24 in the user's Pacific timezone.
