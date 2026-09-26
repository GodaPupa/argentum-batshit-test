# Flashback mandatory-cost menu repair

Status: authored; independent source review and execution qualification pending. This is a shared
engine capability, with no gameplay allocation, seed use, outcome, policy, or deck change.

## Source and scope

Base: `9ca83110f5907e66a0c289f8205ed1e24575535c` (accepted main).
Only `CastFromZoneEnumerator` runtime behavior changes. It now includes a spell's printed
`script.additionalCosts` when presenting its flashback cast, together with the keyword's own
non-mana cost. The existing executor already requires both. The repair reuses
`SelectionCostPresentation` and the existing `AdditionalCostData` picker contract. It never
selects or pays a cost on behalf of the actor.

The official Comprehensive Rules effective September 25, 2026, were checked at
<https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt>. Rule 118.9d keeps additional
costs when paying an alternative cost; rule 702.34a defines flashback's alternative cost and exile
replacement. No new SDK vocabulary, card definition, serialization shape, or Assay grammar is added.

### Preserved lineages

All 211 fetched remote refs were grouped by `CastFromZoneEnumerator` blob before editing.

| Blob | Ref count | Difference from canonical source | Disposition |
| --- | ---: | --- | --- |
| `3de2462d8bc2cea7a488a94b7ef1da11466738d0` | 92 | Canonical main, research/review, and the externally owned archived combined v5 runtime | Base retained |
| `53dfac7b334e8452cf7c3cf74c802dcc30b2529d` | 112 | Does not contain canonical Escape enumeration | Do not import the older omission |
| `5525cb3bed275e8504bf96f50dfbae36b9284348` | 6 | Industrial flashback tap-picker and fixed-life checks; separate older Escape implementation | Preserve tap/life behavior through the existing shared picker, retain canonical Escape |
| `0f610cc80e1f41545f8312a46e4e40d16a8de510` | 1 | Omits canonical Escape; adds a Buyback label in a separate cost branch | No change to either unrelated section |

The externally owned Ferocity combined runtime is not modified or republished by this patch.

## Presentation boundary

A flashback offer can represent one existing selection group plus fixed life payments. Composites
are recursively expanded without discarding any child. Fixed life amounts are summed for affordability
and carried in both the engine's `additionalLifeCost` and the action description; execution continues
to auto-pay them through the existing spell handler.

| Cost | Existing picker/payment channel |
| --- | --- |
| Behold | `Behold` / `beheldCards` |
| Sacrifice without a distinct-name constraint | `SacrificePermanent` / `sacrificedPermanents` |
| Tap permanents | `TapPermanents` / `tappedPermanents` |
| Fixed pay life | No selection; existing fixed-life payment |

A second selection group, a distinct-name sacrifice requirement, or another cost shape raises an
explicit `UnsupportedOperationException` naming the unrepresented costs. It is a capability failure,
not an unaffordable legal cast, a free cast, or an omitted payment. Receiving programs must close any
such reachable gap before gameplay admission. This boundary does not authorize pilot-based exclusions.

The client path already exists: `LegalActionEnricher` copies `AdditionalCostData` to the client DTO;
`web-client/src/store/slices/ui/pipelinePhases.ts` presents the server-supplied sacrifice/tap/Behold
candidates and counts. The response fills the existing `AdditionalCostPayment` fields. No new UI,
state mutation, event, or continuation is introduced. Battlefield candidates use the existing cached
projection, including type and controller changes. Life remains visible through the action description
because the current client DTO does not independently carry `additionalLifeCost`.

## Required qualification

New deterministic class: `FlashbackAdditionalCostEnumerationTest` (12 authored cases).
It distinguishes printed sacrifice from keyword sacrifice; sufficient mana without legal fodder;
untargeted menu-to-payment execution; missing, wrong-type, and opponent-controlled payments; remaining
fodder; projected type/control changes and fresh re-enumeration; mana insufficiency with a retained
picker; keyword tap, Behold and fixed life; nested printed-plus-keyword costs; and explicit rejection
of unrepresentable mandatory costs. Fixture casts use no official program seed or allocation.

Minimum source regression gate: this new class, existing `CastFromZoneEnumeratorTest`, `EscapeTest`,
`SelectionCostPresentation` consumers, and exact `LavaDartScenarioTest`. Full integration is required
at shared acceptance. No test result is claimed by this authored record.

Industrial additionally needs its separate exact `EvisceratorsInsightScenarioTest` to traverse the
actual menu at `{4}{B}`, choose an offered artifact/creature, and submit through the existing executor.
Main has no Insight definition, so the shared synthetic test cannot certify that receiving card.
Industrial's separate ordinary-mana/non-mana resource-binding and full-horizon gates remain required.

## Frozen next-game shape inventory

The accompanying `flashback-scope-inventory.json` binds all four R1 main decks and the three first-cell
Ferocity main decks to their file hashes, and identifies source bytes for all 28 distinct R1 cards and
33 distinct first-cell cards. Industrial uses source `fa076215e0e9511333321a12c86ba121efafe567` for this
read-only comparison. Ferocity uses published research deck head `7052de0b64d5d01c3a31465d19157d95089b7d40`
and the immutable archived combined-v5 source mapping (`2a99c52bcfb869ecc0c32443c2b77765abde4b65` plus
freeze `af1b83ff24fcc17ae12b229677533c8b312d0d2feb5249f590c0351320bf8ea7`). The archived failed broader
run remains failed; this inventory conveys no acceptance of that runtime.

| Frozen scope | Actual flashback identity | Mana | Printed cost | Keyword non-mana cost |
| --- | --- | --- | --- | --- |
| Each of Industrial's four R1 main decks | Eviscerator's Insight (4 copies) | `{4}{B}` | Sacrifice one artifact or creature | None |
| Ferocity first-cell Red benchmark | Faithless Looting (3 copies) | `{2}{R}` | None | None |
| Ferocity first-cell Red benchmark | Lava Dart (4 copies) | None | None | Sacrifice one Mountain |
| Ferocity A3-F4 and A3-N0 | None | — | — | — |
| Industrial passive fixture | None (60 Forest) | — | — | — |

No card in these main decks grants flashback, creates a copy of a new card identity, or obtains a
card from outside the frozen lists. Source review of the compositions identifies R1's Blood, Map,
Eldrazi Spawn and Golem tokens, and first-cell Blood, Clue, Fish and Wicked Role tokens. None has a
flashback grant or a castable instant/sorcery face. Weather the Storm copies itself on the stack;
that does not introduce a graveyard flashback cast or a new cost shape. Searches, draws, milling and
recursion stay within the frozen card identities. Sideboards are outside these declared first gates.
Thus an unrepresentable flashback-cost bundle is unreachable in these exact input sets. Any receiving
source or card-list change requires a fresh compatibility check; policy avoidance is not an exclusion.

The existing `LavaDartScenarioTest` flashback case is strengthened to obtain its targeted cast from the
real menu and select the offered Mountain before submitting. The synthetic shared fixture is not used
as a substitute for the exact Insight receiving test.

The push-only workflow `.github/workflows/shared-flashback-cost-menu.yml` requires exactly 66 actual
cases: the 12 new menu cases, existing zone enumeration 20, cost utilities 23, alternative-cost choice 4,
Escape 5 and exact Lava Dart 2. Each selected Gradle test task uses the task-specific `--rerun` option
so source-compatible dependency build outputs can be reused while the tests execute anew. The option
is documented in <https://docs.gradle.org/current/userguide/command_line_interface.html#sec:rerun_tasks>.
Logs, exact checkout/source hashes, actual XML case names and XML hashes are uploaded even on failure.
A passing targeted manifest still explicitly withholds full integration, exact Insight receiving
qualification, and gameplay authorization.

The first targeted attempt, [run 36249007093](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36249007093),
failed during test compilation before any cases executed. The inline fixture passed a nullable
additional cost to the SDK's nonnullable two-argument `flashback` overload. The successor chooses the
one-argument overload for a null keyword cost and retains the two-argument overload for an actual
keyword cost. Printed-cost entries, runtime code, assertions and the 66-case scope are unchanged.
The [failed-attempt receipt](evidence/flashback-cost-menu-36249007093/receipt.json) binds the original
clean source and complete uploaded artifact; no qualification or gameplay result is inferred from it.

The [second attempt, run 36249869762](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36249869762),
executed 64 engine cases: 63 passed and the unsupported-cost fixture failed on the capitalization of
its expected message. The runtime raised the intended `UnsupportedOperationException` with the SDK
description `Discard a card`. The successor requires that exact description; no runtime behavior or
exception assertion changes. Lava Dart's step had not run. Its [failed-attempt receipt](evidence/flashback-cost-menu-36249869762/receipt.json)
preserves the complete artifact and all five XML suites; the 66-case gate remains unpassed.
