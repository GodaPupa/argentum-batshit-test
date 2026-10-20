# ARGENTUM — Animar R2 Exact Shockland Decision Forensics

**Status: ALL SIX ORIGINAL DECISIONS REPLAYED · ALL ENGINE FORKS LEGAL · NO POLICY PATCH · NO FULL GAMES**

Research-only report dated October 20, 2026. This is not an official candidate promotion or qualification result.

## Exact authority

- Repository: GodaPupa/argentum-batshit-test
- Original Stage 1 four-game execution: [run 39477314282](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39477314282), source commit 84e6347bbcdfa2990eb304051771007daf849ce9.
- Original Challenger A game artifacts: seed 202610202111 artifact 12290591119, ZIP SHA-256 7744f1916818bea1fcb3149474c7f696249c0fc075fce5380d268458eca1bb2c; seed 202610202112 artifact 12290286293, ZIP SHA-256 a614eb9998b25dd34803434bb9ca80cf113b545483a185eac49cfcd419c7fd09.
- Proven primary microfork: [run 39481840733](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39481840733), source 37e5cdd9b550a3b42c1c93f5c1d73b2751955822, artifact 12291623583, ZIP SHA-256 901efcd0f81a0f98aa0fddd52b27b885108bbfe1ac99b092e28fa32b9844d91f.
- All-six archival microforks: [run 39483315564](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39483315564), source adb11d509daafd29a699d27558d2527d4621e69b, artifact 12292402933, ZIP SHA-256 cf60d970a24ff9aefda61cf27b19c5631974f01ba67bd8353c2c02eba5b975e9.
- Original input snapshot SHA-256: seed 111 = ca7032465c59f44966fef3c1e780e1861c73587e2f93f094f5f962c8d108fb89; seed 112 = cf00fb2f7c8f8cc7745639a081e403c9fc988d0cbb09d017a4130a92696a8adf.
- Frozen v0.7 Git blob c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89 unchanged. AiProfile.CURRENT, R5 profile, production code, main and R1 original comparison unchanged.

## Replay integrity

- Seed 202610202111: exactly 599 original actions replayed and **599/599 engine event rows matched**.
- Seed 202610202112: exactly 1,002 original actions replayed and **1,002/1,002 engine event rows matched**.
- Total 1,601 original actions and six original shockland response states reproduced, with **zero ability-ID rebindings** and no engine rejection.
- Every replayed R5 policy response declined the two-life shockland payment, consistent with the original.
- For each archived YesNoDecision, the same authoritative ActionProcessor accepted both No and Yes on the exact saved state: No retained original life and entered tapped, Yes charged two life and entered untapped. The original No-branch engine event list matched exactly.

## Six actual decisions and immediate castability

| Seed | Action | Global turn | Seat | Shockland | Life before | Newly affordable hand spells after paying two life | Classification |
|---|---:|---:|---:|---|---:|---|---|
| 202610202111 | 468 | 14 | 2 | Steam Vents | 40 | None | Insufficient evidence of suboptimality |
| 202610202111 | 555 | 16 | 4 | Steam Vents | 40 | Up the Beanstalk | Potential missed tempo; not proven better |
| 202610202111 | 599 | 17 | 1 | Stomping Ground | 40 | Fyndhorn Elves; Utopia Sprawl | Potential missed tempo; not proven better |
| 202610202112 | 14 | 1 | 1 | Breeding Pool | 40 | Arbor Elf | Exact turn-one acceleration line proven legal |
| 202610202112 | 273 | 9 | 1 | Stomping Ground | 40 | Chaos Warp | Additional instant affordable; use may not be appropriate then |
| 202610202112 | 1002 | 26 | 2 | Breeding Pool | 34 | None | Insufficient evidence of suboptimality |

**Interpretation:** Four of six No choices gave up a spell's same-turn affordability, including a one-drop creature on turn one. Two had no newly affordable hand spell. This **does not** prove all four were strategically wrong: an answer such as Chaos Warp may be best held, and the two life are real resources. The classification deliberately separates legal opportunity from strategic recommendation.

## Primary microfork fully proven

Exact original seed 202610202112 / Challenger A / Seat 1 / action 14:
- Original initial snapshot and 13 preceding actions replayed byte- and event-identically.
- Breeding Pool entity e83 entered tapped under R5's original No. Life stayed 40; Arbor Elf entity e1 could not be cast that turn.
- With the legal Yes decision, life became 38 and Breeding Pool entered untapped. The real ActionProcessor accepted the additional Arbor Elf cast, tapped Breeding Pool for green, spent one mana and put Arbor Elf on the stack.
- Neither an AI policy repair nor a full-game counterfactual has been tested as an improvement. The outcome if R5 itself continues after paying is a **separate question** and is not inferred here.

## Next decision

Before changing R5 or a frozen control, inspect the unchanged R5 AI's **actual next action and evaluation score** from both real primary-fork states. If it still chooses to pass rather than cast a now-affordable Arbor Elf, changing only the shock payment response will not realize the intended tempo. Any repair needs a separate opt-in policy lane with held-out regressions for justified declines.

Budget: original twelve-game ceiling; four Stage 1 full games consumed, **eight remain**. No new full games in R2. Do not rerun prior seeds as fresh outcome evidence. Challenger A is still INCONCLUSIVE; Challenger B remains blocked on Fierce Guardianship, and C is not silently substituted. No adoption or promotion.
