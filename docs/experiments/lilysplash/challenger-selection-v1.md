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

CI run (commit `c218e2b650`, `Lilysplash Preflight` #6, `selection-challenger` job,
<https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34916553630>) succeeded in 5m 12s,
which on its own proves the benchmark's internal guardrails and the `rows.size == seeds.size * 2 * 2`
assertion held for both decks -- no approximated cards, no dropped hands. GitHub now requires sign-in
to read a public run's raw log text, so the table below is the deterministic output of that exact
commit run locally beforehand (same fixed seeds, same shuffle implementation, no wall-clock or network
input to the benchmark) to confirm CI would pass before pushing; CI's green run on the identical commit
is the independent proof that this is what actually executes, not a substitute source for the numbers.

Commander-aware policy, same 12 seeds, both seats, 24 samples per deck.

| Metric | Control | Challenger-selection-v1 | Delta |
|---|---:|---:|---:|
| Mean mulligans | 0.542 | 0.583 | +0.041 |
| Keep seven | 16/24 | 14/24 | -2 |
| Keep six | 3/24 | 6/24 | +3 |
| Keep five | 5/24 | 4/24 | -1 |
| Kept with 0-1 land | 2/24 | 1/24 | -1 |
| No direct blue source | 6/24 | 1/24 | -5 |
| No direct green source | 4/24 | 4/24 | — |
| Selectors per hand (avg) | 0.750 | 0.917 | +0.167 |
| Hands with zero selectors | 10/24 (41.7%) | 6/24 (25.0%) | -4 |
| Cheap selectors per hand (avg) | 0.125 | 0.208 | +0.083 |

The land/U/G columns are a sanity check, not the point of this package -- nothing about the land base
changed, so their movement (including the -5 on "no direct blue source") is reshuffle noise from
swapping 2 of 99 library cards, the same effect `challenger-land-v1.md` already documented for its own
unrelated columns. It is *not* evidence this package fixes color screw; it's evidence that a 2-card
swap reshuffles every seed's full draw sequence, so unrelated metrics can move by chance alone. The
metrics this package is actually about are the last three rows.

Zero-selector hands (an opening 7 with no cantrip, no looter, no card-selection creature -- nothing to
dig toward a missing combo piece) dropped from 10/24 to 6/24. That size of move matches simple
arithmetic: adding 2 selection spells to a 99-card library raises the chance any given 7-card hand
contains at least one by roughly 2x7/99 =~ 14 percentage points, close to the observed 16.7-point drop.
Opt appeared in 3 of the 24 challenger hands, Preordain in 0 (expected value for a single copy at this
sample size is ~1.7 each; 3-and-0 is ordinary variance, not a sign either card underperformed).

Three representative pairs (same seed and seat, control vs. challenger):

- **Seed 2026091404, seat 1** -- same mulligan count (1) and land count (2) in both. Control's kept
  hand had one selector (Brainstorm); the challenger's had two (Brainstorm and Opt). A clean,
  same-keep-decision comparison where the added card is pure upside.
- **Seed 2026091411, seat 1** -- same mulligan count (0) and land count (3) in both. Control's hand had
  zero selectors; the challenger drew Opt into the same slot, going from zero to one. Another
  apples-to-apples case with no confound.
- **Seed 2026091406, seat 0** -- the one case worth flagging: the challenger's kept hand does contain
  Opt, but it cost two extra mulligans to get there (0 -> 2) versus control's zero-mulligan seven. This
  is the land/U/G-style reshuffle noise described above, not a cost of running fewer copies of
  Whirlpool Rider or Capsize -- the two decks draw different card sequences from this seed onward the
  moment any one of the 99 cards changes, independent of which two cards they were.

## Verdict

**Selection package v1 is accepted, with a narrower evidence base than the land package.** The
zero-selector-hand reduction is real, lands almost exactly where simple probability predicts, and every
spot-checked pair traces back to its actual cause (either the new card showing up with no other change,
or ordinary reshuffle noise already documented as a known artifact of this benchmark). Nothing traces a
regression to the specific cards removed.

The caveat the plan's promotion bar asks for is worth stating plainly: this benchmark only samples
opening hands, not full games, so "more selection in the opening 7" is a proxy for the actual goal
(finding the mana/mill combo faster over a whole game), not a direct measurement of it. The land
package's result was a first-order measurement of the thing it claimed to fix (color access in the
opening hand *is* the land base's job); this package's opening-hand selector count is one step removed
from what "cheaper selection" is ultimately supposed to buy. Held pending combination with the other
Stage 4 packages, same as the land package, and pending eventual full-game validation once that
infrastructure exists.
