# Monster Tron replacement smoke R1 — prospective proposal

Status: **PROPOSED, SEED-FREE, NOT AN EXECUTION AUTHORIZATION**.

This is the concrete replacement-protocol proposal required by the accepted
[qualification stopping rule](tier-one-qualification-stopping-rule.md#monster-tron-smoke-and-replication-rule)
after an outcome-independent infrastructure defect. It is staged separately from accepted repair
[PR #150](https://github.com/GodaPupa/argentum-batshit-test/pull/150), merge
`57488435470787baea9e3c9949144d833352d98c`, so the qualified source
`7271c7702ee02528c6ca9a58878c4f5b4986d052` remains unchanged.
The proposal creates no seed, ref, claim, workflow, execution source or sampled evidence.
Acceptance and every subsequent authority-bearing gate must be recorded prospectively through the
normal project review and required checks. Existing user authorization permits routine bounded
continuation and review; it does not waive those project gates.

## 1. Why a replacement is eligible for review

The [original attempt audit](tier-one-monster-tron-one-shot-failure-audit.md) records run
[36085093386](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36085093386), attempt 1,
artifact10843459099, and immutable claim `1c2e253ad7f5a7652304f7c9aaafc30e547a481e`. The attempt
failed in repository-relative command execution before a per-game journal or game initialization.
Counters are exactly **4 frozen / 1 consumed block claim / 0 per-game attempts / 0 initialization
entries / 0 successful initializations / 0 submitted actions / 0 outcomes**.

The original claim ref `refs/heads/pest-control/official-attempts/monster-tron-smoke-v1`, activation
source `ac2188f7fe1f67dd300d07b4c4e6b5b0c5d96814`, execution source
`8a425d8532395e3d4262fbb35cfbac71e96feeb5` (C), original four-vector and original evidence remain
immutable. All four original assignments are retired, including the ones never initialized.
There is no result-dependent selection because no game outcome was observed. Command repair source
`7271c7702ee02528c6ca9a58878c4f5b4986d052` passed construction36087138331, PR surface audit
36087138327 and repository CI36087138367. Independently audited artifact10844371915 is 253,766
bytes, SHA-256 `6a1efb8163f22ca10c96cffafd0b808ffa33d028f53a8ceec02cf0d1a3d2f986`, and retains
11 passing boundary tests, seven durable-failure tests, five calibrated-policy tests, four surface
tests and one explicitly skipped official runner. These results qualify the command-context repair;
they do not qualify the newly discovered priority-rule defect or authorize a replacement.

## 2. Proposed bounded design and identities

Retain protocol `PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`. The proposed
new block identifier is
`PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1_NONEXPERIMENTAL_REPLACEMENT_SMOKE_4_R1`.
It designates exactly one replacement smoke of four games and does not reopen any closed opponent.

The proposed future claim identity is
`refs/heads/pest-control/official-attempts/monster-tron-replacement-smoke-r1`. This ref does not
exist by authority of this document and must not be created until a separately accepted activation.
Its payload must name the replacement block, parent original block and consumed claim, replacement
freeze artifact and exact vector/assignment hashes, immutable execution source, activation source,
workflow run/attempt and a four-member, one-attempt limit. It may never reuse or update the old ref.

Exact main identities remain:

| Deck / source | Frozen identity |
|---|---|
| Pest Control v1.0 main | `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5` |
| mehanske Monster Tron main | `79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f` |
| Historical qualified Pest runner | `9829ee98869343cd48dceaa9a27c56ed27c6b3bc` |
| Historical core engine / card / AI baseline | `a224ef0008a2b85e2c2c106df9959678782e765d` |

The actual Pest pilot remains `AiProfile.PRODUCTION_CANDIDATE_EXPIRING`. The Monster Tron pilot
remains the qualified `PestMonsterTronPolicy.profile`, which adds `PestMonsterTronAdvisorModule`
to that base profile and identifies itself as `pest-monster-tron-policy-audit`. The deck source,
card counts, legal actions, London mulligans without hand smoothing and pilot information rules
remain unchanged. The sideboards retain their admitted identities but are not instantiated.

A newly confirmed shared rules defect blocks reuse of the historical engine unchanged:
`PassPriorityHandler.resolveTopOfStack` returns priority to the resolved spell's caster, while the
effective rules require the active player to receive priority after resolution. The separately owned
canonical post-resolution repair, post-cast SBA repair, and their multiplayer/interaction regression tests must be accepted and integrated
before C2 can qualify. The AnyTarget eligibility repair must also be qualified for the exact spells and pilots that use it; the forest/artifact eligibility defect and Battle enumeration path cannot be treated as qualified by registry coverage. The original Monster attempt had zero initializations and actions, so it
contains no gameplay affected by this defect. The accepted additive historical evidence audit, merged at
`b630f7bfc4125556639e0819f58e817c9e122e69`, in [PR #153](https://github.com/GodaPupa/argentum-batshit-test/pull/153), source
`9bf29b048b12b816824ba96b08bdf5b6874133c1`, confirms 148 wrong-priority transitions in at least
53 of the 81 previously accepted Red, Grixis and Mono-Blue games. All five blocks / all 81 games
are **QUARANTINED_PENDING_PROTOCOL_DISPOSITION** for clean Tier-1 inference; the absence of a
detected witness does not permit selective salvage. Original raw evidence, recorded outcomes and
seed retirements remain immutable. The closed opponents are not reopened, and this Monster
replacement proposal grants no replacement, sampling or outcome-adjustment authority for them.

Construct a future execution source **C2** from the historical C behavior plus the canonical command
repair, the **separately qualified post-resolution and post-cast priority repairs**, the separately qualified
AnyTarget correction for the exact spells that use it, and specifically reviewed
replacement-identity/authorization bindings. Do not use moving main or silently import Spy/postboard
card support or pilot revisions. Freeze the exact new engine source/tree and explicit minimal delta
from `a224ef0008a2b85e2c2c106df9959678782e765d`; do not broaden a source-diff exclusion to hide the
rules change or call the engine unchanged. Rerun the exact Pest/Monster policy, interaction, replay
and relevant card tests at that resulting source. C2 is not yet created or frozen; later source and
tree hashes must be explicit rather than guessed. The final activation **A2** must pin accepted C2
after C2 exists, avoiding a self-referential source hash.

## 3. Four fixed cells and seed exclusions

Assign the fresh vector in unchanged draw order:

| Game | Pest seat | Monster Tron seat | Starting deck | Pest play/draw |
|---:|---:|---:|---|---|
| 1 | 0 | 1 | Pest Control | Play |
| 2 | 0 | 1 | Monster Tron | Draw |
| 3 | 1 | 0 | Pest Control | Play |
| 4 | 1 | 0 | Monster Tron | Draw |

The accepted original exclusion audit contains 566 unique retired Pest identities. Reconstruct it
from the pinned historical evidence, then add all four original Monster Tron vector members. The
original vector must be loaded from artifact10836436268 with ZIP SHA-256
`70b9a665fbb154342e2789c1b6b2c2fd912579431a6ae1f9ab289984d5e7801c`, ordered-vector SHA-256
`1cace17d62bf9133bd834ac0ef7df3bbd29de141716f631764d465867975ab31`, and assignment SHA-256
`3e8c61d729d219261eb485600039b150d66e2e73e90f0f2e1450c7b48a57e098`. Validate its complete
checksum inventory and four distinct nonzero members; prove disjointness from the inherited 566.
The derived minimum exclusion universe is therefore **570**, not 566.

Before any production freeze, reconcile live accepted, rejected, quarantined, pending and claimed
Pest vectors. If another authorized gate retired or reserved additional seeds, prospectively extend
and pin the exclusion manifest rather than silently assert that 570 is still complete. Any missing
source, digest/count drift or unexplained overlap blocks the freeze. Deterministic fixtures remain
explicitly excluded from official gameplay and cannot provide an official result.

A separately reviewed production-freeze authority may permit exactly one 32-byte entropy draw,
interpreted as four signed big-endian 64-bit seeds in unchanged order, preserving the accepted
original generation method. Quarantine and fsync the entire draw before checking it. Reject the
entire draw for zero, duplicate or excluded members; retain all four identities and stop without
reroll or partial salvage. Reviewable fixed fixture entropy may validate the implementation before
that authority exists, but no production entropy is requested by this proposal.

## 4. Authority and readiness gates

| Gate | Required concrete evidence | Authority after acceptance |
|---|---|---|
| Repair qualification and this prospective protocol | Exact-source #150 checks and artifact; separately accepted canonical post-resolution and post-cast repairs; exact-deck AnyTarget qualification; consumed-claim audit; reviewed four-cell scope and exclusions | Implement and validate the bounded replacement machinery without entropy or gameplay |
| Seed-free replacement freeze validation | Complete pinned exclusion reconstruction; adversarial fixture tests; exact deck/pilot identities; durable quarantine/no-reroll contract; no reachable gameplay | A separately recorded one-shot production seed-freeze authority may be considered |
| Production seed freeze and audit | One authorized draw; quarantine, member/vector/assignment/manifest/checksum hashes; run/source identities; collision audit | Record a vector-specific execution authorization while the runner stays disabled |
| C2 construction qualification | Exact input binding; canonical priority repair with explicit source-delta audit and exact-deck integration tests; repaired root-cwd helper and protected-source tests; calibrated pilots; replay and failure evidence; receipt authentication and no-clobber attempts; current scoped rules/legality admission | Review a separate exact-source A2 activation |
| A2 activation acceptance | Exact workflow bytes and guard tests; C2 pin; live source/ref/run/claim reconciliation; adequate runtime/upload budget | One new claim and one execution of these four replacement members only |

Compatible implementation and tests should be batched within each coherent gate. Separate
entropy-generation, sampled-game authorization and outcome-exposure boundaries are substantive;
they cannot be collapsed into an implicit permission. The original seed-freeze and execution
acknowledgements name the retired block and cannot authorize this replacement. New authorization
records and guarded bindings must explicitly name the replacement block and its exact artifacts.
No current workflow, guard allowlist, claim helper constant or original loader is changed here.

At C2/A2 admission, archive the rules actually effective on the execution date, verify the official
[Comprehensive Rules source](https://magic.wizards.com/en/rules) and applicable
[Pauper banned list](https://magic.wizards.com/en/banned-restricted-list), and reconcile current
Oracle/legality evidence for the exact main decks. The previous September25 archive digest
`8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca` is historical source evidence,
not an indefinite current-date admission. Do not apply a future-effective revision early or infer
full simulator conformance from an archive digest. Any relevant rules/card change requires scoped
qualification before authorization; it does not permit an unreviewed hardware or pilot change.

## 5. Execution, artifacts and stopping rule

Preserve 20 starting life, exact-one submissions, the 12,000-action/60-turn/500-action-per-turn
ceilings, and the accepted five-hour process cap inside a six-hour job with the full predeclared
claim and upload reserve verified before claiming. A timeout, resource cap, rejected action,
engine/pilot/provenance defect or unresolved outcome is recorded according to the existing rejection
contract. None produces a synthetic winner. An observed terminal draw remains a draw.

The new global claim must be atomic and create-only. A failed or ambiguous reservation is never
retried. Any existing replacement claim consumes this replacement attempt, even with zero game
initializations. The sealed loader must validate the exact source, replacement artifact, effective
rules and authenticated remote receipt before initialization; an invented local receipt is not
sufficient. Preserve fsynced game-attempt and initialization-entry ordering, actual-successful-init
counts, per-action INTENT/RESULT records, raw terminal or partial-failure evidence, claim/rules copies,
pre-sealing failure receipts, process exit, final inventory and upload on every available exit.
No original block evidence may be overwritten by replacement paths or labels.

Accept the smoke only after independent audit confirms exactly four valid terminal games in the
fixed order and cells, with every durable transition/raw/event transcript reconciled and no
protocol defect. **Four valid games trigger fresh twelve-game replication regardless of wins.**
A legitimate loss is kept. Replication preserves the original 6/6 play-draw, 6/6 seat balance and
three observations per seat × starting-deck cell, uses fresh excluded-audited seeds, and proceeds
through its separate established freeze and execution gates. This proposal neither generates nor
executes that twelve-game vector.

If the replacement smoke is defective, preserve its complete evidence and consumed claim, retire
its full vector, and stop this replacement gate. There is no automatic second replacement or
open-ended extension. Any further action must satisfy the governing prospective review requirement.
Clean accepted replication closes the Monster Tron preboard axis regardless of its record. Spy
and the bounded five-opponent postboard phase remain independently required for the final Tier-1
conclusion; this replacement cannot itself complete the full qualification program.

## 6. Current proposal counters and review disposition

New official seeds: **0**. New claims: **0**. New official games authorized or initialized: **0**.
New actions and outcomes: **0**. Candidate C2/A2 commits, replacement artifact and replacement
workflow: **not created**. Existing claim/ref/vector: **unchanged**.

Review this concrete proposal together with the separately qualified priority repair. If accepted,
record the acceptance
reference and implement only the next authority shown in the table. Until then the precise blocker
is the unaccepted replacement protocol, required canonical priority-repair qualification, and separate
seed/activation gates, while Spy support
and postboard preparation can proceed independently.
