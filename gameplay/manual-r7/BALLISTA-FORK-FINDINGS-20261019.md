# ARGENTUM — R7 Ballista counter preservation fork (October 19, 2026)

## Source-bound status

**VERIFIED: REPLAY/FORK DIAGNOSTIC PASSED. BEHAVIORAL POLICY CORRECTION NOT YET PROVEN OR IMPLEMENTED.**

- Accepted R7 gameplay source: `88b822070e63626ec9e73f1384ecb3252ced13fa`, successful two-game run [39318496017](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39318496017).
- Accepted R7 forensic report source: `15e3c010b3b19f17f1e8dfa294edcdd3f0f27a8a`, forensic CI [39324375067](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39324375067).
- Original exploratory failed replay source: `2aa20df2a8a6239e39ef607c49d0701cc06490de`, failed run `39327940602`; artifact `12223467364` (ZIP SHA-256 `b5ec45dc381f12b785fe22a4a67e083ec2ce390ff2715985d7d62ddabbd8188a`). Historical-action binding at index 183 was overly strict about generic enumeration, not a demonstrated gameplay engine fault. Failed receipt preserved.
- **Validated fork/test source**: `1fef06639cf0f45c02453f9fac1a11533f00583f`; GitHub Actions [39328726693](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39328726693), SUCCESS. Artifact `12224335627`, ZIP SHA-256 `cf638cfef6e88e63094c126ed8dc1dc5ad84bc200a071ddd22b5f0dc058e769b`, independently SHA-verified against downloaded original ZIP.
- Original R7 seed-1609 artifact `12218767899`, SHA-256 `1367da258c0ebb9f216101adf3440cf748e251be64c7789fdaff80a25ab5c8eb`. Original seed-1610 artifact `12219790862`, SHA-256 `111687fab8c483c227ec22478822aa4824ccb736d031b716b9c4855f0fa58ba9`.

## Actual pre-action decision and controlled continuations

The exact seed-1609 historical prefix replayed `2658/2658` accepted actions to **before action 2659**, turn 46. Seat 3 controlled Walking Ballista with **eight +1/+1 counters**, while eight abilities were already on the stack. Original action 2659 and following seven shot decisions targeted opposing Animar (`e0`, ten toughness), failed to kill it, and exhausted Ballista.

| Measurement | Original first shot, ordinary fresh R5 AI | Pass/retain Animar-target counters, controlled |
| --- | --- | --- |
| Accepted continuation actions | 180 | 153 |
| Controlled own priority decisions | 0 | **15** |
| Original controller memory restored | No | No |
| Ballista activations after divergence | 8 | 8 |
| Ballista damage events targeting Animar | 8 | **0** |
| Opposing Animar on battlefield at stop | Yes | No (its controller was eliminated) |
| Stop | Resource bound, turn 49, no terminal | **Game over, Seat 3 wins turn 48** |
| Seat 3 life | 34 | 28 |

In the controlled continuation, the held counters were later spent against eight other small creatures: Cloud of Faeries, Aether Channeler, Malevolent Hermit, Eternal Witness, Elvish Visionary, Fblthp, Imperial Recruiter and Glen Elendra Archmage. This removed several potential blockers or board resources. Seat 1 was eliminated at fork action **80**, global turn **46**, from real AI-controlled combat; Seat 2 was eliminated at fork action **153**, global turn **48**, also by combat. The controlled experiment does not represent an observed policy-selected alternate history: its **15 forced passes of only Seat 3's Animar-targeting shots** are explicit interventions; all opponent choices were actual R5 AI responses. No opponent pass was forced. Winning on this fork establishes an actionable candidate, not a general win-rate conclusion.

## Mandatory opposite control

Seed 1610 historical actions replayed exactly through action **2800** from the state before **2764**, including **nine** real Ballista shots at a nine-toughness Animar. Real engine `CreatureDestroyedEvent` at action `2799` and commander replacement decision at `2800` moved the opponent's Animar to its command zone. This is a legitimate removal line. Any proposed policy must **retain this nine-shot kill**.

## Interpretation and next gated action

**Evidence supports a specific tactical target-allocation failure hypothesis**, not a blanket rule saying nonlethal damage is always wasteful: the controlled fork also diverged in subsequent AI behavior and did not restore original controller memory. Before a production correction, design a public-state-only witness discriminating between removing low-toughness blockers with available Ballista counters and ineffectual chip damage to Animar. Reproduce turn-46 positive and turn-53 lethal negative controls using exact rules-engine decisions and ensure no hidden draws, future actions, arbitrary turn identifiers, or forced opponent cooperation enter policy scoring. Then implement the smallest isolated opt-in advisor or pilot wrapper if both regressions pass. Only after integration may one bounded four-player Commander game run.

All R5 Pact/payment, R4 Ballista/combo protections, frozen Manual Transmission v0.7, `AiProfile.CURRENT`, `main`, and official C4/D2 boundaries remain unchanged. **No new full Commander game and no production Kotlin modification** occurred in this fork experiment.
