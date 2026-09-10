# Spec — archetype ranking & final deck score (`kf`, `lW`, `Im`, `Mm`/`Wm`/`Gm`/`G4`, `Mu`)

Porting contract for Layer-1 archetype ranking (`kf`, what `dW` ranks builds with) and the
0–10 final deck score (`Mm` and friends, what `vX`/`ek` report and what `dW` sorts on).
Builds on `SPEC_scoring.md` (helpers) and `SPEC_deckbuild.md` (where these are called).

Two parallel sub-score producers exist and must match each other:
- **`kf`'s inner `c(colors, arch?)`** — scores a *hypothetical* deck = all pool cards castable in
  `colors`. Used to rank archetypes before building.
- **`Im(deckCards, …)`** — scores an *actual chosen* deck's nonland cards. Used by the final score.

They compute the same six sub-scores by the same formulas; only the card set differs.

---

## 0. Set baseline `sW`

`sW(setCode)` = `sX[setCode.lower] ?? 2`. `sX` is an **empty** map in this bundle, so the baseline
**`B = 2` always**. It's the rating floor subtracted when computing quality and the unrated default.
(Keep it as a function in case a future bundle populates `sX`.)

---

## 1. Curve model `lW(buckets, fill)`

`buckets` = `Map<1..6, Int>` of nonland card counts per CMC bucket (6 = "6+"). `fill ∈ [0,1]`.
Table rows `[cmc, low, high, maxBonus, weight]`:
```
[1, 1, 3, 2, 0.5]
[2, 6, 8, 2, 0.5]
[3, 5, 7, 2, 0.5]
[4, 3, 5, 2, 0.4]
[5, 2, 3, 1, 0.4]
[6, 1, 2, 1, 0.5]
```
For each row: `lo = low*fill`, `hi = high*fill`, `count = buckets[cmc] ?? 0`, `f = maxBonus`.
If `count < lo` → `f -= (lo-count)*weight`; else if `count > hi` → `f -= (count-hi)*weight`.
Accumulate `n += max(0, f)`. Return `clamp(n, 0, 10)`.

Favors ~6–8 two/three-drops at full fill; under- or over-shooting a window costs `weight` per card.

---

## 2. The six sub-scores

For a card set `S` (nonland cards; in `kf` only those `fitsColors(colors)`), with `B = sW(set)`:
```
quality  = Σ max(0, rating(card) ?? B - B)          # raw, divided by 3 later
count    = |S|
bombs    = #{ rating ≥ 3.9 }                          # uses Wt ?? B (NOT ratingOrDefault)
removalN = #{ name.lower ∈ removal }
buckets  = histogram of min(cmc,6) for cmc∈[1,5], and 6 for cmc≥6   (cmc<1 ignored)
enablers / payoffs = archetype-tag role counts (see below)
fill        = min(count/23, 1)
curveScore  = lW(buckets, fill)
bombScore   = bombs * 1.5
synergyScore= min(enablers, payoffs)                  # rewards having both halves
expected    = 6 * fill
removalScore= fill<0.15 ? (removalN>0 ? 2 : 0)
            : removalN ≥ expected*1.15 ? 6
            : removalN ≥ expected*0.8  ? 4
            : removalN ≥ expected*0.5  ? 2 : 0         # bucketed 0/2/4/6
```

**Enabler/payoff counting differs by caller:**
- `Im` (real deck): per card, for *every* archetype tag, `role=="enabler"→enablers++`, `"payoff"→payoffs++`
  (archetype-agnostic — totals across all archetypes).
- `kf`'s `c(colors, arch)`: only counts tags whose `archetype == arch` (the specific archetype being
  scored). When `arch` is absent (raw color pair), both stay 0 → `synergyScore = 0`.

**Mana penalty `manaScore` (only in `kf`, `Im` always returns 0):** for `colors.length ≥ 3`, count pool
nonbasic lands that fix: `fix = arch[name].fixing (if any) else color_identity`; counts if
`fix.length≥4` OR `≥2` of `fix` ∈ `colorSet`. Then:
`3 colors & fixers<3 → -5`; `4 colors & fixers<6 → -10`; `≥5 colors & fixers<7 → -15`; else 0.

**Combined archetype score** (`p`): `qualityScore = quality/3`;
`score = round((quality/3 + curveScore + bombScore + synergyScore + removalScore + manaScore) * 10)/10`.
Returns `{name, score, colors, cardCount, qualityScore(rounded), curveScore, bombScore, synergyScore,
removalScore, manaScore}`.

---

## 3. `kf` — archetype ranking

`kf(pool, ratings, arch, archColors, removal, setCode="")` → list of archetype descriptors,
**sorted by `score` desc**. (Minified arg order is shuffled; bind by role.)

1. **Ten two-color pairs** `[WU,UB,BR,RG,GW,WB,UR,BG,RW,GU]` → score each with `c(pair)` (no `arch`,
   so synergy=0). Names = the pair string (e.g. `"WU"`).
2. **Good-stuff shells**: the ten three-color combos (`cW` guild names) → score each with `c(combo)`,
   sort desc, **take top 2**. Names = `"<Guild> good stuff"`.
3. **If `arch` present** (`arch.size>0`): collect every archetype name appearing on any pool card's
   tags. Score each with `c(archColorsFor(name), name)` where colors come from `archColors[name]` (else
   `C2(pool, arch, name)` — colors on ≥2 tagged cards). Return `[named archetypes…, top-2 goodstuff]`
   sorted desc. **The two-color pairs are dropped** when real archetypes exist.
4. **Else** (no arch data — the common case): return `[ten pairs…, top-2 goodstuff]` sorted desc.

`dW` then filters out `"good stuff"` names from the *ranking* (but re-adds an explicit `SX` good-stuff
build for sealed) and builds the top N.

---

## 4. Final deck score `Mm` → `Wm`

`Mm(deckCards, ratings, arch, removal, archColors="")` → `{ score, manaBaseScore }`.
`deckCards` are PoolCards (the full 23 + lands + basics).
```
o = Im(deckCards, ratings, arch, removal, archColors)     # six sub-scores over nonland cards
# removal sub-score for the FINAL score is computed differently (Gm, not the bucket ladder):
removalInDeck = nonland deck cards with name.lower ∈ removal
i = count, avgRating = Σ(rating ?? 2)/i
removalScore = Gm(i, i>0 ? avgRating : 0)
manaBaseScore = G4(deckCards.map{card}, arch)             # 0–10 playability (§6)
score = Wm(o.quality, o.curve, o.bomb, o.synergy, removalScore, manaBaseScore, o.cardCount)
```

**`Wm(quality, curve, bomb, synergy, removal, mana, cardCount)`** — normalize each sub-score to 0–10
and average:
```
l = min(cardCount/23, 1)
qN = Mu(quality, ba.quality.mean*0.7*l, ba.quality.2σ*0.5*max(l,0.3))
cN = round(clamp(curve,0,10)*10)/10                      # already 0–10, just clamp+round
bN = Mu(bomb,    ba.bomb.mean*l,        ba.bomb.2σ)
sN = Mu(synergy, ba.synergy.mean*l,     ba.synergy.2σ*max(l,0.3))
rN = round(clamp(removal,0,10)*10)/10                    # Gm output, clamp+round
mN = Mu(mana,    ba.manaBase.mean,      ba.manaBase.2σ)
return round(((qN+cN+bN+sN+rN+mN)/6)*10)/10
```

**Population stats `ba`** (baked-in mean / two-sigma):
```
quality : mean 11.54, 2σ 4.56
bomb    : mean  2.61, 2σ 4.06
synergy : mean  5.00, 2σ 4.83
manaBase: mean  5.42, 2σ 2.83
```

**`Mu(value, mean, twoSigma)`** — z-score mapped onto a 0–10 scale centered at 6.8:
```
if twoSigma == 0: return 6.8
n = 6.8 + ((value - mean) / twoSigma) * 3.1
return round(clamp(n, 0, 10) * 10) / 10
```

---

## 5. Removal sub-score for the final score `Gm`

`Gm(count, avgRating)`: `count==0 → 0`; else `round((bX(count) + _X(avgRating)) / 2 * 10)/10`.
```
bX(count):   7+→10, 6→9, 5→7, 4→5, 3→3, 2→2, 1→1, 0→0      # quantity
_X(avg): ≥3.7→10, ≥3.5→9, ≥3.3→8, ≥3.0→7, ≥2.6→6, ≥2.3→5,  # quality
         ≥2.0→4, ≥1.7→3, ≥1.5→2, ≥1.0→1, else 0
```
(Note: `Im` also produces a bucketed `removalScore`, but `Mm` **overrides** it with `Gm`. `tk`
— `SPEC_deckbuild.md` §4 — uses `Gm` too but fixes `manaBaseScore = ba.manaBase.mean` instead of `G4`.)

---

## 6. Manabase playability `G4` (seeded Monte Carlo)

`G4(cards, arch)` → 0–10. If `cards.length < 15` → 0.

**Seed**: `r = 0; for each card, for each char: r = ((r<<5) - r + charCode) | 0` (32-bit). PRNG
`rng = hX(r)` (below). Precompute `fixMap`: nonland cards with `arch[name].fixing` non-empty → their
fixing colors.

For **20** trials:
- Seeded Fisher–Yates shuffle a copy of `cards` (`j = floor(rng()*(v+1))`, swap).
- `hand = first 7`; `lands = hand.filter(isLand)`. If `lands.length < 2` → skip trial (not counted).
- Increment `validTrials`. Build available mana counts `h{W,U,B,R,G}`: for each land in hand, +1 per
  `color_identity` color; for each hand card in `fixMap`, +1 per fixing color.
- A hand is **castable** iff every nonland card in it can be paid: from a copy `R=h`, subtract its
  plain pips `{X}` (fail if insufficient); then for each hybrid group (the WUBRG sides of a `{a/b}`),
  spend 1 from the side with the most remaining (fail if that side is 0). If any card fails → hand not
  castable.
- Count `castable++` if all nonland cards payable.
- Return `validTrials==0 ? 0 : round(castable/validTrials*100)/10`  (→ 0–10).

**`hX(seed)`** (mulberry32-style PRNG, returns next double in [0,1)):
```
state = seed | 0
next():
  state = (state + 0x6D2B79F5) | 0                 # +1831565813
  t = imul(state ^ (state >>> 15), 1 | state)
  t = (t + imul(t ^ (t >>> 7), 61 | t)) ^ t
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
```
Port `imul` as 32-bit signed multiply and `>>>` as unsigned right shift; otherwise sequence diverges.

---

## 7. Porting notes

- **`bombs` uses `Wt(name) ?? B`** (B=2), **not** `ratingOrDefault` (which defaults mythics to 4).
  An unrated mythic is *not* a bomb here. Don't conflate with `jm`'s `isBomb`.
- **Curve & removal sub-scores bypass `Mu`** — they're already 0–10, just clamped+rounded. Only
  quality/bomb/synergy/mana go through the z-score normalizer.
- **`Mm` overrides `Im`'s removalScore with `Gm`** — the bucket ladder (`0/2/4/6`) is for *archetype
  ranking* (`kf`); the final deck score uses the quantity×quality `Gm`. Easy to wire wrong.
- **`G4` is deterministic** despite Monte Carlo: the PRNG is seeded from card names, so identical decks
  score identically. Replicate `hX` exactly (32-bit `imul`/`>>>`) or every manabase score drifts.
- **Two-sigma, not sigma**: `ba.*.twoSigma` is already 2σ; `Mu` divides by it directly (the `*3.1`
  maps ~2σ to ~6.2 points of spread around 6.8). Don't halve it.
- **Score scale**: archetype `kf.score` is an *unbounded* heuristic sum (used only for ranking);
  `Mm.score` is the bounded 0–10 shown in the UI and sorted on by `dW`. Different scales — keep separate.
- **Validation**: capture a finished build's `score` + `manaBaseScore` from the web app and assert the
  Kotlin `Mm` matches to 1 dp; separately assert `kf`'s ordering of archetypes matches.
