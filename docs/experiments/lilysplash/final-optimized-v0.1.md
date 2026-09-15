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

CI run pending -- infrastructure commit not yet pushed.

## Verdict

Pending CI confirmation.
