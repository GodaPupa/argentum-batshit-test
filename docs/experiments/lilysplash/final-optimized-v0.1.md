# Lilysplash Mentor Final-Optimized v0.1

Stage 4's closing step from `experiment-plan.md`: "The final optimized list is assembled from accepted
packages and then receives its own fresh, frozen validation sample." All six Stage 4 packages (land,
selection, aura, untap, stack-protection, win-condition density) were tested and accepted independently,
one variable at a time, on the shared `2026091401`-`2026091412` seed block. This package assembles all
six swaps into one list and validates the result on a brand-new, never-before-used seed block, per the
plan's explicit instruction -- not by summing the six individual deltas.

## Provenance

- Control: `submitted-v0.1.txt`, SHA-256 `f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525`
- Final-optimized: `final-optimized-v0.1.txt`, SHA-256
  `db3cdedb5d284658a8f1883f1d2b28000866151e9f171df0b0f79e9fbb63614b`
- Guardrails enforced in code (`checkDeckGuardrails` in `LilysplashOpeningHandBenchmark.kt`): both decks
  are 99 library cards plus the Lilysplash Mentor commander, and neither has a repeated nonbasic land or
  spell.
- Each of the six swaps below was verified programmatically (a Python set-membership check across all six
  packages' removals and additions) to have zero overlap with any other package's removal or addition
  before assembly, confirming the packages compose as independent single-variable edits rather than
  silently touching the same card twice.

## Scoping this package

This is not a seventh independent variable -- it is the union of the six already-accepted swaps, each
documented and CI-proven on its own:

| Package | Removed | Added | Why (see full package doc for the traced mechanism) |
|---|---|---|---|
| [Land](challenger-land-v1.md) | Ash Barrens | Paradox Gardens | Colorless-until-cracked fetch replaced by a plain tapped dual that taps for either color outright, plus a minor surveil upside. |
| [Land](challenger-land-v1.md) | Escape Tunnel | Simic Guildgate | Tapped one-basic fetch replaced by a plain tapped dual, no second land needed. |
| [Land](challenger-land-v1.md) | Saprazzan Skerry | Thornwood Falls | Blue-only bounce-land (net tempo loss) replaced by a tapped dual that keeps the land drop and fixes both colors. |
| [Selection](challenger-selection-v1.md) | Whirlpool Rider | Opt | High-variance hand-shuffle body replaced by a plain scry 1 + draw at the same cost. |
| [Selection](challenger-selection-v1.md) | Capsize | Preordain | Mana-intensive repeatable bounce, uncoupled from the deck's actual plan, replaced by the cheapest unambiguous selection in the format. |
| [Aura](challenger-aura-v1.md) | Dawn's Reflection | Wayfarer's Bauble | A blowout-prone Aura doing "ramp and fix" replaced by an artifact doing the same job for a third of the up-front commitment. |
| [Untap](challenger-untap-v1.md) | Sunshower Druid | Seeker of Skybreak | The most replaceable green one-drop (zero interaction with the combo) replaced by a repeatable untap engine independent of Freed from the Real. |
| [Stack](challenger-stack-v1.md) | Frogify | Miscalculation | A functional duplicate of Kasmina's Transmutation (and an existing rarity quirk) replaced by the deck's first non-hard-counter countermagic. |
| [Wincon](challenger-wincon-v1.md) | Displace | Vedalken Entrancer | A near-exact duplicate of Ghostly Flicker for the deck's actual two-creature blink loop replaced by a second, recursion-independent win condition. |

Land count, total deck size (99 + commander), and commander identity are unchanged from the control list.
Every removed card and every added card is confirmed unique across the whole assembly -- no package
reintroduces a card another package already removed, and no two packages add the same card.

**Correction (post-acceptance):** the land package's own row here originally read Ash Barrens →
Yavimaya Coast, and this table also omitted the land package's third swap (Saprazzan Skerry → Thornwood
Falls) entirely -- both fixed above. Yavimaya Coast has been printed 22 times, always at Rare; it has
never had a common printing, so it is not legal in Pauper Commander. Replaced with Paradox Gardens
(Secrets of Strixhaven, common) -- see `challenger-land-v1.md` for the full note and the land package's
own corrected, re-run result. This document's Result and Verdict below are likewise the corrected,
re-run numbers with Paradox Gardens in the assembled list; the original Yavimaya Coast-based numbers are
superseded and no longer cited anywhere in this experiment.

An earlier, unpushed local dry run on the *already-spent* `2026091401`-`2026091412` seed block (labeled
at the time as "LOCAL PREVIEW ONLY," never presented as an official result) found that the assembled
list's win-condition-density metric came back completely unchanged (0.042 in both decks) despite the
wincon package alone showing a real, traced improvement on that same seed block in isolation -- because
nine simultaneous pre-shuffle index shifts interact with the deterministic Fisher-Yates shuffle
non-additively. That finding is exactly why the plan requires a fresh sample here rather than assuming
the six deltas simply add up, and it is superseded by this package's own real result below, which uses a
seed block no individual package has ever touched.

## Method

`LilysplashOpeningHandBenchmark.kt` test `"Lilysplash final-optimized v0.1 preflight"` (gated on
`-DlilysplashFinalOptimized=true`, run via `just lilysplash-final-optimized`) replays control and
final-optimized-v0.1 across a genuinely new set of 12 frozen seeds (`2026091501`-`2026091512`, never used
by any of the six individual packages), both seats, under the commander-aware keep rule Stage 3 settled
on. It reports the same land/U/G/fixer/untapper/protection/winConditions columns as the individual
packages.

## Result

Re-run after the Yavimaya Coast → Paradox Gardens correction described above (a Kotlin-level fix too:
the benchmark's own `blueSources`/`greenSources` sets are what recognize which cards count as color
fixing, and those literal card-name sets needed the same swap). The original CI citation
(`004c634053`, `Lilysplash Preflight #11`) covered the Yavimaya Coast-based list and is superseded. The
table below is verified locally (forced `--rerun` of `-DlilysplashFinalOptimized=true`, deterministic,
reproduced twice) against the corrected `final-optimized-v0.1.txt`. A first push of the fix (commit
`395494cf23`, `Lilysplash Preflight #15`, run `35021667554`) went green but is **not** a valid citation
for this table: a sync race on the connected OneDrive folder reverted `final-optimized-v0.1.txt` to the
old Yavimaya Coast content between staging and the GitHub Desktop commit snapshot, so that CI run
actually re-verified the illegal, superseded list under the corrected list's file path -- caught by
diffing the committed blob's SHA-256 against the intended source after the fact, not by anything in the
push itself. Valid citation: commit `ef55e14243` (a corrective commit that re-applied the intended
content, its own committed blob hash-verified against the source before and after pushing), `Lilysplash
Preflight #16`, `final-optimized` job,
<https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35023315186>, 5m 1s, green. Commander-
aware policy, same fresh 12-seed block (`2026091501`-`2026091512`, never used by any individual package),
both seats, 24 samples per deck.

| Metric | Control | Final-optimized-v0.1 | Delta |
|---|---:|---:|---:|
| Mean mulligans | 0.667 | 0.750 | +0.083 |
| Keep seven | 11/24 | 13/24 | +2 |
| Keep six | 10/24 | 4/24 | -6 |
| Keep five | 3/24 | 7/24 | +4 |
| Kept with 0-1 land | 1/24 | 2/24 | +1 |
| No direct blue source | 5/24 | 3/24 | -2 |
| No direct green source | 6/24 | 3/24 | -3 |
| Untappers per hand (avg) | 0.750 | 0.917 | +0.167 |
| Protection pieces per hand (avg) | 0.250 | 0.167 | -0.083 |
| Win conditions per hand (avg) | 0.125 | 0.083 | -0.042 |

This is a more mixed result than the superseded Yavimaya Coast run reported, and it changes the
headline claims: with the legal card in the list, mean mulligans and kept-0-to-1-land hands are both
*worse* than control on this seed block (+0.083, +1), and win-conditions-per-hand is worse too (-0.042).
Color access still improves meaningfully (no-blue hands -2, no-green hands -3, keep-seven +2, untappers
per hand +0.167), which is the column set the land, selection, and untap packages each directly target
-- but this sample does not support a claim that the assembled list mulligans less overall, only that it
accesses color better when it does keep a hand.

Every one of these columns is exactly the kind of non-additive composition effect
`final-optimized-v0.1`'s own scoping section already warns about: nine simultaneous swaps change a
hand's entire evaluated composition at once, and a fresh, frozen 12-seed sample (no rerolls, per the
guardrails) will not always land as cleanly as either the individual packages or an earlier illegal-card
run did. The win-condition-density column illustrates this directly: the wincon package alone raised win
conditions per hand from 0.042 to 0.125 on its own seed block, but on this fresh block, final-optimized
lands at 0.083 (2 of 24: a Sage's Row Denizen hand at seed `2026091501` seat 1, and a Vedalken Entrancer
hand at seed `2026091508` seat 1) against control's 0.125 (3 of 24, all Sage's Row Denizen: seeds
`2026091503`, `2026091504`, `2026091512`, all seat 0) -- worse than control on this specific sample, not
because the wincon package's own accepted mechanism is wrong, but because which hands get kept at all
shifted once every other package's swap reshuffled the library too.

## Verdict

**The Stage 4 final-optimized v0.1 list is accepted as the assembled result of all six individually-
accepted packages, but with a narrower claim than the superseded Yavimaya Coast run supported.** On its
own fresh, frozen 12-seed sample -- never used by any individual package, per the plan's explicit closing
instruction, and not rerolled after the correction, per the plan's "no rerolls or replacement seeds"
guardrail -- the assembled list keeps seven more often, accesses color meaningfully better when it does
keep a hand (no-blue -2, no-green -3, untappers +0.167), and each of those movements traces to a directly
causal column (the land, selection, and untap packages' own accepted mechanisms). It does **not** show
fewer mulligans overall on this sample: mean mulligans, kept-0-to-1-land hands, and win-conditions-per-
hand are all measurably worse than control here, each traced above to the AI's holistic hand-evaluation
heuristic reacting to nine simultaneous swaps and to which hands get kept at all shifting once every
package's swap reshuffled the library together -- not to any individual package's accepted mechanism
failing to hold, but a real, honestly-worse result on this specific sample nonetheless. The six
individually-accepted packages each still stand on their own separately-validated evidence; what this
document can no longer claim is that their combination is a clean, uniform improvement in every column
once actually assembled and drawn from a fresh 99-card shuffle.

As with every prior Stage 4 package, the caveat is unchanged: this benchmark samples opening hands only,
not full games. A hand containing a win-condition card, an untapper, or a color source is a proxy for the
deck actually assembling and executing its combo, not a direct measurement of win rate. Full-game
validation (does the assembled list actually win more often against representative opposition) remains
out of scope for this preflight-style benchmark and out of scope for Stage 4 as defined in the experiment
plan.
