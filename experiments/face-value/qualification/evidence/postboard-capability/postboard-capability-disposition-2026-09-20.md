# Postboard capability disposition — 2026-09-20

## Decision

Forge profile v4 plus frozen postboard map v2 is accepted for official postboard qualification in the seven simulator-valid matchups. Mono-Blue Faeries remains `HUMAN_ONLY_REQUIRED`. The permanent Face Value Final Forge 75 is unchanged.

Map v1 is preserved but simulator-rejected because its Elves plan included Monstrous Emergence, which Forge did not cast under forced exposure. Map v2 changes only those two opponent sideboard slots to two Nylea's Disciple; no qualification outcomes existed when v2 was frozen.

## Evidence chain

| Run | Seeds | Scope | Result | Disposition |
|---|---:|---|---|---|
| 35540359835 | 730001–730028 | Map-v1 legal screen, profile v3 | 28/28 terminal, 0 flags; Relic 9 casts/41 activations; only 1 blast cast | Preserved; failed blast-exposure threshold |
| 35540990308 | 730029–730036 | Nonlegal blast forced exposure, profile v3 | REB 6 and BEB 15 casts; Pyroblast/Hydroblast 0 | Preserved; demonstrated conditional-blast AI defect |
| 35541317361 | 730037–730044 | Independent blast remediation replication, profile v4 | 8/8 terminal, 0 flags; Pyroblast 12, Hydroblast 18, REB 12, BEB 9; counter and destroy modes for both colors | Accepted capability evidence |
| 35541670012 | 730045–730058 | Legal map-v1 integration, profile v4 | 14/14 terminal, 0 flags; Pyroblast 2, Hydroblast 0 | Preserved; failed only Hydroblast-exposure threshold |
| 35542056261 | 730059–730066 | Targeted legal Hydroblast confirmation, profile v4 | 8/8 terminal, 0 flags; Hydroblast 5 with valid red-spell targets | Accepted capability evidence |
| 35542408524 | 730067–730070 | Nonlegal Monstrous Emergence forced exposure, profile v5 | 4/4 terminal, 0 flags; 0 casts | Preserved; map-v1 Elves configuration rejected |
| 35542693497 | 730071–730074 | Legal map-v2 Elves integration, profile v4 | 4/4 terminal, 0 flags; Nylea's Disciple 2 casts with correct life gain | Accepted capability evidence |

Artifact digests, respectively: `261d45848f02bb5ae7b791eb389f9e9462e8824b601789d7438a603c389027de`, `d5159902f54acebe402d7291507f68828521f43b961e369926140c4ee45bd274`, `b88b4abd658899987ca43f8bd622f1379c24225b74524614a9f58ffc9ec01301`, `b2c834d6728d70c17548bf0af4ca35f1bfa6dfc266b16e06cd3d2c8aa569fe6a`, `0167294a8d13cdfeaa55c825e6d42e72344a2572b240bba1f8a5c71b87a2ef3d`, `8067d1b443a93d712c104288e74ec2909b73316424a5da35717d19ad0a76fdc0`, and `36a04fb22919330652daa20a0c48c9e8431e44002de38f5a518e3ea7c058086d`.

## Gameplay-quality audit

- Every accepted legal diagnostic ended with exactly one terminal result and no exception, outer timeout, slow-draw termination, missing deck, unsupported operation, or contradictory bookkeeping.
- Profile-v4 blasts selected correctly colored spells or permanents. Legal traces include Pyroblast countering blue spells and Hydroblast countering Pyroblast; forced traces exercised both counter and destroy modes for all four blast names.
- Relic of Progenitus cast and activated normally. Ancient Grudge, Annul, Weather the Storm, Breath Weapon, Duress, Cast into the Fire, Extract a Confession, Gut Shot, End the Festivities, Scattershot Archer, Mwonvuli Acid-Moss, and Tamiyo's Safekeeping all received legal dynamic exposure across the preserved screens.
- Nylea's Disciple resolved its devotion trigger correctly in both exposed map-v2 games (life changes 20→26 and 2→4).
- No diagnostic outcome is authorized for matchup estimation, structural diagnosis, tuning, or challenger selection. All seeds 730001–730074 are permanently retired.

## Authorization

Fresh, non-overlapping official postboard assignments may now be frozen for the seven simulator matchups using profile v4 and map v2. Results remain sealed from tuning until automated and gameplay-quality audits complete. Faeries continues to block any full-gauntlet simulator aggregate.
