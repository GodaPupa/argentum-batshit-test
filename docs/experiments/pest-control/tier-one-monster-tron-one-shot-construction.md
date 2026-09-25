# Monster Tron smoke: sealed one-shot construction

Status: **CONSTRUCTION ONLY — OFFICIAL ACTIVATION ABSENT**.

This prospectively extends the accepted operational implementation (PR #141, merge
`a224ef0008a2b85e2c2c106df9959678782e765d`). It preserves the existing protocol,
four-game vector, deck identities, calibrated pilots and no-rerun rule. It neither creates a new
vector nor starts an official attempt. Construction CI performs deterministic excluded fixtures,
read-only frozen-input validation, and fake-client claim tests.

## Accepted starting point

The production policy repair at `bfc41872c8080accacc91dc7d2159115cd23c872` passed operational
validation **36079651113**, calibrated policy validation **36079651178**, and CI **36079651135**.
The qualified Monster Tron advisor is now the same production profile exercised by the five
accepted pilot scenarios. The Pest seat keeps its accepted production profile. Source and pilot
qualification is separate from eventual gameplay evidence.

The frozen smoke source remains `4230ccaef2760fef54604f64b260dc897d618250`; artifact
**10836436268**, from run **36066393701**, has ZIP SHA-256
`70b9a665fbb154342e2789c1b6b2c2fd912579431a6ae1f9ab289984d5e7801c` and ordered-vector SHA-256
`1cace17d62bf9133bd834ac0ef7df3bbd29de141716f631764d465867975ab31`.
The existing member hashes, 566-identity exclusion audit, and balanced four-cell schedule remain
binding. This construction generates zero seeds and initializes zero official games.

## Entry and source binding

The only new official entry is `PestControlTierOneMonsterTronOneShotBoundary.executeFromEnvironment`.
Its caller cannot pass seeds, assignments, a vector identity, or a durability Boolean. The boundary
loads the original pinned ZIP internally, verifies every pinned member, recomputes the actual
ordered-seed digest, verifies the exact freeze source and assignment membership, and authenticates
the repository claim remotely before initialization. Synthetic primitives remain available to
excluded regression tests; their invented seed values cannot satisfy this sealed boundary merely
by carrying official hash labels.

The future activation must pin the fully qualified construction commit **C**. The runner checks a
clean checkout at exactly C and the actual GitHub run/workflow identities. The core rules engine,
SDK, card registry/decks, AI profiles and advisor source remain the accepted baseline
`a224ef0008a2b85e2c2c106df9959678782e765d`. Only the separately reviewed boundary, surface audit and
operational write-through evidence instrumentation may differ in production source. The exact
execution driver and wrapper are identified by C. This avoids a self-referential source freeze or
silently running a moving branch.

## Durable ownership before initialization

The helper `scripts/pest-monster-tron-one-shot-claim.py` atomically creates the single canonical ref
`refs/heads/pest-control/official-attempts/monster-tron-smoke-v1`. The claim commit is parented to C
and adds the exact block/run/source/vector reservation payload. A failed or ambiguous mutation is
not retried. Any existing claim closes the block to another worker, workflow rerun or fresh run,
even if no game ultimately initializes. Actions concurrency and `run_attempt=1` are additional
checks, not substitutes for this repository-level reservation.

The claim reserves all four assignments but explicitly does **not** authorize gameplay by itself.
The Kotlin entry separately verifies the original execution authorization, frozen input, rules
source, readiness and source identity. It calls the helper's GET-only receipt authentication,
which validates the canonical ref, commit parent/tree/blob, exact payload, source tree and live
workflow. A local receipt file alone is insufficient.

After repository ownership is established, the existing fsynced no-clobber local journal records
each assignment's attempt and initialization entry before the initializer is invoked. A separate
successful-initialization list records when reset actually completes; the report does not confuse
entering initialization with completing it.

## Evidence and failure contract

Every selected action first gets a create-only, fsynced **INTENT** record. Only then is it submitted
through the existing exact-one action processor. Its resulting events, rejection or exception get
a separate **RESULT** record. An intent alone is never interpreted as an accepted submission. If
the process stops inside the engine, the pending intent and all earlier results survive in the
artifact directory. No stale events are attached to a thrown processor call.

Complete per-game raw is saved independently before its journal terminal transition. Typed failure
raw is likewise saved independently before rejection recording. Failure of a journal rejection is
reported explicitly. A fatal diagnostic records any further exception without erasing prior raw.
The activation workflow must upload the entire output directory with `if: always()` and cannot
retry the runner. A host termination may leave no final summary; the immutable remote claim and
partial journal/action evidence still close the block and require audit.

The original caps remain 12,000 actions, 60 turns, and 500 exact-one actions per turn. A cap or
engine/pilot/durability defect rejects the execution; it does not produce a synthetic winner or
permission to reuse a seed. Raw observed terminal draws remain draws. This runner does not generate
replication seeds or activate a successor.

A valid four-game smoke is judged by valid execution and evidence, **not by four wins**. Audit of
four valid games invokes the predeclared fresh twelve-game replication gate regardless of its
record. Any subsequent seed freeze and execution remain separate controlled boundaries. The
fixed five-opponent gauntlet and four-game-per-opponent postboard phase remain governed by
`tier-one-qualification-stopping-rule.md`.

## Effective rules source and remaining activation admission

The current official rules landing page is <https://magic.wizards.com/en/rules>. Its September 25,
2026 text is <https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt>, 977,752 bytes,
SHA-256 `8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca`. Its header states it is
effective September 25, 2026. Construction validates these exact bytes and effective date and
archives them. This reuses the public source bytes independently discovered for Manual Phase 2;
no Manual engine qualification or gameplay evidence is imported.

Source identity alone does not prove simulator conformance to the entire rules document. Before
final activation, the accepted Pest/Monster rules admission and relevant exact-mechanic evidence
must be reconciled with this effective release, or the governing frozen ruleset must be documented
without silently changing it. The source digest is not a blanket semantic certification. Current
Pauper legality and any applicable admission changes also require the final activation audit.

### Scoped September 25 source review

The official [Reality Fracture update bulletin](https://magic.wizards.com/en/news/announcements/reality-fracture-update-bulletin)
identifies additions for empowering Jace, attacking a player alone, and Heartwood tokens/types.
None of those mechanics occurs in the two admitted main decks. None of its named Oracle changes
(Dragon Engine, Traxos, Sylvan Primordial, Generator Servant, Arena of Glory or Charmed Pendant)
is present. Rooftop Percher's changeling is unaffected by this bulletin's empty list of new creature
types. Bonder's Ornament has separate mana and card-draw abilities; Candy Trail's draw ability
produces no mana, so the bulletin's Charmed Pendant discussion does not add a new mana-ability
classification requirement for these cards. This is a scoped admission observation, not a claim
that every rule in the archive is implemented.

A fresh Scryfall collection lookup resolves all **34 distinct identities** in the two exact 60-card
main decks as Pauper legal. The per-card Oracle IDs, source URLs, current text, legality and lookup
time are frozen in `tier-one-monster-tron-one-shot-legality-source.json`. The current official
[Pauper banned list](https://magic.wizards.com/en/banned-restricted-list) has no intersection with
these main decks, which also contain no sticker/Attraction cards. This check makes no sideboard
admission or hardware change. Final activation must combine this current source review with the
accepted exact-card, Cascade, Prototype and production-policy evidence and the new construction
CI result before any repository claim is created.

## Controlled prospective surface change

The historical surface audit now recognizes exactly one validation-only construction workflow by
its path **and full byte digest**. Relocation, an added activation, permissions changes, unexpected
runner references, and additional public entry methods fail its negative tests. The old disabled
construction classes remain disabled. The official runner test is compiled but disabled during
validation. The actual activation workflow
`.github/workflows/pest-control-tier-one-monster-tron-official-smoke.yml` is intentionally absent.

A later reviewed activation must pin C, preserve the frozen workflow branch while it runs, perform
all source/artifact/readiness preflight checks before attempting the claim, authenticate the claim,
set the exact original execution acknowledgement, invoke the single runner once, and upload all
raw evidence on every exit. Its prospective surface-audit change must be reviewed explicitly;
renaming a command to evade the existing guard is not permitted.

## Validation gate

The coherent gate includes sixteen adversarial fake-client claim tests, strict sealed-input and
receipt tampering tests, fsynced intent/result ordering and no-clobber tests, action-transcript
replay on an excluded exact-deck fixture, all accepted operational failure tests, and the five
production Monster Tron pilot scenarios. The existing official artifact test still validates the
unchanged four-game vector without initialization. Kotlin tests require the existing GitHub CI
build environment; no local Kotlin pass or waived check is claimed.
