# ARGENTUM — R7 source-bound forensic triage (October 19, 2026)

## Disposition

**VERIFIED ORIGINAL TRACE OBSERVATION; STRATEGIC DEFECT NOT YET PROVEN.**

This note documents exact decisions from accepted R7 gameplay artifacts. It does not change the frozen deck, any production AI policy, `AiProfile.CURRENT`, or `main`. No new games or alternative action simulations were run for this note.

- R7 gameplay source: `88b822070e63626ec9e73f1384ecb3252ced13fa`.
- Completed workflow: [39318496017](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39318496017).
- Seed `202610191609`: artifact `12218767899`; original ZIP SHA-256 `1367da258c0ebb9f216101adf3440cf748e251be64c7789fdaff80a25ab5c8eb`. Seat 3 wins; 3,099 accepted actions, zero rejections.
- Seed `202610191610`: artifact `12219790862`; original ZIP SHA-256 `111687fab8c483c227ec22478822aa4824ccb736d031b716b9c4855f0fa58ba9`. Seat 1 wins; 3,156 accepted actions, zero rejections.
- Both downloaded original archive SHA-256 digests were checked before this transcript-only analysis.

## Ballista target allocation, seed 1609

Seat 3 (player `manual-mirror-2`) casts Walking Ballista X=15 at action **2638**, global turn 46. It enters with fifteen counters at action 2651, then activates all fifteen shots at actions **2652–2666** in a single sequence before those damage abilities resolve.

| Target | Submitted activation indices | Shots | Observed result |
| --- | --- | ---: | --- |
| Glen Elendra Archmage (`e47`) | 2652, 2655 | 2 | Initially dies, then returns via persist |
| Hope-Ender Coatl (`e33`) | 2653–2654 | 2 | Destroyed |
| Peregrine Drake (`e15`) | 2656–2658 | 3 | Destroyed |
| Opposing Animar (`e0`) | **2659–2666** | **8** | **8 damage against 10 toughness; not destroyed** |

All fifteen Ballista damage events resolved during actions **2669–2711**. The Animar-targeted damage did not cause a battlefield departure. Later in the same turn, Seat 3 submitted **no attackers** at action 2731. The eight points of marked damage thus had no observed same-turn lethal payoff.

Each Animar shot was individually selected by AI scoring above passing (approximately a +3.67 score margin per shot), despite the eight-shot total not killing its target. This is a **high-value candidate for a narrow ineffective-damage/activation-continuation policy defect**, not yet a proven counterfactual improvement: opponents, future interactions and legal alternative shot/hold actions still require actual-engine replay.

A key testable alternative is **retain Ballista counters rather than queue eight nonlethal shots** after the first seven activations; such an alternative must be simulated from the exact pre-action state at **2659** with normal opponent priority.

## Ballista negative control, seed 1610

Seat 4 (`manual-mirror-3`) casts Walking Ballista X=10 at action **2643**, global turn 52. On global turn 53, its ten activations **2763–2772** target:

- Glen Elendra Archmage (`e47`): one damage at action 2763.
- Opposing Animar (`e0`): **nine** activations at actions 2764–2772.

The nine Animar damage events resolved with target toughness **nine**. At action **2799**, Animar leaves the battlefield, and at **2800** it moves into the command zone. This is a **legitimate lethal removal control**. A future correction must preserve that play, not reflexively penalize targeting a high-toughness commander.

## Final defense, seed 1609

At action **3097**, Seat 1 blocks Fyndhorn Elves with Benevolent Geist. It was at **30 life** immediately before combat damage. At action **3099**, the engine records **80 total combat damage** to Seat 1, including **38** from the attacking Animar. The seat finishes at **-50 life**.

Even an unrealistically favorable subtraction of *all 38 Animar damage* while leaving the other resolved damage unchanged yields **42 incoming damage**, still exceeding 30 life. Therefore, simply redirecting the one recorded blocker to Animar would not, by itself, prove a survival-saving alternative. This arithmetic is **not** a substitute for full legal-blocker enumeration or complete engine replay.

**Combat-survival conclusion:** No proven correction from this specific block choice.

## Next justified experiment

A bounded, real-engine alternative-action fork at original **seed 1609 / pre-action 2659** is the strongest lead. Compare keeping remaining Ballista counters (pass/resolve normal priority) against the originally scored continuing shots, using exact state, legal actions and no forced opponent passes. Protect the real nine-shot Animar kill from seed 1610 as a negative control.

Do not claim a gameplay win-rate improvement, implement a generic shot penalty, or rerun large Commander batches based solely on these historical traces.

### Forensic CI

An isolated read-only extraction test and authenticated GitHub Actions workflow were published on `gameplay/manual-r7-forensic-20261019`. The validation result is governed by its actual run receipt, not by publication of this note.

## Verified forensic CI receipt

- Exact forensic workflow source: `287ab5498ea44560ffd96c734c8c6860a16fbbd0`.
- GitHub Actions [run 39324375067](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39324375067): **SUCCESS**, one targeted Kotlin forensic test passed.
- GitHub artifact: `12221797778` (`manual-r7-forensic-39324375067`), size **22,039 bytes**.
- Original artifact ZIP SHA-256: `a43ba8209eca2998acd9580c53aad8d9f71072b8f722324ee43da7d96cf3d7e8`, independently verified.
- `forensic-summary.json`, both expanded action/result windows and both sets of relevant AI scoring records were preserved.
- Seed 1609: 15/15 shots resolved, eight hit Animar (toughness ten) without removing it; the optimistic 38-damage-only combat subtraction still leaves 42 damage against 30 life.
- Seed 1610: 10/10 shots resolved, nine hit the nine-toughness Animar and caused its battlefield departure.
- **Formal disposition: TRACE FORENSICS COMPLETE; STRATEGIC POLICY DEFECT NOT YET PROVEN.** Targeted real-engine choice/priority fork remains the next justified investigation, not a general Ballista suppression policy.
