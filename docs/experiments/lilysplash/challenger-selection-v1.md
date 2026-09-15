# Lilysplash Mentor Selection Challenger v1

Stage 4 item 1 from `experiment-plan.md`: "cheaper selection and tutors," tested alone per the plan's
one-variable-at-a-time rule. No land, ramp/fixing card, Aura, untap/flicker piece, counterspell/
protection spell, or win-condition card is touched -- only two nonland, non-engine spells are swapped
for two cheaper selection spells.

## Provenance

- Control: `submitted-v0.1.txt`, SHA-256 `f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525`
- Challenger: `challenger-selection-v1.txt`, SHA-256
  `2937001c636529e18109c7bc5649e9a8f78830b9804fd27d336b1a1e50caedcc`
- Guardrails enforced in code (`checkDeckGuardrails` in `LilysplashOpeningHandBenchmark.kt`): both
  decks are 99 library cards plus the Lilysplash Mentor commander, and neither has a repeated
  nonbasic land or spell.

## Scoping this package

There is no real common-rarity nonland tutor in Simic colors -- Magic simply doesn't print one -- so
"cheaper selection and tutors" cannot mean adding a tutor; the deck's one real tutor effect (Muddle the
Mixture's search-a-CMC-2-card mode) is already in the control list and untouched. The package instead
targets the "cheaper selection" half directly: it lowers the average cost of finding the deck's engine
pieces without touching anything another Stage 4 item already owns.

The two cheapest, purest selection spells that exist -- Opt and Preordain, both `{U}`, both already
implemented at common rarity in this engine (scry-then-draw, no conditions, no targets) -- replace two
cards that are neither part of the land base (item 6), an Aura (item 2), an untap/Freed/flicker piece
(item 3), a counterspell or protection spell (item 4), nor a win condition (item 5):

| Removed | Replaced with | Why the removed card was the weakest of the interaction suite |
|---|---|---|
| Whirlpool Rider | Opt | A 1/1 body whose entire function is shuffling your hand into the library and redrawing that many -- high-variance, and actively bad after any setup (it discards a hand you already like). Opt is a plain, low-variance scry 1 + draw for the same one mana. |
| Capsize | Preordain | A repeatable bounce spell with buyback `{3}`; real in a dedicated control shell, but mana-intensive and uncoupled from this deck's actual plan (assemble the mana/mill combo, not lock the board). Preordain is scry 2 + draw for `{U}`, the cheapest, least ambiguous selection in the format. |

Both removed cards are blue, both added cards are blue, so the deck's color balance among nonland
spells is unchanged -- the swap is isolated to "what does this blue mana buy," not to how much blue
or green the deck runs.

Land count, total deck size (99 + commander), and every card belonging to another Stage 4 item's
territory are unchanged from the control list, so any difference the benchmark measures is
attributable to the selection swap alone.

## Method

`LilysplashOpeningHandBenchmark.kt` test `"Lilysplash selection challenger v1 preflight"` (gated on
`-DlilysplashSelectionChallenger=true`, run via `just lilysplash-selection-challenger`) replays both
decks across the same 12 frozen seeds (`2026091401`-`2026091412`), both seats, under the
commander-aware keep rule Stage 3 settled on. It reports the same land/U/G/fixer columns as the Stage
3 and land-challenger tables (as a sanity check that the swap left the mana base untouched), plus two
new columns: `selectors` (hands containing a card from the deck's full selection suite -- Brainstorm,
Ponder, Faerie Seer, Serum Visionary, Cloudkin Seer, Sea Gate Oracle, Pond Prophet, Mulldrifter,
Elvish Visionary, Llanowar Visionary, Pondering Mage, Muddle the Mixture, Coiling Oracle, plus Opt/
Preordain for the challenger) and `cheapSelectors` (the `{U}`-costed subset: Brainstorm, Ponder,
Faerie Seer, plus Opt/Preordain for the challenger).

## Result

Status: pending. This document is committed alongside the code and decklist so the challenger's
provenance is frozen before the CI run; the `Result` and `Verdict` sections below will be filled in
from the actual `land-challenger`-style CI log once `selection-challenger` has run on this branch, the
same way `challenger-land-v1.md` was updated after its first (invalid) and corrected CI runs. No
result is reported here until that log exists.
