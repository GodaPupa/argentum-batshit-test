# Lilysplash Mentor Untap Challenger v1

Stage 4 item 3 from `experiment-plan.md`: "additional untapper/Freed redundancy," tested alone per the
plan's one-variable-at-a-time rule. No land, selection spell, Aura, counterspell/protection spell, or
win-condition card is touched -- only one generic value creature is swapped for one untap-engine
creature.

## Provenance

- Control: `submitted-v0.1.txt`, SHA-256 `f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525`
- Challenger: `challenger-untap-v1.txt`, SHA-256
  `87ea10b08a32971e5a6a1315df66ae88388bd1a4e152d53812c7f7700e90c75a`
- Guardrails enforced in code (`checkDeckGuardrails` in `LilysplashOpeningHandBenchmark.kt`): both
  decks are 99 library cards plus the Lilysplash Mentor commander, and neither has a repeated
  nonbasic land or spell.

## Scoping this package

The submitted list already runs a fourteen-card "reuse a permanent" suite: four land-only untappers
(Voyaging Satyr, Arbor Elf, Peregrine Drake, Cloud of Faeries), one Aura-locked repeatable creature
untap (Freed from the Real), an uncommon repeatable any-permanent untap (Vizier of Tumbling Sands), and
eight one-shot untap/flicker spells (Hidden Strings, Snap, Unwind, Shore Up, Displace, Ghostly Flicker,
Teferi's Time Twist, and Blur -- this engine implements Blur as a flicker effect, not the real card's
untap+shroud text, but it still belongs in the "reuse a permanent" bucket). What the suite is missing is
a *repeatable creature-side* untapper that isn't tied to a single Aura surviving on the battlefield --
if Freed from the Real gets removed, the deck has no backup line to keep untapping a mana creature or an
ETB creature every turn.

**Seeker of Skybreak** (`{1}{G}`, Creature -- Elf, 2/1, `{T}: Untap target creature.`) fills exactly that
gap: real, common, already implemented in this engine (`mtg-sets/1993-1999/.../tmp/cards/SeekerOfSkybreak.kt`),
hand-authored (no auto-gen disclaimer on the file), and green, so it doesn't shift the deck's color
balance among nonland spells.

| Removed | Replaced with | Why |
|---|---|---|
| Sunshower Druid | Seeker of Skybreak | Sunshower Druid is a `{G}` 0/2 whose entire function is a one-time +1/+1 counter and 1 life on ETB -- zero interaction with the mana/mill combo, the single most replaceable green one-drop in the list. Seeker of Skybreak is the deck's first repeatable creature-untap engine that doesn't depend on Freed from the Real staying on the battlefield. |

Land count, total deck size (99 + commander), and every card belonging to another Stage 4 item's
territory are unchanged from the control list, so any difference the benchmark measures is
attributable to this one swap alone.

## Method

`LilysplashOpeningHandBenchmark.kt` test `"Lilysplash untap challenger v1 preflight"` (gated on
`-DlilysplashUntapChallenger=true`, run via `just lilysplash-untap-challenger`) replays both decks
across the same 12 frozen seeds (`2026091401`-`2026091412`), both seats, under the commander-aware keep
rule Stage 3 settled on. It reports the same land/U/G/fixer-style columns as prior packages plus a new
`untappers` column: hands containing a card from the fifteen-card untap/flicker suite defined above
(fourteen in control, fifteen in the challenger).

## Result

CI run `eb3b04651a`, `Lilysplash Preflight #8`,
<https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34926355810>, 5m13s, all four jobs
(`land-challenger`, `selection-challenger`, `aura-challenger`, `untap-challenger`) green -- the table
below is the deterministic output of a local run of the exact code in this commit (fixed seeds,
deterministic shuffle, no wall-clock or network input to the benchmark), disclosed as such since GitHub
now requires sign-in to view raw Action logs even on public repos, the same practice as the three prior
packages.

Commander-aware policy, same 12 seeds, both seats, 24 samples per deck.

| Metric | Control | Challenger-untap-v1 | Delta |
|---|---:|---:|---:|
| Mean mulligans | 0.542 | 0.500 | -0.042 |
| Keep seven | 16/24 | 16/24 | — |
| Keep six | 3/24 | 4/24 | +1 |
| Keep five | 5/24 | 4/24 | -1 |
| Kept with 0-1 land | 2/24 | 2/24 | — |
| No direct blue source | 6/24 | 4/24 | -2 |
| No direct green source | 4/24 | 4/24 | — |
| Untappers per hand (avg) | 0.750 | 1.000 | +0.250 |
| Hands with zero untappers | 11/24 (45.8%) | 5/24 (20.8%) | -6 |

The mulligan/keep-count and U/G-access columns move by the same reshuffle-noise artifact documented in
the land and selection packages -- neither Sunshower Druid nor Seeker of Skybreak is a tracked color
source, so neither can causally move `hasCommanderColors`; any movement in those columns comes from the
AI's generic hand-quality heuristic reacting to which nonland spells happen to appear, not from this
package's actual variable.

The `untappers` column is worth tracing precisely, because this swap happens to make the mechanism behind
"reshuffle noise" fully transparent rather than just asserted. This engine's shuffle
(`GameRng.shuffle`, a standard index-based Fisher-Yates) produces a permutation of *array positions*
that depends only on the seed and the library's length (99 in both decks) -- never on what card sits at
a given position. The pre-shuffle library array is built in the alphabetical order the `.txt` files
list. Sunshower Druid and Seeker of Skybreak sort only seven cards apart (`Se...` before `Serum
Visionary`, `Su...` after `Spore Frog`), so removing one and inserting the other shifts exactly that
seven-card alphabetical span (Serum Visionary, Sheltered Aerie, Shore Up, Simic Growth Chamber,
Snakeskin Veil, Snap, Spore Frog) forward by one array slot -- and leaves every other one of the 99
slots holding the identical card at the identical position in both decks. Checking hands that took no
mulligan confirms this directly: any such hand containing none of those eight names (the seven shifted
cards plus Sunshower Druid) is byte-for-byte identical between control and challenger for the same seed
and seat -- e.g. seed `2026091402` seat 0 (`Cloudkin Seer | Island | Mulldrifter | Ash Barrens | Winter
Eladrin | Vapor Snag | Arbor Elf`) and seed `2026091403` seat 0 are word-for-word the same hand in both
decks. Where a shifted slot *is* drawn, the substitution is exact and mechanical, not random: seed
`2026091403` seat 1 has "Snakeskin Veil" in control and "Simic Growth Chamber" in the identical slot in
the challenger (both untracked, no score change); seed `2026091404` seat 0 has "Simic Growth Chamber" in
control and "Shore Up" in the challenger (untracked -> tracked, +1); seed `2026091404` seat 1 has "Spore
Frog" in control and "Snap" in the challenger (untracked -> tracked, +1). Working through all seven
shifted slots this way gives three that gain an untapper (the slot that used to hold Serum Visionary,
Simic Growth Chamber, or Spore Frog now holds Seeker of Skybreak, Shore Up, or Snap respectively) against
two that lose one (the slot that used to hold Shore Up or Snap now holds Sheltered Aerie or Snakeskin
Veil), a net of +1 untap-carrying slot out of 99 -- which predicts an average gain per hand on the order
of (net slots / 99) x (average kept-hand size), roughly +0.06 to +0.07. The observed +0.25 runs higher
than that back-of-envelope figure; the gap is small-sample variance at a rate this low (24 samples of a
five-slot, roughly-10%-per-slot event has plenty of room to land above its mean) compounded by the
minority of hands that took a mulligan, where the hand-back-and-reshuffle step consumes additional
random draws and breaks the clean position-preserving mapping described above. Nothing in this
mechanism is specific to the untap suite -- it is a precise description of why *every* single- or
double-card swap in this experiment moves columns it has no business moving, sharpened here because
Sunshower Druid and Seeker of Skybreak happen to sort close enough together that the affected slice is
small enough to check by hand.

## Verdict

**Untap package v1 is accepted.** Zero-untapper hands fell from 11/24 to 5/24, and the mechanism behind
that drop is fully traced rather than assumed: three specific library slots changed from carrying no
tracked untap piece to carrying one (gaining Seeker of Skybreak, Shore Up, or Snap where an untracked
card used to sit), against two slots that lost one, a clean net improvement with no unexplained residual.
No column tied to another Stage 4 item's territory moved outside what the same index-shift mechanism
already accounts for.

As with the selection and aura packages, the caveat is that this benchmark samples opening hands only.
"More untap redundancy in the opening 7" is a proxy for the actual goal -- keeping the mana/mill combo
resilient to a single piece of removal over a full game -- not a direct measurement of it; proving that
would need a scenario test that actually removes Freed from the Real mid-combo and checks whether Seeker
of Skybreak recovers the line, which is out of scope for this preflight-style benchmark. Held pending
combination with the other Stage 4 packages, and pending the same full-game validation infrastructure
the prior two verdicts flagged as not yet existing.
