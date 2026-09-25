# Pest Control: exact Spy opponent policy qualification

## Scope

This seed-free executable policy candidate targets the unchanged Dr_dej96 Spy Combo preboard60
under `PEST_CONTROL_V10_VS_DR_DEJ96_SPY_COMBO_2026_09_15_PREBOARD_V1`. It creates no sampled
vector, claim or execution authority. `PestSpyComboPolicy.profile` is intended only for Spy;
Pest's profile and the frozen Monster source remain unchanged. Effective rules, canonical priority
and other confirmed defect repairs, operational calibration, deterministic full-game replay,
artifact contract and guarded execution still require exact-source admission before gameplay.

## Canonical code and fixed choices

The selective prior code is Industrial's accepted `abfd806f6332c0da311e40b01c9d85eeb0962165`
test-only `SpyComboAdvisorModule`. Its card patterns become one production module, with a
prospectively fixed revealed-hand ranking supplied by each independently qualified project.
No Industrial matchup result, seed, control identity or authority is transferred.

Pest's immutable profile ID is `pest-spy-combo-policy-v1`, extending
`PRODUCTION_CANDIDATE_EXPIRING`. Its Fiend ranking is Weather the Storm, Cast Down, Bone Shards,
Chainer's Edict, Essence Warden, Blood Researcher, Follow the Lumarets, Fierce Witchstalker,
Pest Mascot, Carrier Thrall, then Generous Ent. Only legally revealed options are ranked. Other
patterns develop land access and defender mana, select creatures with Winding Way and Lead,
target Spy at its controller, and prefer Lotleth Giant over another Spy for Dread Return. These
are frozen policy choices to test, not a claim of optimal play or a deck modification.

The canonical reuse fixes projected defender/land reads, ranks every sacrifice instance including
duplicates, and connects Dread Return and Quirion preferences to actual additional-cost payment
metadata. A pending-decision callback alone cannot control an already-materialized payment.

## Shared AI boundary

Four narrow existing files gain default-off/default-null hooks: CardAdvisor, AiProfile, AIPlayer
and Strategist. Ordinary mana filtering and its sacrifice-for-mana exception remain unchanged by
default. No whole historical Strategist file is imported: current survival and expiring-condition
behavior remains intact. The mana hook requires an affordable, explicitly advised ability; color
selection reads only the acting player's own hand and runtime-legal colors. Target and payment
rankings use complete legal instance lists before search caps. Target advice is limited to
nonmodal singleton slots; broader target bounds retain generic handling. Singleton distinctness
still comes from the engine. Null advice follows the original generic path. No SDK, rules permission, event schema,
server or UI change is introduced.

The module uses its player's hand, public battlefield/graveyard and legally offered decision
cards. It does not inspect hidden opponent cards, future draws or outcomes. The base profile's
hidden-information determinization remains enabled.

## Required qualification

The dedicated exact-HEAD workflow verifies nine source byte digests and retains separate XML
for Spy policy, default additional-cost regression, accepted Monster pilot regression, exact Spy
main closure and frozen admission; it uploads artifacts on failure. Eighteen fixtures include
actual Dread Return flashback using three Gatecreeper copies, actual Quirion tapped-Forest payment,
null-hook default behavior, and invariance when unknown opponent identities and future library
orders change while visible information stays fixed. All use the fixed excluded regression
entropy in the manifest. Semantic forks are information tests, not full-engine replay claims.

Kotlin runtime and exact artifact audit are still pending. Registry closure or these fixtures
alone cannot authorize games. Official Spy seeds, games, actions and outcomes remain zero. Future
operational work must call the same shared production profile, rather than a separate test copy.


## First validation failure and canonical tap-cost correction

Candidate0d52052baddd3982d0c5acdadfc2a52a7382451a failed dedicated run36091495389 at its first
Kotlin stage:17/18 policy cases passed, while Saruli Caretaker incorrectly fell back to passing.
The original artifact10845294465 (7386bytes, SHA-256
`def32b8f72d890b12f19fd4f63174e948dca90696bbc064743f38e4790a65d50`) and exact failed JUnit XML
are preserved by the companion failure receipt. All four later workflow stages were unexecuted;
they are not credited as passing. This was a capability validation defect, not a deck loss.

The composite-cost enumerator offered the source itself for the second TapPermanents cost even
though its separate tap symbol already required that source. Generic payment chose this first
option; authoritative payment rejected tapping it twice, and the AI discarded the simulated line.
The narrow canonical correction uses the existing hasTapCost flag to exclude that reserved source
from the composite tap-permanent pool. It changes neither the card nor its pilot score.

Three focused engine fixtures require refusal with only the source, an exact other-creature pool
and successful real payment while a forged double tap leaves state/events unchanged, and preserved
self-tapping eligibility for a composite without a separate tap symbol. The dedicated workflow
retains this new stage and expands its source pins to the enumerator and fixture. All18 original
policy cases remain unchanged and must pass before downstream regression stages can run.

Separately, canonical Bestow/Spy registry closure is now accepted via PR151, merge
`1c8bc618e8b3ca0b3b154e2ef3c87ca8ff779698`. Exact source1ad4079b passed435 JUnit cases and three
source-verifier cases; artifact10846221712 has SHA-256
`6a1339c66a2d5c1cfcf060e14501999e4eeeda4831cefccf4f9ae2da8bce3345`. Spy main gaps are zero;
the current six-sideboard queue remains10 identities/28slots. That accepted support does not admit
this failed policy candidate or authorize sampled gameplay.
