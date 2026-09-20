# v0.6-A Pieces of the Puzzle — Accepted Promotion Audit

Run: 35522823492
Artifact: 10608733407
Artifact ZIP SHA256: 0f00dee31b09c16d632c6006840407769ec99c79819aa52307b84bae676cf683
Control output SHA256: 9b1c0c8140b0f2582eb9b91d459e2593fd00c2810e8e53994cecad53f9aa6bb5
Challenger output SHA256: 718913aaaee4ca77435cb9f83101faebf9945e36bc144d52734bc8a6a1e16007
Source: bed3d99c2b2db6a00f0754509f172f9956460f76
Samples: 100000 per deck
Seed: 0x1A22E7001
Horizon: T1-T10

Change:
-1 Curate
+1 Pieces of the Puzzle

Integrity:
- Workflow and repository CI: green
- Exact v0.5 control reran to its accepted challenger-output hash
- Artifact present and non-empty
- Reported artifact digest matches downloaded ZIP SHA256
- One slot changed; mana, ritual, tutor, and interaction packages unchanged
- No reroll, replacement seed, or post-outcome protocol change

Primary T10 results:
- Pair: 10.362% -> 10.689% (+0.327 pp)
- Pair + commander ready: 9.638% -> 9.946% (+0.308 pp)
- Lethal state: 7.279% -> 7.524% (+0.245 pp)
- Cumulative lethal: 7.288% -> 7.536% (+0.248 pp; +3.40% relative)

Cumulative lethal by turn:
- T3: 0.017% -> 0.020% (+0.003 pp)
- T4: 0.085% -> 0.090% (+0.005 pp)
- T5: 0.560% -> 0.578% (+0.018 pp)
- T6: 1.572% -> 1.599% (+0.027 pp)
- T7: 2.893% -> 2.970% (+0.077 pp)
- T8: 4.246% -> 4.381% (+0.135 pp)
- T9: 5.697% -> 5.909% (+0.212 pp)
- T10: 7.288% -> 7.536% (+0.248 pp)

Selection:
- T10 average cards seen per game-turn: 0.13670 -> 0.16239
- T10 average cards drawn per game-turn: 0.13524 -> 0.14350
- T10 selection-cast rate: 9.334% -> 9.454%

Guardrails at T10:
- Commander battlefield: 94.735% -> 94.735%
- U execution: 95.656% -> 95.856% (+0.200 pp)
- R execution: 84.580% -> 84.483% (-0.097 pp)
- UU execution: 43.690% -> 43.959% (+0.269 pp)
- High Tide productive: 15.368% -> 15.480% (+0.112 pp)
- Reversal positive: 10.140% -> 9.995% (-0.145 pp)

Early commander cost:
- T3 battlefield: 55.124% -> 54.884% (-0.240 pp)
- T4 battlefield: 67.622% -> 67.286% (-0.336 pp)
- T5 battlefield: 77.847% -> 77.558% (-0.289 pp)
- T6 battlefield: 84.114% -> 84.012% (-0.102 pp)
- The gap closes completely by T10.

Interpretation:
Pieces converts one additional mana into materially deeper selection and a two-card
ceiling. The accepted pair and lethal gains exceed the prior Strategic Planning
promotion, while the measured costs are small, temporary, and concentrated in early
commander deployment and the secondary Reversal threshold. The primary-combo gain
and improved ordinary blue execution justify promotion.

Disposition:
V06A_PASS
PIECES_OF_THE_PUZZLE_PROMOTED
V06_CONTROL_ACCEPTED
CURATE_REMOVED
