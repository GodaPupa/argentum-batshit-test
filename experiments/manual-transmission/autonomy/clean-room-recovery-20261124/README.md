# Candidate 02 clean-room recovery

## Terminal outcome

The single authorized qualification stopped at the registry lint gate on November 24, 2026, at 11:19 AM Pacific. The original evidence is published and independently verified. The new recovery lease is closed. Historical execution records and ownership uncertainty remain unchanged.

| Gate | Observed result |
| --- | --- |
| Compile forms | 15 passed |
| Normal behavioral cases | 67 passed |
| Mutation witnesses and positive controls | All 14 pairs validated; zero error rows |
| Restored behavioral cases | 67 passed |
| Registry | 401 raw XML cases: 400 passed, 1 failed, zero errors or skips |
| Source restoration | All 376 bindings match; tracked source is clean |
| Ordinary suite, client checks, and metadata qualification | Not executed after the registry stop |
| CI and original CI artifact receiving | Not started |
| Capability and Phase 2 admission | Not admitted; zero Phase 2 seeds and games |

The M14 parser correction accepted the fresh, correctly directed assertion report. The later registry failure is `CardLintTest`: the generic linter does not declare `SELF_ALTERNATIVE` from a real `CardScript.selfAlternativeCost`, even though the card declares that cost and the engine records its selected choice. See [REGISTRY_DIAGNOSIS.json](REGISTRY_DIAGNOSIS.json) and the [unchanged raw failing report](CARD_LINT_FAILURE.xml).

## Durable evidence

- [Closeout](CLOSEOUT.json) and [current ledger](CURRENT.json).
- [Original local receipt](LOCAL_QUALIFICATION_01/LOCAL_RECEIPT.json) and [archive part index](LOCAL_QUALIFICATION_01/ARCHIVE_PARTS.json).
- [Independent immutable and live readback](LOCAL_QUALIFICATION_01/READBACK_VERIFICATION.json).
- Original archive commit: `31f917d635b206f72afab392f54406d21737ed92`.
- Original archive: 4,854,439 bytes, 583 members, SHA-256 `f4c1c42b774217889dd3714f1c6dde2498d90339c3d44968991ed00ed8f9458a`.
- Frozen source: `2059afe031fb33dbe43a4741a3ca25ed9a2fabd9`.

The readback verified all original part bytes, ZIP member integrity, both manifests, and the expected live branch head. The first readback correctly refused locally absent Git blobs in the partial clone; explicit hydration of the exact published objects and the successful second readback are both recorded. No qualification was repeated.

## Prepared follow-up

The [unapplied patch](SELF_ALTERNATIVE_LINT_PROPOSAL.diff) adds five linter lines and eight dedicated regression tests. The [proposal contract](LINT_FOLLOWUP_PROPOSAL.json) lists the exact identities and scope. Static review and `git apply --check` completed; compilation and execution remain unverified.

A separately authorized follow-up would add exactly those eight identities to the ordinary inventory: 19,429 total, 19,386 passing, and the same 43 historical skips. Every original identity and the 401-row registry gate remain required. Source hashes, audit inventory, and execution/receiving bindings must be frozen prospectively before a new complete qualification. The consumed failed attempt remains preserved.

## Authority and history

- Authority and limits: [CHARTER.json](CHARTER.json)
- Current progress, attempt accounting, and next action: [CURRENT.json](CURRENT.json)
- Source basis: `3c7a9dd9a028b8644c92b67b3351ecf413eec484`
- Preserved historical checkpoint: `62b95ee8f854cb9a29830832c287106ad8baaac4`

Only this branch is writable. Do not infer old-runtime termination, release old owners, reactivate scheduled executors, or reset attempt counters. Complete all frozen engineering and original-evidence gates before bounded admission. Phase 2 remains unadmitted.
