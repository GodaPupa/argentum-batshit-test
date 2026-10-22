# Purphoros modern R4: source candidate, not qualification

Baseline: `70caeccfa5a72f1a13a204ba35503afc8a4d1162`.
Reference donor: `a27c09f43483b86045b88679c784c105d9575c07`.
The preregistration commit precedes this implementation. Failed semantic candidates
`6054eb9ad9981da2d1a3420565cc0668d516eca6` and
`b2041ffdfff3474ee804fc7b6c32a7a5ff898a0d` remain unchanged.

The original serialization archive is preserved verbatim here. Its SHA-256 is
`1393a9827e8052e2a7e0f4c8d37bc5ba9de79ddde89c2f1907ff4e1534390ef1`;
13 distinct ZIP members, CRC clean. Original XML: JSON 3/3, Purphoros 19/25,
six failures, no errors/skips. These are component tests, not competitive games.

## Repair and trace

The compiling baseline retains modern entry, targeting, stack and SDK APIs.
Only relevant donor hunks are applied: subtype removal, numeric projection,
noncreature combat removal, pre-entry characteristic projection, and its explicit
callers. Unrelated historical trigger-origin, targeting and commander-counter
changes are excluded. StackResolver keeps its modern implementation and supplies
the checkpoint to the two entry-replacement calls. TriggerMatcher's creature-entry
predicate now reads projected characteristics; departure predicates retain LKI.

Entry-tap/untap decisions use a checkpoint before battlefield placement. The entrant's
prepared components participate in characteristic projection; its own mana symbols,
entry history, and global effects on other objects do not count prematurely.
All existing land, spell, move and token entry callers pass that checkpoint.
Paused source/target/Aura copy batches retain the original checkpoint, stripped of
executable continuation frames. This stored state is evaluation data, never a
replacement for the live resolution state. Missing required checkpoint data rejects
legacy pending-copy JSON instead of inventing entry history.

No SDK vocabulary, card definition or client interaction is added. Existing projected
state drives legality and the server's card view. Combat uses the existing removal
helper; blocked attackers retain blocked status. No new decision or UI is required.
Projection snapshots are immutable; global replacements compute their preview lazily.

Rules checked against the official September 25, 2026 text: type/subtype association
(205.1a), removal from combat (506.4), and entry characteristics (614.12). The official
FINAL FANTASY release notes' Purphoros entry confirms pre-entry versus post-entry
devotion, subtype loss, and combat removal. No rules snapshot for official gameplay
is adopted by this implementation.

## Qualification and limits

`SOURCE_MANIFEST.json` binds 22 production paths and four original generic regression
files. The unchanged Purphoros test has 25 cases; generic tests cover subtype/control/
combat, intermediate numeric conditions, entry previews, and serialized batch history.
The focused workflow must retain fresh XML, complete logs, checked source identity,
actual command exits and an evidence manifest even on failure. Broad engine and card
scenario receiving remain required after focused green; isolated green is not source
adoption or gameplay admission. Authors cannot label their own audits independent
source reviews.

Known wider boundary: this narrow entry-trigger repair uses the current projected
state; comprehensive event-time entry snapshots and every delayed trigger path are
separate qualification work. Do not infer them from the 25 Purphoros cases.

## Mission status at takeover

R3 `ab65657918c0f5e02ae181c579d1c8e6f972c0ec`, run `39702175031`, passed
131/131, with 476/800 registry positions (84 Manual, 392 opponents). Its final CI
collector/log was read. R4 card-only `a0b28760cd8ba15279fe91991f815d21e810c3cd`,
run `39714862526`, passed 15/15 and reported 479/800 (87 Manual, 392 opponents).
These are distinct sources, not additive test or registration counts. The separate
World Breaker/Glen branch had five cases green and its registry check still running
at the initial readback. No combined successor is yet qualified.

Next: focused repair qualification; full engine/affected scenarios and Assay checks;
compose only qualified card deltas; verify all eight exact rosters; close remaining
card/mechanic, independent masked pilot, replay, telemetry and admission requirements.
Capability remains 0/36; primary 0/864. Precision remains 10 used, two reserved.
Frozen v0.7, official protocols, main, accepted AI policies and unrelated lanes stay
protected. No hardware swap, official seed/claim operation or full game is run here.
