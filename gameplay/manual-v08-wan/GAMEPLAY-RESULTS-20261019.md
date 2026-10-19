# ARGENTUM — Wan Shi Tong v0.8 first candidate gameplay (October 19, 2026)

**VERIFIED: ONE CANDIDATE GAME COMPLETE. NO ADOPTION / NO COMPETITIVE IMPROVEMENT PROVEN.**

## Exact execution authority

- Candidate source `4f9b03f77c5ccfc98a83f7f0c0eb5d0bd6ac48dc`; Actions [39333744844](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39333744844): **SUCCESS**; one safety gate and one completed four-player Commander game.
- GitHub artifact `12225408250` (`manual-v08-wan-game-39333744844`), original ZIP SHA-256 `c30206c726a287cb86fc68277db9d88fe89b244cee9e95352e0961c75614d1c3`; bytes downloaded independently and SHA-256 verified.
- Candidate source loads `gameplay/manual-v08-wan/CHALLENGER.decklist.txt`: the single legal 100-card substitution **Wan Shi Tong, Librarian IN / Lifecrafter’s Bestiary OUT**; all four seats use the same opt-in R5 policy, with candidate profile ID `manual-v08-wan-shi-tong-opt-in`.
- Historical frozen control: R7 source `88b822070e63626ec9e73f1384ecb3252ced13fa`, success [39318496017](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39318496017), seed-1609 original ZIP artifact `12218767899`, SHA-256 `1367da258c0ebb9f216101adf3440cf748e251be64c7789fdaff80a25ab5c8eb`. Accepted R5 correction remains intact.

## Exact game outcome

| Measure | Frozen v0.7 seed `202610191609` | Wan v0.8 same seed |
|---|---:|---:|
| Status | COMPLETE | COMPLETE |
| Accepted/submitted actions | 3,099 / 3,099 | 3,762 / 3,762 |
| Engine rejections | 0 | 0 |
| Terminal global turn | 54 | 68 |
| Winner | Seat 3 (`manual-mirror-2`) | Seat 2 (`manual-mirror-1`) |
| Winner life | 6 | 40 |
| Commander casts across seats 1–4 | 1 / 0 / 1 / 1 | 1 / 1 / 1 / 1 |
| Eliminations | Three LIFE_ZERO | Three LIFE_ZERO |

Although seeds and R5 policy are matched, the deck substitutions cause different seeded library arrangements; the submitted action prefixes match exactly for only the first 1,758 submissions. This is a **single observed candidate versus control comparison**, not a randomized win-rate estimate or a clean one-decision causal fork.

## What Wan actually did

- **Only one Wan Shi Tong cast occurred:** original candidate action **3077**, turn **55**, by Seat 3 (`manual-mirror-2`), announced **X=7**.
- **Actual mana payment was nine mana**, including **three blue** (`white=2, blue=3, red=2, green=2`), with SpellCastEvent `totalManaSpent=9`. This was **not** an Animar-discounted cast: that seat's Animar (`e200`) had returned from battlefield to hand at **action 3043**, before Wan's cast. The earlier **9/9** synthetic rules tests remain the evidence for Wan's `{U}{U}` cost with enough Animar counters.
- At action **3084**, the Wan ETB placed **seven +1/+1 counters** and drew **three** cards (`Sheltered Thicket`, `Malevolent Hermit`, `Cloud of Faeries`). A separate Soul of the Harvest optional-draw decision and additional draw abilities also resolved around this window; do not misattribute them to Wan's ETB.
- Opposing Seat 2 cast `Rapid Hybridization` targeting Wan at **action 3149**; Wan moved from battlefield to graveyard at **3161** as an 8/8 Bird Spirit.
- **Zero opponent LibrarySearchedEvent instances occurred while Wan was on the battlefield (actions 3080–3161)**, so its opponent-search trigger was *not observed* in this sampled game. Separate existing card-level tests verify the trigger.
- All three eliminations were LIFE_ZERO; no failed library draws or deck-out eliminations occurred.

**Conclusion:** Wan's rules and preliminary library-safety tests passed; a real nine-mana X=7 cast produced three extra ETB cards but it was removed shortly afterward, and its controller lost the game. Keep it only as an **unaccepted opt-in challenger**. Do not call it a stronger deck or replace Lifecrafter's Bestiary in frozen v0.7 based on this result.

## Next justified work

A later predeclared comparison program may evaluate Wan across more seeds if deck-slot decision research remains valuable. It is not warranted within the current single-game authorization, and no extra games are launched here. Near-empty-library tests covered the AI's *voluntary* ETB draw choice but not every forced opponent-search trigger when a library is nearly empty. Preserve `main`, frozen v0.7, `AiProfile.CURRENT`, R5 safeguards and C4/D2 official gates.
