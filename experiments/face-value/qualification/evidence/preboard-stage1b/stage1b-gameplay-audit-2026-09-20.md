# Face Value preboard Stage 1B gameplay audit — 2026-09-20

## Disposition

All seven simulator matchup blocks are accepted as preboard qualification evidence. The audit found no material rules, runtime, terminal-bookkeeping, target-selection, sequencing, deck-identity, or opponent-pilot defect.

This acceptance does **not** include Mono-Blue Faeries, which remains `HUMAN_ONLY_REQUIRED`, and it does not establish Tier 1 status. No full-gauntlet simulator aggregate is permitted.

The frozen **Face Value — Temur Chrysalis E — Final Forge 75** remains byte-identical and permanent. No challenger is authorized by Stage 1B.

## Provenance

- Workflow run: `35537193920`
- Execution head: `a8a6724a94faf2ea1d0119831afee267f27a3713`
- Evidence preservation commit: `8724e60b50`
- Artifact: `10613739438`
- Artifact digest: `sha256:0087b04e248d7999001ccab3c7c389b56f4f67b7b911af630fb4f5625a61d715`
- Summary digest: `sha256:80b54f4f9706315d755096b3a955ffda6c371ca3e002aaa659b987920feb80e2`
- Raw-log archive digest: `sha256:229a840d4e17272e41bf6c106a6fe98a46b2208418cf25cadd4a0d03a4f96f27`
- Seed-registry digest: `sha256:c2932d41cd15668360882e5f3c04a841ddd1c801b1f58ac712dd90060118bd43`
- Forge profile: `FACE_VALUE_QUALIFICATION_FORGE_PROFILE_V2`
- Games: 224/224 terminal; 0 automated flags

Every Stage 1B seed is permanently consumed. None may be replayed, rerolled, reassigned, rehabilitated, or used for a challenger.

## Gameplay-quality review

The audit covered all records mechanically and reviewed the longest game in every matchup, key-card traces, interaction targets, emerge payments, and the remediated opponent decision paths.

- No exception, outer timeout, slow-match draw, missing deck, unsupported operation, missing mulligan, multiple winner, or missing terminal result occurred.
- Mono-Red Rally produced four natural same-turn Rally/Bushwhacker sequences with zero order violations.
- Mono-Red Madness cast Faithless Looting 51 times and Highway Robbery 42 times; Fireblast and madness payoffs also appeared naturally.
- Grixis Affinity cast Reckoner's Bargain 47 times and used Krark-Clan Shaman 50/16 cast/activation times. Bargain payments were expendable artifacts: 20 Ichor Wellsprings, 10 Blood tokens, 4 Clues, and 13 artifact lands; no creature was sacrificed to the forced pilot rule.
- Jund Wildfire produced 42 Shaman casts and 16 activations.
- Elves produced 41 Quirion Ranger casts, 149 Ranger activations, and 27 Winding Way casts without runaway or nonterminal behavior.
- Mono-Blue Terror used Lórien Revealed 21 times after the documented runtime Unicode normalization.
- Monster Tron cast Prophetic Prism 24 times.
- Face Value cast every maindeck spell naturally. Its four unambiguous Snap self-targets—Eyeless Watcher, two Eldrazi Repurposers, and Nest Invader—were all responses that rescued the creature from targeted removal and caused the opposing spell to fizzle.
- Emerge chains used Spawn/Scion and disposable bodies predominantly. Higher-value sacrifices occurred as legal postcombat conversion chains that generated cast triggers, cards, or tokens; sampled traces did not show a material execution defect.

## Accepted preboard results

Intervals are two-sided 95% Wilson intervals. Seat 1 is Face Value on the play; seat 2 is Face Value on the draw.

| Matchup | Overall | Win rate | 95% interval | Play | Draw | Disposition |
|---|---:|---:|---:|---:|---:|---|
| Mono-Red Madness | 15–17 | 46.9% | 30.9–63.6% | 4–12 | 11–5 | accepted; high seat variance |
| Mono-Blue Terror | 25–7 | 78.1% | 61.2–89.0% | 12–4 | 13–3 | accepted strength |
| Grixis Affinity | 13–19 | 40.6% | 25.5–57.7% | 5–11 | 8–8 | accepted weakness signal |
| Monster Tron | 20–12 | 62.5% | 45.3–77.1% | 10–6 | 10–6 | accepted strength signal |
| Jund Wildfire | 19–13 | 59.4% | 42.3–74.5% | 8–8 | 11–5 | accepted strength signal |
| Elves | 10–22 | 31.3% | 18.0–48.6% | 7–9 | 3–13 | accepted structural-warning signal |
| Mono-Red Rally | 14–18 | 43.8% | 28.2–60.7% | 7–9 | 7–9 | accepted weakness signal |

Across these seven simulator-eligible matchups only, Face Value finished 116–108 (51.8%; 95% Wilson 45.3–58.2%). This is a descriptive engineering summary, not a metagame-weighted estimate and not a full-gauntlet Tier 1 result.

## Research gate

Stage 1B identifies Elves as the clearest preboard structural warning, with Grixis Affinity and Mono-Red Rally as secondary weakness signals. These exposed results may not be used to alter the frozen 75 directly.

The next authorized work is to construct matchup-theory-based postboard maps from the already frozen 15, validate postboard simulator capability, and run fresh non-overlapping postboard blocks. A challenger may be opened only if accepted postboard evidence and an independently predeclared confirmation establish a persistent structural deficit.
