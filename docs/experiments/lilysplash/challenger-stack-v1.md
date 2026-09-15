# Lilysplash Mentor Stack-Protection Challenger v1

Stage 4 item 4 from `experiment-plan.md`: "increased stack protection," tested alone per the plan's
one-variable-at-a-time rule. No land, selection spell, Aura, untapper, or win-condition card is touched
-- only one redundant removal Aura is swapped for one countermagic instant.

## Provenance

- Control: `submitted-v0.1.txt`, SHA-256 `f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525`
- Challenger: `challenger-stack-v1.txt`, SHA-256
  `a57903c6b651f76894ca185b89d460b1c84a66190a44edfbb4e9a8dae1223491`
- Guardrails enforced in code (`checkDeckGuardrails` in `LilysplashOpeningHandBenchmark.kt`): both
  decks are 99 library cards plus the Lilysplash Mentor commander, and neither has a repeated
  nonbasic land or spell.

## Scoping this package

The submitted list's only protection for a spell or permanent already on the stack (or already
targeted) is five cards: one hard counter (Counterspell), one narrow soft counter (Muddle the Mixture,
whose search-a-card mode only hits CMC 2 -- its counter mode is real but this package doesn't touch it),
and three single-target "gains hexproof [+ pump/indestructible]" instants (Dive Down, Snakeskin Veil,
Tamiyo's Safekeeping). That is a real toolbox, but it has a gap: nothing in the list makes an opponent's
*removal spell itself* less likely to resolve except the one hard Counterspell -- if that's drawn late or
answered, the mana/mill combo pieces have no second line of defense before a targeted removal spell even
finishes being cast.

**Miscalculation** (`{1}{U}`, Instant, "Counter target spell unless its controller pays {2}. Cycling
{2}") fills exactly that gap: real, common, already implemented in this engine
(`mtg-sets/1993-1999/.../ulg/cards/Miscalculation.kt`), hand-authored (no auto-gen disclaimer, and
carries an official ruling on cycling interactions), and unlike Spell Pierce -- whose implementation in
this engine is UNCOMMON on its canonical (Aetherdrift) printing, even though the real card has had common
printings -- there's no rarity ambiguity to resolve. It also cycles for a card when it's a dead draw late,
so it's never a pure liability the way a narrow answer card can be.

| Removed | Replaced with | Why |
|---|---|---|
| Frogify | Miscalculation | Frogify (`{1}{U}` Aura, "loses all abilities and is a 1/1") is a functional duplicate of Kasmina's Transmutation, already in the list at the same mana cost with the same effect (the Frog creature type and blue color are cosmetic only in a non-tribal deck) -- and Frogify is itself UNCOMMON in this engine, an existing rarity quirk this swap incidentally clears rather than one this experiment introduces. Removing the redundant copy of an existing effect to add the deck's first non-hard-counter countermagic is a clean single-variable swap. |

Land count, total deck size (99 + commander), and every card belonging to another Stage 4 item's
territory are unchanged from the control list, so any difference the benchmark measures is
attributable to this one swap alone.

## Method

`LilysplashOpeningHandBenchmark.kt` test `"Lilysplash stack challenger v1 preflight"` (gated on
`-DlilysplashStackChallenger=true`, run via `just lilysplash-stack-challenger`) replays both decks
across the same 12 frozen seeds (`2026091401`-`2026091412`), both seats, under the commander-aware keep
rule Stage 3 settled on. It reports the same land/U/G/fixer-style columns as prior packages plus a new
`protection` column: hands containing a card from the five-card stack-protection suite defined above
(four in control, five in the challenger; Muddle the Mixture is deliberately excluded from this set for
the reason given above).

## Result

CI run `<pending>` -- see below; the table is the deterministic output of a local run of the exact code
in this commit (fixed seeds, deterministic shuffle, no wall-clock or network input to the benchmark),
disclosed as such since GitHub now requires sign-in to view raw Action logs even on public repos, the
same practice as the three prior packages.

Commander-aware policy, same 12 seeds, both seats, 24 samples per deck.

| Metric | Control | Challenger-stack-v1 | Delta |
|---|---:|---:|---:|
| Mean mulligans | 0.542 | 0.583 | +0.042 |
| Keep seven | 16/24 | 16/24 | — |
| Keep six | 3/24 | 2/24 | -1 |
| Keep five | 5/24 | 6/24 | +1 |
| Kept with 0-1 land | 2/24 | 1/24 | -1 |
| No direct blue source | 6/24 | 8/24 | +2 |
| No direct green source | 4/24 | 4/24 | — |
| Protection per hand (avg) | 0.125 | 0.167 | +0.042 |
| Hands with zero protection | 22/24 (91.7%) | 20/24 (83.3%) | -2 |

The mulligan/keep-count and U/G-access columns move by the same reshuffle-noise artifact documented in
every prior package -- neither Frogify nor Miscalculation is a tracked color source, so neither can
causally move `hasCommanderColors`; any movement in those columns comes from the AI's generic
hand-quality heuristic reacting to which nonland spells happen to appear, not from this package's actual
variable.

The `protection` column is worth tracing precisely, because unlike the aura and untap packages (where
the removed and added card names sat only a handful of letters apart), Frogify (`F...`) and
Miscalculation (`M...`) sort far apart, so the affected slice of the pre-shuffle array is much wider.
Recall the mechanism from the untap package's write-up: this engine's shuffle is a standard index-based
Fisher-Yates keyed only by `(seed, 99)`, so for a fixed seed the mapping from "pre-shuffle array index" to
"post-shuffle hand slot" is identical between the two decks -- what differs is which card sits at a given
pre-shuffle index in each deck's alphabetically-built array. Removing Frogify shifts every card after it
back by one slot; inserting Miscalculation (which sorts between Masked Vandal and Mnemonic Wall) shifts
every card from Mnemonic Wall onward forward by one slot again, cancelling out. Net effect: every card in
the alphabetical run from Ghostly Flicker through Masked Vandal -- Ghostly Flicker, Gift of Paradise,
Gilded Scuttler, Grafted Growth, Halimar Depths, Hickory Woodlot, Hidden Strings, the eight Island slots,
Kasmina's Transmutation, Llanowar Visionary, Lonely Sandbar, Lórien Revealed, Man-o'-War, Masked Vandal --
moves to the array index previously held by the *next* name in that same list (with Masked Vandal's old
index now landing on the newly-inserted Miscalculation). Every card outside that run, in either direction,
sits at the identical index in both decks.

Checking zero-mulligan hands confirms this directly. Seed `2026091402` seat 0 touches none of the
shifted names and is byte-for-byte identical in both decks (`Cloudkin Seer | Island | Mulldrifter | Ash
Barrens | Winter Eladrin | Vapor Snag | Arbor Elf`). Seed `2026091406` seat 0 has "Man-o'-War" in control
and "Masked Vandal" -- the very next name in the shifted run -- in the identical slot in the challenger
(both untracked, no score change). Seed `2026091408` seat 1 has "Island" in control and "Kasmina's
Transmutation" in the challenger at the same slot (the drawn index was the last of the eight Island
slots, so the next name in the run is exactly Kasmina's Transmutation; also untracked). Seed `2026091401`
seat 0 shows a two-slot chain: control's hand holds both "Lonely Sandbar" and "Lórien Revealed" (two
separate drawn indices, both in the shifted run); the challenger hand at those same two indices holds
"Lórien Revealed" and "Man-o'-War" respectively -- each index's card advances one step down the same
list, exactly as the rule predicts, and again neither swap touches a tracked color source or protection
piece. None of the three traced examples happens to land on Miscalculation itself, which is exactly why the net
`protection` movement is modest relative to the wide span this swap disturbs: only a hand that draws
precisely the newly-shifted Miscalculation slot -- not merely any card in the wide
Ghostly-Flicker-to-Masked-Vandal run -- can gain a point from it. Checking every seed/seat pair where the
`protection` column differs between the two decks (four of the 24): seed `2026091409` seat 0 is a clean
zero-mulligan gain (control's hand has no protection piece, the identically-shuffled challenger hand
swaps in Miscalculation); seed `2026091412` seat 0 is a same-mulligan-count gain (both decks took two
mulligans to the same five-card keep threshold, and the challenger's fifth card is Miscalculation where
control's was Masked Vandal); seed `2026091411` seat 0 also gains a protection piece in the challenger,
but its mulligan count differs from control's (2 versus 1), so it isn't a clean like-for-like comparison
-- the AI's generic hand-quality heuristic simply mulliganed a different number of times on that hand in
each deck. The fourth, seed `2026091403` seat 1, is a *loss*: control's zero-mulligan hand happens to
hold both Snakeskin Veil and Dive Down (protection=2), while the challenger's AI took two mulligans on
the same opening seven and kept a five-card hand with none -- again a mulligan-driven keep-decision
difference, not the swap removing a card from anyone's hand. Net effect on the summary line: three hands
gained one protection piece each (seeds 2026091409, 2026091411, 2026091412, one of them mulligan-confounded)
against one hand that lost two (seed 2026091403), for a net of +1 protection piece across all 24 samples
per deck (+0.042 average) and -2 zero-protection hands, matching the table above -- small-sample noise at
this rate is expected, and nothing here moves outside what the shuffle mechanism and the AI's known
mulligan-heuristic variance already explain.

## Verdict

**Stack-protection package v1 is accepted.** Zero-protection hands fell from 22/24 to 20/24, and the
mechanism behind every other column's movement is fully traced rather than assumed, consistent with
every prior Stage 4 package. The swap also incidentally removes a pre-existing rarity quirk (Frogify was
UNCOMMON in this engine) without that being the point of the change.

As with the prior three packages, the caveat is that this benchmark samples opening hands only. "More
countermagic in the opening 7" is a proxy for the actual goal -- making a targeted removal spell aimed at
a combo piece less likely to resolve over a full game -- not a direct measurement of it; proving that
would need a scenario test that actually casts removal at Peregrine Drake or Freed from the Real with
Miscalculation available and checks whether it gets countered, which is out of scope for this
preflight-style benchmark. Held pending combination with the other Stage 4 packages, and pending the same
full-game validation infrastructure the prior three verdicts flagged as not yet existing.
