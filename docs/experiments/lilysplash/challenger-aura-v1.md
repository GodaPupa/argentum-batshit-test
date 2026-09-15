# Lilysplash Mentor Aura Challenger v1

Stage 4 item 2 from `experiment-plan.md`: "reduced four-mana Aura density," tested alone per the plan's
one-variable-at-a-time rule. No land, selection spell, untap/Freed piece, counterspell/protection spell,
or win-condition card is touched -- only the deck's single four-mana Aura is swapped for a cheaper,
non-Aura mana-development artifact.

## Provenance

- Control: `submitted-v0.1.txt`, SHA-256 `f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525`
- Challenger: `challenger-aura-v1.txt`, SHA-256
  `cb7cdb7cd9b52a57ef4e18934462ebee763d5a2bdb7415461ae1b274dd4fc699`
- Guardrails enforced in code (`checkDeckGuardrails` in `LilysplashOpeningHandBenchmark.kt`): both
  decks are 99 library cards plus the Lilysplash Mentor commander, and neither has a repeated
  nonbasic land or spell.

## Scoping this package

The submitted list runs thirteen Auras. Twelve of them cost 1-3 mana (Utopia Sprawl and Wild Growth at
`{G}`; Fertile Ground, Frogify, and Kasmina's Transmutation at 2; Freed from the Real, Gift of Paradise,
Grafted Growth, Nature's Embrace, New Horizons, Overgrowth, and Sheltered Aerie at 3). Exactly one sits
at four mana: **Dawn's Reflection** (`{3}{G}`, enchant land, that land taps for two mana of any one
color instead of one). That makes it the entire target population for "reduced four-mana Aura density"
-- the package only has one card it can touch.

Dawn's Reflection is a real 2-for-1 risk that the deck's cheaper Auras share less of: committing four
mana to a single enchantment means losing both cards to any removal that hits the land it's attached to,
and the deck has no protection built around it specifically (Stage 4 item 4 covers general stack
protection, untouched here). The replacement needed to (a) not be an Aura, so it can't be blown out the
same way, (b) cost meaningfully less than 4, and (c) do a comparable job -- helping find/fix the mana
this two-color deck needs -- without drifting into another Stage 4 item's territory (not a land itself,
so it doesn't touch item 6's land-base scope; not a cantrip or tutor, so it doesn't touch item 1's
selection scope; no untap or protection text, so items 3 and 4 are untouched; no power/toughness or
damage, so item 5's win-condition scope is untouched).

**Wayfarer's Bauble** (`{1}`, common artifact, `{2}, {T}, Sacrifice: search your library for a basic
land card, put it onto the battlefield tapped, then shuffle`) fits all three constraints. It is already
implemented at common rarity in this engine, real (not approximated), and colorless, so it doesn't shift
the deck's color balance among nonland spells the way a colored replacement would.

| Removed | Replaced with | Why |
|---|---|---|
| Dawn's Reflection | Wayfarer's Bauble | A `{3}{G}` Aura that dies with its land to any removal, replaced by a `{1}` colorless artifact that fetches a basic land for a further `{2}` and a sacrifice -- same "develop and fix mana" job, a third of the up-front commitment, and no attachment to blow out. |

Wayfarer's Bauble is added to the shared `fixing` set in `LilysplashOpeningHandBenchmark.kt` alongside
the existing fetchlands (Ash Barrens, Escape Tunnel, Evolving Wilds, Terramorphic Expanse, Lórien
Revealed) -- it does the same "find the color I'm missing" job, just via an artifact instead of a land.

Land count, total deck size (99 + commander), and every card belonging to another Stage 4 item's
territory are unchanged from the control list, so any difference the benchmark measures is
attributable to this one swap alone.

## Method

`LilysplashOpeningHandBenchmark.kt` test `"Lilysplash aura challenger v1 preflight"` (gated on
`-DlilysplashAuraChallenger=true`, run via `just lilysplash-aura-challenger`) replays both decks across
the same 12 frozen seeds (`2026091401`-`2026091412`), both seats, under the commander-aware keep rule
Stage 3 settled on. It reports the same land/U/G/fixer columns as the Stage 3, land-challenger, and
selection-challenger tables -- for this package the fixer column is not just a sanity check, it's the
one metric a single-card artifact-for-Aura swap can actually move, since Wayfarer's Bauble is now part
of the tracked `fixing` set.

This is a narrower test than either prior package: those swapped two cards each, this swaps one, so any
real signal here is smaller and more easily swamped by reshuffle noise.

## Result

CI run (commit `e873e71a74`, `Lilysplash Preflight` #7, `aura-challenger` job,
<https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34922529134>) succeeded in 4m 9s
(land-challenger and selection-challenger also stayed green in the same run, confirming this change
didn't disturb either prior package), which on its own proves the benchmark's internal guardrails and
the `rows.size == seeds.size * 2 * 2` assertion held for both decks -- no approximated cards, no dropped
hands. The table below is the deterministic output of that exact commit run locally beforehand (same
fixed seeds, same shuffle implementation, no wall-clock or network input to the benchmark) to confirm CI
would pass before pushing; CI's green run on the identical commit is the independent proof that this is
what actually executes, not a substitute source for the numbers.

Commander-aware policy, same 12 seeds, both seats, 24 samples per deck.

| Metric | Control | Challenger-aura-v1 | Delta |
|---|---:|---:|---:|
| Mean mulligans | 0.542 | 0.583 | +0.041 |
| Keep seven | 16/24 | 14/24 | -2 |
| Keep six | 3/24 | 6/24 | +3 |
| Keep five | 5/24 | 4/24 | -1 |
| Kept with 0-1 land | 2/24 | 2/24 | — |
| No direct blue source | 6/24 | 6/24 | — |
| No direct green source | 4/24 | 2/24 | -2 |
| Fixers per hand (avg) | 0.417 | 0.458 | +0.041 |
| Hands with zero fixers | 15/24 (62.5%) | 13/24 (54.2%) | -2 |

The mulligan/keep-count columns move by reshuffle noise, the same artifact already documented in both
prior packages: swapping even one card out of 99 reshuffles the whole draw sequence for every seed from
that point on, so unrelated aggregate counts can shift by chance. It's worth flagging explicitly here
that neither Wayfarer's Bauble nor Dawn's Reflection is in the `blueSources`/`greenSources`/`fetchBoth`
sets the mulligan policy actually reads (`hasCommanderColors`) -- this swap cannot influence the
mulligan decision by design, so the entire mulligan/keep movement in this table is noise, not signal.

The fixer column is the one this package can actually speak to, and it moves in the predicted direction
and roughly the predicted size: going from 5 to 6 tracked fixing cards in a 99-card library raises the
expected fixer count in a hand of average kept size (~6.3 cards across this sample) by roughly
6.3 x 1/99 =~ 0.064, in the neighborhood of the observed +0.041. Zero-fixer hands dropped from 15/24 to
13/24, consistent with the same small shift.

The green-access column ("no direct green source") dropping from 4/24 to 2/24 looks like a real
improvement but isn't one this package caused on purpose: Wayfarer's Bauble doesn't produce green mana
by itself (it's a colorless artifact whose fetch ability plays out on a later turn, well outside what an
opening-hand snapshot measures), so this is the same reshuffle noise as the mulligan columns, not a
second real effect.

## Verdict

**Aura package v1 is accepted, on the weakest evidence base of the three Stage 4 packages tested so
far.** The guardrails hold for both decks, the one column this single-card swap can causally move
(fixers) shifts in the predicted direction at roughly the predicted size, and no other column shows a
change this benchmark's own noise floor can't explain.

The actual hypothesis behind "reduced four-mana Aura density" -- that a `{3}{G}` Aura's 2-for-1 exposure
to removal is a real liability worth trading for a cheaper, unattached mana source -- is not something
an opening-hand benchmark can measure at all; that risk only materializes deep into a game, against an
opponent who actually has removal, aimed at the specific permanent the Aura is attached to. This
package's acceptance rests on "the swap doesn't cost anything the benchmark can see and cheapens the
deck's most expensive, most exposed mana piece," not on a measured reduction in blowout rate. Held
pending combination with the other Stage 4 packages, and pending the same full-game validation
infrastructure the selection package's verdict flagged as not yet existing.
