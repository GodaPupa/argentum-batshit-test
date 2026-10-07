# Monster Tron: prospective twelve-game replication transition v1

Status: **PROPOSED FOR INDEPENDENT DESIGN REVIEW ONLY.** No entropy, seed allocation,
claim, workflow dispatch, engine initialization, gameplay, retry or replication is authorized.
The companion JSON is a seed-free design contract, not an execution input or receipt.
The accompanying Python qualification only reads this contract and mutates in-memory copies.
It cannot invoke the engine, an entropy source, GitHub, a claim helper or a workflow.

## Governing history and purpose

PR #216 result review 6046389902 (canonical 7,510 UTF-8 bytes, SHA-256
`18d5668c2ee46d5cdd822bed52554eb80cf0e76d89d04d6ab3149a8a2d2645ce`) and adoption
6046397725 accept only the repaired four-game R1 smoke: W,W,L,W, Pest 3–1.
The exact original is run `37676510705`, attempt 1, artifact `11506879794`, ZIP SHA-256
`e4dcf7eb85a08a6bc16a4f4b93c23ffb373b3a1e4a26dde2ced613329fa3fbaf`.
Its claim `f165a7d19447d68a7db5984c36f1654179120eff` is permanently consumed.
The historical preclaim failure `37633585655` remains a failure with consumed dispatch
authority. The older claim `1c2e253ad7f5a7652304f7c9aaafc30e547a481e` also remains immutable.
Neither success nor failure may be relabeled, overwritten or used as new permission.

The existing stopping rule at execution source `ed35c3035d41a740aac1657708b2023b624cfc47`,
blob `6c737d8bb7af58cccc7cafbb60a6abe9876695ae`, already predeclares twelve fresh games:
six Pest play, six draw, six in each seat, three in each seat × starting-deck cell.
The R1 proposal at that source, blob `f319d6d9e84608f5c251ce805cb669a7799b6c6b`, preserves
this rule and separate freeze/execution gates. Its historical automatic-trigger wording means
eligibility to prepare the next gate; adoption 6046397725 expressly grants no replication authority.
Live reconciliation found no Monster Tron replication-specific branch, vector, runner or workflow.
Other opponents' replication paths are not imported into this lane.

The purpose is one bounded, fresh-seed preboard replication of the same fixed deck/pilot/source
pair under the accepted simulator, and descriptive robustness across four assignment cells.
It is not an optimization search, policy comparison, tournament estimate or broad Tier-1 claim.
The count twelve predates the 3–1 observation; it supplies three observations per cell and is not
a power calculation. Twelve games cannot establish precise win rates or strategic superiority.
Report all W/L/draw counts, denominators, cells, caps and failures; do not introduce a significance
or win-percentage acceptance threshold after outcomes. Smoke remains a separate four-game prior
readiness stratum, not part of the twelve-game primary denominator. No pooling, sample-size
extension, stopping after a favorable prefix, or opponent/deck change is permitted by this design.

## Exact reuse and required future delta

| Identity | Immutable accepted value |
|---|---|
| Execution source / tree | `ed35c3035d41a740aac1657708b2023b624cfc47` / `34406e5a26aad6d6ebf64e9b1a1977e1b719b095` |
| Engine baseline | `433df3310efe31c49f27034b50e6d8d7e60561f7` |
| Smoke activation / workflow blob | `570751227244d555b6fc45793cc3c32917374089` / `9b8d92cd27af4e39377c2ebe4914d8212b778264` |
| Smoke claim-helper blob | `6ff7fcfa39c0f349d4a8bf7f189188be3ea29724` |
| Accepted repaired boundary-test blob | `40cf4f00284b77c16abc7818cca9ba42a74ea0dc` |
| R1 freeze / archive SHA-256 | `41716f6918ec559ee60b14815b5b0f23d3aaf38a` / `e9ddafe0a7aec28cb07950f2f1d5908a31af677ff58415e809117575f46d6817` |
| R1 vector / canonical assignments SHA-256 | `8cdba4018aac3f423d92981581b628a8647e15be1f916a32c81ca1aef6ccff89` / `edd4d831ed0e9cd319ce908e71cca117203a1f5970e00441f3c9310f10e412c7` |
| Pest main SHA-256 | `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5` |
| Monster Tron main SHA-256 | `79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f` |

Pest pilot remains `AiProfile.PRODUCTION_CANDIDATE_EXPIRING`; Monster Tron remains
`PestMonsterTronPolicy.profile` / `pest-monster-tron-policy-audit`. Preserve starting life 20,
London mulligans without smoothing, information boundaries, exact-one action submission,
card/rules/pilot bytes and no sideboarding. Exact path/blob inventory and protected-source diff
against the source above must accompany any future implementation; no moving-main checkout.

The current sealed loader and runner are deliberately bound to four R1 games and the consumed
claim. They are NOT reusable as a twelve-game input interface. A later separately reviewed
candidate needs replication-specific identity/loader, twelve-member coordinator/boundary tests,
claim namespace/payload, workflow guards and evidence count assertions. Preserve existing smoke
files; do not change their constants to twelve or widen their allowlists. Reuse production mechanics
only byte-identically. Bind future source/tree/workflow/vector only after they actually exist.
Any production semantics change or broad helper redesign is a separate review question.

This candidate makes no production or workflow changes. Preparing the design and its static
qualification is the smallest complete transition before those authority-bearing implementation
choices are accepted. Qualification here does not qualify a future loader or execution runner.

## Fixed assignment order, with no seeds allocated

Indices 1–12 are prospective slots, not seed identities or official game attempts.
Repeat the four smoke cells three times in the following fixed order, assigning entropy words in
unaltered draw order. No sorting, cycling through seeds until legal, or outcome-based assignment.

| Slots | Pest seat | Monster seat | Starting deck | Pest |
|---|---:|---:|---|---|
| 1,5,9 | 0 | 1 | Pest Control | Play |
| 2,6,10 | 0 | 1 | Monster Tron | Draw |
| 3,7,11 | 1 | 0 | Pest Control | Play |
| 4,8,12 | 1 | 0 | Monster Tron | Draw |

## Prospective one-shot freeze procedure

1. Before entropy, independently accept this design and separate seed-free machinery qualification.
   Reconstruct the entire live Pest retired/reserved/quarantined/accepted/rejected seed inventory
   from pinned evidence, including failed vectors and all deterministic fixture exclusions. The old
   570-identity floor plus the complete four-member R1 smoke gives a **minimum 574**, not a claim
   that the live total is exactly 574. Deduplicate canonically and prove provenance, counts and
   digests. Missing artifacts, unknown seeds or unexplained overlaps block the draw. Reserve the
   freeze lane prospectively so concurrent work cannot silently change the exclusion universe.
2. Freeze exact machinery source/tree, exclusion snapshot, this contract and fixed schedule; obtain
   a fresh explicit one-draw seed-generation/freeze authority, with independent review and adoption.
   No current authority token or smoke acknowledgement may satisfy it.
3. Only under that later authority, make exactly one cryptographic `os.urandom(96)` call. Durably
   create the full raw quarantine and consumed-authority record with no-clobber file creation,
   fsync file and directory, and preserve remotely before interpreting/validating candidates.
   Record raw digest, byte count, UTC, source/tree, exact authority, run/attempt and exclusion digest.
   Ambiguous entropy, failed durability or missing remote preservation consumes the draw authority
   and stops; never draw again to recover missing bytes.
4. Interpret exactly twelve signed big-endian 64-bit words in original byte order. Reject the ENTIRE
   vector for zero, duplicate, any exclusion overlap, wrong count/length, or concurrent inventory
   drift. Preserve and retire all twelve known words, including unused members; never redraw,
   replace a word, change order, choose a subset, or use an alternate random source. Unknown or
   incomplete bytes remain an unresolved consumed draw, not a license to reconstruct them.
5. For a valid vector, emit canonical LF UTF-8 ordered decimal/hex seeds, the fixed assignment CSV,
   provenance/authority/exclusion manifest and complete member checksum inventory; build a
   deterministic immutable archive and freeze its exact commit/tree/member/outer digests.
   Reconcile exclusions again before independent freeze audit. Drift fails closed for classification.
   No game loader runs during generation or validation. All future qualification uses explicit
   nonofficial fixtures; it never evaluates candidate seeds for game quality or predicts outcomes.

## Independent gates and dispatch/claim separation

| Gate | Required reviewable object | Maximum next authority, only if explicitly adopted |
|---|---|---|
| D: design (this request) | Exact spec, seed-free contract, static/negative qualification | Propose narrow seed-free machinery/runner construction scope; no entropy or execution |
| M: seed-free machinery | Exact source/tree; deterministic fixtures; exclusion reconstruction, quarantine/no-clobber/ambiguous-write negatives; disabled runner/loader receipt separation | Consider separate explicit one-draw freeze authority |
| F: freeze authority and result | Pre-draw scope then complete original draw/quarantine/vector/exclusion/archive evidence | Accept exact vector for later review, no game permission |
| S: source and activation | Twelve-game source/tree, exact vector/receipt binding, gates, budget, every failure path; no official initialization | Consider publication and prospective exact execution-ref preparation only |
| P: publication/ref | Exact workflow bytes on reviewed default branch and proposed execution ref; no hidden drift | Consider fresh one-dispatch review after final live reconciliation |
| E: execution authority | Exact source/workflow/vector, current rules/legality, absent fresh claim, zero prior matching dispatches, original attempt 1 | Exactly one separately adopted dispatch and one create-only twelve-game claim |
| R: result | Entire original artifact/journals/raw games/claims/failures, regardless of score | Independent result classification/adoption and bounded closure |

Every gate requires a fresh independent Reviewer A (or fresh independent Pest reviewer) who did
not author, design, execute or materially propose the exact target, followed by orchestrator
adoption. Gate labels are design sequencing, not approval receipts. Do not request E in this task.

Proposed future names ONLY:
`refs/heads/pest-control/official-attempts/monster-tron-preboard-replication-12-v1` and
`pest-control/tier1-monster-tron-replication-12-v1`. Neither is created here. Any rename after
review requires prospective reconciliation. Keep both historical smoke execution refs fixed.
One dispatch is consumed upon appearance of the exact matching run, even before claim or failure.
An ambiguous submission must be reconciled read-only, never clicked again. Claim reservation is
a later runtime gate, atomically create-only, binding source/tree, workflow/ref, run/attempt,
vector/archive/assignments, count twelve, max one attempt, prior-smoke evidence and current rules.
Existing/ambiguous claims fail closed; no delete/update/retry/resume. A claim reserves the whole
block permanently, including uninitialized assignments. Dispatch and claim cannot authorize one
another, and a passing validation ACK cannot open the execution loader.

## Failure semantics, time and evidence

Retain per-game caps 12,000 actions, 60 turns and 500 actions per turn. Prospectively keep one
five-hour process cap for the ENTIRE twelve-game block within a six-hour job, with a ten-minute
claim reserve and fifteen-minute upload reserve: before claim, at least 19,500 seconds must remain
measured conservatively from workflow start. This is a ceiling, not a runtime prediction from four
games. Source qualification must demonstrate refusal when insufficient; expanding the cap,
sharding or adding jobs requires separate prospective design review before allocation/execution.
Refresh actual effective rules, banned-list and exact-deck Oracle/legality evidence at source and
dispatch gates. The smoke's 2026-10-12 fail-closed boundary is not waived or extended here.

| Failure | Required disposition and preservation |
|---|---|
| Static/fixture qualification | Preserve exact failed source/test/output; diagnose before a justified successor. No unchanged rerun for reassurance. |
| Pre-draw freshness/provenance failure | Stop without entropy; retain evidence; no inference of authority. |
| Draw/quarantine/freeze failure | Preserve consumed draw and all known members; stop for independent classification; no second draw. |
| Preclaim runtime failure/budget refusal | Preserve failed run, original attempt and upload; dispatch remains consumed; no redispatch. |
| Claim rejection/ambiguous create/confirmation | Preserve exact requests/responses safely; reconcile read-only; no mutation retry or alternative claim. |
| Postclaim initialization/action/cap/timeout/host loss/evidence defect | Reject incomplete/defective block, stop later games, preserve whole vector/claim, partial raw and durable prefix; no resume, substitute, refill or selective salvage. |
| Legitimate terminal loss/draw | Preserve as observed outcome, continue fixed schedule; neither is a defect or reroll trigger. |

Before each initialization, durably record attempt then initialization entry; separately record
actual successful initialization with seat/player mapping. Before every one-and-only submission,
write/fsync INTENT; afterward write/fsync RESULT including rejection/error. Failed intent prevents
submission; missing result leaves an unresolved attempted action, never a synthetic result.
Use create-new files and fsync directories, preserving full original action/event/raw transcripts,
receipt copies, rules, provenance, ordered coordinator journal, per-game raw hashes, failure
diagnostics, process exit, test XML/logs and complete final checksum inventory. Always-upload must
cover every available exit and all prefixes. Upload loss is an evidence defect, not success.
Redact credentials only, never outcomes or protocol failures. Bound capture must mark truncation.
An independent audit must reconcile exact archive/member coverage, all twelve slots, sequences,
intent/result pairs, terminal outcomes/player mapping, caps and all failures. Logs/source may
support durable ordering; timestamps alone do not prove physical fsync chronology.

## Stopping and requested disposition

One independently accepted complete twelve-game result closes Monster Tron preboard under the
predeclared stopping rule regardless of W/L/draw record. No optional extension, repeated testing
until favorable, new opponent, postboard sample, or deck/pilot adjustment follows from it. On a
defect/incomplete block, preserve it and stop for independent classification with no automatic
replacement. The smoke's Game 3 loss and all historical failed predecessors remain permanently
available and separate. Broader program conclusions remain outside this lane.

Review this exact prospective transition, including the bounded sample rationale and unchanged
failure semantics. Acceptance sought is **DESIGN AND STATIC QUALIFICATION ONLY**. No future
source/workflow/vector SHA is invented. No official seed allocation, claim, game, action or
outcome occurs in this task. Stop modifying this candidate once frozen for review.
