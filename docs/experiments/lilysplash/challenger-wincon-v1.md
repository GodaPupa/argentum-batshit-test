# Lilysplash Mentor Win-Condition Challenger v1

Stage 4 item 5 from `experiment-plan.md`: "cleaner win-condition density," tested alone per the plan's
one-variable-at-a-time rule. No land, selection spell, Aura, or stack-protection card is touched -- only
one redundant blink instant is swapped for one countermagic-adjacent mill creature.

## Provenance

- Control: `submitted-v0.1.txt`, SHA-256 `f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525`
- Challenger: `challenger-wincon-v1.txt`, SHA-256
  `b53be3c86fbd1c2a793790fd7d408dd73c09584a860982df277fac17d79df836`
- Guardrails enforced in code (`checkDeckGuardrails` in `LilysplashOpeningHandBenchmark.kt`): both
  decks are 99 library cards plus the Lilysplash Mentor commander, and neither has a repeated
  nonbasic land or spell.

## Scoping this package

The submitted list has exactly one card that converts the mana/untap engine into an actual win: Sage's
Row Denizen ("Whenever another blue creature you control enters, target player mills two cards"). Stage
2 documents the only proven line that uses it: Ghostly Flicker (or another double-creature blink) loops
Peregrine Drake and Archaeomancer/Mnemonic Wall for mana-positive recursion, and Sage's Row Denizen mills
two per loop iteration until the opponent decks out. That is a real, deterministic win, but it depends on
the *entire* recursion chain being assembled -- Sage's Row Denizen alone does nothing until Peregrine
Drake, a blink spell, and a recursion creature are all online together.

The submitted list also carries three separate "exile then return your own permanents" instants:
Ghostly Flicker (`{2}{U}`, exactly two artifacts/creatures/lands), Displace (`{2}{U}`, up to two
creatures), and Essence Flux (`{U}`, one creature, with a Spirit-only upside that is cosmetic in this deck).
Displace is a near-exact functional duplicate of Ghostly Flicker for the deck's actual combo line: the
proven loop only ever needs to blink two *creatures* (Peregrine Drake plus a recursion piece), which
Displace does exactly as well as Ghostly Flicker at the same mana cost. Essence Flux is not touched by
this package -- it only hits one creature, so it cannot alone sustain the two-creature loop, and it serves
a distinct role (instant-speed protection for a single threatened creature) that the stack-protection
package's territory already covers conceptually.

**Vedalken Entrancer** (`{3}{U}`, Creature -- Vedalken Wizard, 1/4, "{U}, {T}: Target player mills two
cards") fills the density gap directly: real, common, already implemented in this engine
(`mtg-sets/2003-2007/.../rav/cards/VedalkenEntrancer.kt`), hand-authored, no rarity ambiguity to
resolve. Unlike Sage's Row Denizen, it needs no recursion chain at all -- once infinite mana and *any one*
untap effect already in the submitted 99 (Freed from the Real, Hidden Strings) are online, repeatedly
untapping this one creature mills the opponent directly. It is a structurally simpler second win
condition, not a bigger one: fewer assembly pieces required, not a faster clock once assembled.

| Removed | Replaced with | Why |
|---|---|---|
| Displace | Vedalken Entrancer | Displace duplicates Ghostly Flicker for the deck's proven two-creature recursion loop (the loop never needs to blink a land, which is the only thing Ghostly Flicker can do that Displace can't); Ghostly Flicker stays in the list untouched, so the Stage 2 combo line is unaffected by this swap. Removing the redundant copy of an existing effect to add the deck's first non-recursion-dependent win condition is a clean single-variable swap. |

Land count, total deck size (99 + commander), and every card belonging to another Stage 4 item's
territory are unchanged from the control list, so any difference the benchmark measures is
attributable to this one swap alone.

## Method

`LilysplashOpeningHandBenchmark.kt` test `"Lilysplash wincon challenger v1 preflight"` (gated on
`-DlilysplashWinconChallenger=true`, run via `just lilysplash-wincon-challenger`) replays both decks
across the same 12 frozen seeds (`2026091401`-`2026091412`), both seats, under the commander-aware keep
rule Stage 3 settled on. It reports the same land/U/G/fixer-style columns as prior packages plus a new
`winConditions` column: hands containing a card from the win-condition suite defined above (one card in
control, two in the challenger).

## Result

CI run `c592059b47`, `Lilysplash Preflight #10`,
<https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34933472905>, 5m19s, all six jobs
(`land-challenger`, `selection-challenger`, `aura-challenger`, `untap-challenger`, `stack-challenger`,
`wincon-challenger`) green -- the table below is the deterministic output of a local run of the exact
code in this commit (fixed seeds, deterministic shuffle, no wall-clock or network input to the
benchmark), disclosed as such since GitHub now requires sign-in to view raw Action logs even on public
repos, the same practice as the five prior packages.

Commander-aware policy, same 12 seeds, both seats, 24 samples per deck.

| Metric | Control | Challenger-wincon-v1 | Delta |
|---|---:|---:|---:|
| Mean mulligans | 0.542 | 0.583 | +0.042 |
| Keep seven | 16/24 | 14/24 | -2 |
| Keep six | 3/24 | 6/24 | +3 |
| Keep five | 5/24 | 4/24 | -1 |
| Kept with 0-1 land | 2/24 | 2/24 | — |
| No direct blue source | 6/24 | 6/24 | — |
| No direct green source | 4/24 | 2/24 | -2 |
| Win conditions per hand (avg) | 0.042 | 0.125 | +0.083 |
| Hands with zero win conditions | 23/24 (95.8%) | 21/24 (87.5%) | -2 |

The mulligan/keep-count and U/G-access columns move by the same reshuffle-noise artifact documented in
every prior package -- neither Displace nor Vedalken Entrancer is a tracked color source, so neither can
causally move `hasCommanderColors`; any movement in those columns comes from the AI's generic
hand-quality heuristic reacting to which nonland spells happen to appear, not from this package's actual
variable.

The `winConditions` column is worth tracing precisely, and unlike the untap and stack packages, this
swap disturbs an unusually wide slice of the pre-shuffle array: Displace (`D...`) sits between Dawn's
Reflection and Dive Down alphabetically, while Vedalken Entrancer (`V...`) sorts between Vapor Snag and
Vizier of Tumbling Sands -- a run of 64 of the 99 library slots (every card from Dive Down through Vapor
Snag) shifts back by one index in the challenger's alphabetically-built array before the identical
`(seed, 99)`-keyed Fisher-Yates shuffle is applied. Because that shifted run spans nearly two-thirds of
the deck, none of the 24 zero-mulligan hands in this sample happens to dodge it entirely (in contrast to
the narrower swaps in the untap and stack packages, where an untouched-hand example was easy to find);
the mechanism is still directly checkable from single- and double-slot shifts within the run. Seed
`2026091401` seat 1 shows the plainest case: control's hand holds "Gift of Paradise" where the
identically-shuffled challenger hand holds "Gilded Scuttler" -- the very next name after Gift of Paradise
in the shifted run -- with every other card in the hand unchanged (both untracked, no score change).
Seed `2026091402` seat 0 shows two independent one-step shifts landing in the same hand: "Mulldrifter" to
"Myconid Spore Tender" (untracked), and "Vapor Snag" to the newly-inserted "Vedalken Entrancer" -- the
exact slot the swap created -- which is this sample's `winConditions` gain.

Checking every seed/seat pair where the `winConditions` column differs between the two decks (four of the
24, matching the +2 net shown in the table): seed `2026091402` seat 0 is a clean zero-mulligan gain
(control's hand has no win-condition piece; the identically-shuffled challenger hand swaps in Vedalken
Entrancer, confirmed above). Seed `2026091411` seat 1 is a second clean zero-mulligan gain (control's
hand has none; challenger's hand at the same seed/seat holds Vedalken Entrancer). Seed `2026091410` seat 0
is the one *loss*: control's zero-mulligan hand holds Sage's Row Denizen, but the identically-shuffled
challenger hand at that seed/seat holds no win-condition card at all -- Sage's Row Denizen shuffled out of
the seven drawn indices once the wide index shift moved other cards into its old slots. Seed `2026091404`
seat 1 gains a win-condition piece in the challenger (Sage's Row Denizen), but its mulligan count differs
from control's (0 versus 1), so it isn't a clean like-for-like comparison -- the AI's generic hand-quality
heuristic mulliganed a different number of times on that hand in each deck. Net effect on the summary
line: two hands gained one win-condition piece each cleanly (seeds `2026091402` and `2026091411`) and one
gained a piece under a mulligan-confounded comparison (seed `2026091404`), against one clean loss (seed
`2026091410`), for a net of +2 win-condition pieces across all 24 samples per deck (+0.083 average) and -2
zero-win-condition hands, matching the table above -- consistent with the shuffle mechanism and the AI's
known mulligan-heuristic variance, with no unexplained movement.

## Verdict

**Win-condition-density package v1 is accepted.** Zero-win-condition hands fell from 23/24 to 21/24, and
win conditions per hand rose from 1 in 24 samples to 3 in 24, entirely traced rather than assumed,
consistent with every prior Stage 4 package. The swap is also a genuine density improvement rather than a
faster clock: Vedalken Entrancer needs no recursion chain to close a game once infinite mana and any one
of the deck's existing untap effects are online, so it gives the deck a second, structurally independent
path to the same decking win instead of a second copy of the same combo requirement.

As with the prior four packages, the caveat is that this benchmark samples opening hands only. "A
win-condition card in the opening 7" is a proxy for the actual goal -- reliably converting an assembled
mana/untap engine into a win -- not a direct measurement of it; proving that would need a scenario test
that actually assembles infinite mana with only Vedalken Entrancer and an untapper on board (no Peregrine
Drake/Archaeomancer/Ghostly Flicker loop at all) and checks that the opponent decks out, which is out of
scope for this preflight-style benchmark. Held pending combination with the other Stage 4 packages, and
pending the same full-game validation infrastructure the prior four verdicts flagged as not yet existing.
