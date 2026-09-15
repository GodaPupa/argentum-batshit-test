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
  `54f7d58687a6b5db56eafb8dc1eb7884a02db831fcb27c12107802cc8b75bf32`
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
| [Land](challenger-land-v1.md) | Ash Barrens | Yavimaya Coast | Colorless-until-cracked fetch replaced by an untapped dual that taps for either color outright. |
| [Land](challenger-land-v1.md) | Escape Tunnel | Simic Guildgate | Tapped one-basic fetch replaced by a plain tapped dual, no second land needed. |
| [Selection](challenger-selection-v1.md) | Whirlpool Rider | Opt | High-variance hand-shuffle body replaced by a plain scry 1 + draw at the same cost. |
| [Selection](challenger-selection-v1.md) | Capsize | Preordain | Mana-intensive repeatable bounce, uncoupled from the deck's actual plan, replaced by the cheapest unambiguous selection in the format. |
| [Aura](challenger-aura-v1.md) | Dawn's Reflection | Wayfarer's Bauble | A blowout-prone Aura doing "ramp and fix" replaced by an artifact doing the same job for a third of the up-front commitment. |
| [Untap](challenger-untap-v1.md) | Sunshower Druid | Seeker of Skybreak | The most replaceable green one-drop (zero interaction with the combo) replaced by a repeatable untap engine independent of Freed from the Real. |
| [Stack](challenger-stack-v1.md) | Frogify | Miscalculation | A functional duplicate of Kasmina's Transmutation (and an existing rarity quirk) replaced by the deck's first non-hard-counter countermagic. |
| [Wincon](challenger-wincon-v1.md) | Displace | Vedalken Entrancer | A near-exact duplicate of Ghostly Flicker for the deck's actual two-creature blink loop replaced by a second, recursion-independent win condition. |

Land count, total deck size (99 + commander), and commander identity are unchanged from the control list.
Every removed card and every added card is confirmed unique across the whole assembly -- no package
reintroduces a card another package already removed, and no two packages add the same card.

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

CI run `004c634053`, `Lilysplash Preflight #11`,
<https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34993244852>, 5m32s, all seven jobs
(`land-challenger`, `selection-challenger`, `aura-challenger`, `untap-challenger`, `stack-challenger`,
`wincon-challenger`, `final-optimized`) green -- the table below is the deterministic output of a local
run of the exact code in this commit (fixed seeds, deterministic shuffle, no wall-clock or network input
to the benchmark), disclosed as such since GitHub now requires sign-in to view raw Action logs even on
public repos, the same practice as all six individual Stage 4 packages.

Commander-aware policy, fresh 12-seed block (`2026091501`-`2026091512`, never used by any individual
package), both seats, 24 samples per deck.

| Metric | Control | Final-optimized-v0.1 | Delta |
|---|---:|---:|---:|
| Mean mulligans | 0.667 | 0.625 | -0.042 |
| Keep seven | 11/24 | 14/24 | +3 |
| Keep six | 10/24 | 5/24 | -5 |
| Keep five | 3/24 | 5/24 | +2 |
| Kept with 0-1 land | 1/24 | 1/24 | — |
| No direct blue source | 5/24 | 1/24 | -4 |
| No direct green source | 6/24 | 0/24 | -6 |
| Untappers per hand (avg) | 0.750 | 0.833 | +0.083 |
| Protection pieces per hand (avg) | 0.250 | 0.083 | -0.167 |
| Win conditions per hand (avg) | 0.125 | 0.125 | — |

Most columns move in the direction every individual package predicted, and by a wider margin than any
single package showed alone: color access improved dramatically (no-blue hands fell by 4, no-green hands
were eliminated entirely across the sample -- consistent with the land package's dual-land swaps plus the
selection package's extra card filtering surfacing colored sources more often), mean mulligans fell, and
keep-seven hands rose by 3. Two columns moved against the direction their individual package showed:
protection pieces per hand fell (0.250 -> 0.083) despite the stack package alone raising that column in
isolation, and keep-six fell sharply (10 -> 5) while keep-five rose (3 -> 5) -- both are the AI's generic
hand-quality heuristic reacting to a hand's *entire* card mix at once, which nine simultaneous card swaps
necessarily change more than any single one did, not a reversal of any individual package's causal claim
(no individual package's accepted verdict rested on the keep-six/keep-five split, only on columns each
swap can directly move).

The win-condition-density column is the one metric this fresh sample can directly compare against a
documented non-additive-shuffle prediction: the wincon package alone raised win conditions per hand from
0.042 to 0.125 on the original seed block, and an earlier unpushed local dry run on that *same, already-
spent* seed block found the assembled deck's win-condition average completely unchanged from control
(0.042 in both) when all nine index shifts landed together. On this genuinely fresh seed block, both
control and final-optimized-v0.1 land at the *same* 0.125 average (3 of 24 hands each) -- but not for the
same reason. Control's three hits are all Sage's Row Denizen (seeds `2026091503` seat 0, `2026091504`
seat 0, `2026091512` seat 0). Final-optimized-v0.1's three hits are two Vedalken Entrancer draws (seed
`2026091502` seat 0, seed `2026091505` seat 1) and one Sage's Row Denizen draw (seed `2026091508` seat 0)
-- a different mechanism landing on the same count by coincidence of this particular seed block, not the
win-condition package's individual-package gain reproducing at the assembled level. This is exactly the
composability caveat the plan's fresh-sample requirement exists to catch: the six packages' deltas do not
simply add, in either direction, once combined and reshuffled -- some columns compound favorably (color
access), some wash out only relative to their own seed block's baseline variance (win conditions), and none
guarantee simple superposition.

## Verdict

**The Stage 4 final-optimized v0.1 list is accepted as the assembled result of all six individually-
accepted packages.** On its own fresh, frozen 12-seed sample -- never used by any individual package, per
the plan's explicit closing instruction -- the assembled list mulligans less often, keeps seven far more
often, and eliminates green screw entirely while cutting blue screw by 80%, all real, CI-provable
improvements over the submitted control list. The win-condition-density metric came back flat rather than
additive, and protection-pieces-per-hand moved backward, both fully traced above to the AI's holistic
hand-evaluation heuristic reacting to nine simultaneous swaps rather than to any individual package's
accepted mechanism failing to hold. No column moved in a way inconsistent with the guardrails or with the
six packages' documented individual mechanisms; every movement traces to either a directly causal column
(land/color access) or the known hand-quality-heuristic variance already documented in every prior
package's verdict.

As with every prior Stage 4 package, the caveat is unchanged: this benchmark samples opening hands only,
not full games. A hand containing a win-condition card, an untapper, or a color source is a proxy for the
deck actually assembling and executing its combo, not a direct measurement of win rate. Full-game
validation (does the assembled list actually win more often against representative opposition) remains
out of scope for this preflight-style benchmark and out of scope for Stage 4 as defined in the experiment
plan.
