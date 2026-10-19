# ARGENTUM — R7 Ballista opt-in causal game comparison (October 19, 2026)

## Final disposition

**VERIFIED COMPLETE: 20/20 targeted regressions, 64/64 accepted-action integration prefix, and one complete four-player Commander game with zero rejections. OPT-IN CANDIDATE ONLY; NOT QUALIFIED FOR FROZEN DECK / CURRENT / OFFICIAL GATES.**

- Accepted R7 v0.7 reference: `88b822070e63626ec9e73f1384ecb3252ced13fa`, run [39318496017](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39318496017), seed `202610191609`, original gameplay artifact `12218767899`, original ZIP SHA-256 `1367da258c0ebb9f216101adf3440cf748e251be64c7789fdaff80a25ab5c8eb`.
- Exact R7 tactical policy source `a4a4234533c78c3be628fd1e22f0fd7c54f54f8a`, passed 20/20 targeted tests in run [39337184560](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39337184560), original artifact `12228205338`, ZIP SHA-256 `7d4ddf910017ded7fff8b8e13c4db30f38f931c98990e3a7b259901a7df804b4`.
- Exact tested integration harness/workflow source: `cb8a1d9bae794f14a3a442023bc7168023647c6c`, isolated branch `gameplay/manual-r7-ballista-integration-20261019`.
- Successful Actions run [39338118746](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39338118746), artifact `12228570402` (`manual-r7-allocation-integration-39338118746`), original ZIP SHA-256 `5333cf5193b1004d3ce201a7f56e62f6a0ff1ad30d88cac01a5d9ef9b5acf1ba`; downloaded bytes independently hash verified.
- Before gameplay the runner authenticated-read the completed exact 20/20 targeted run and checked pinned source blobs of the v0.7 deck, `AiProfile.CURRENT`, R5 profile, original R4 Ballista advisor and the passing isolated R7 candidate.

## Gated game results

| Check | Frozen v0.7 + opt-in R5 | Frozen v0.7 + opt-in R7 Ballista allocation |
| --- | ---: | ---: |
| Seed | 202610191609 | 202610191609 |
| Status | COMPLETE | COMPLETE |
| Winning seat | Seat 3 (`manual-mirror-2`) | **Seat 3** (`manual-mirror-2`) |
| Terminal global turn | 54 | **48** |
| Accepted / submitted actions | 3,099 / 3,099 | **2,811 / 2,811** |
| Engine rejections | 0 | **0** |
| Winner final life | 6 | **28** |
| Elimination causes | Three LIFE_ZERO | Three LIFE_ZERO |

The R7 candidate's additional **64-action prefix smoke** also passed before this full game: status `PREFIX_COMPLETE`, 64/64 accepted actions, zero errors; it did not start a second full game.

## Exact one-action causal boundary

Both original accepted R7 and the isolated candidate have the **same initial GameState serialization bytes**, SHA-256 `3a8bf9f72110058abde5769b29273951b5a0cb9ee66b86362b887b3af39d8316`.

The **first 5,316 JSONL entries** match **exactly as parsed records** across complete submitted action and authoritative result-event streams: **2,658 identical accepted engine submissions and outcomes**.

**First difference at action 2659, turn 46:**

- **Original R5:** Seat 3 activates `Walking Ballista` (`e242`) targeting opposing `Animar, Soul of Elements` (`e0`), when remaining counters cannot deal lethal damage to that ten-toughness creature.
- **R7 candidate:** Same exact player and same pre-action state submits `PassPriority`, conserving the eight remaining Ballista counters. No opponent was forced to pass, and all four AI pilots thereafter followed their actual engine choices under the declared opt-in profile.

This strict common prefix, including the same RNG state and engine result events, supports a causal comparison at this single recorded decision; it does **not** establish population-level competitive strength.

## What the counters actually accomplished

After the pass and resolution of previously queued damage, the candidate's Seat 3 reallocated seven Ballista shots on turn 46 toward opposing **one-toughness creatures**, with real `DamageDealtEvent` and `CreatureDestroyedEvent` receipts:

| Activation | Target | Destruction result |
| ---: | --- | --- |
| 2668 | Cloud of Faeries | Destroyed at 2671 |
| 2672 | Aether Channeler | Destroyed at 2675 |
| 2676 | Malevolent Hermit | Destroyed at 2679 |
| 2680 | Eternal Witness | Destroyed at 2683 |
| 2684 | Elvish Visionary | Destroyed at 2687 |
| 2688 | Fblthp, the Lost | Destroyed at 2691 |
| 2692 | Imperial Recruiter | Destroyed at 2695 |

The candidate did eventually fire **one** additional Ballista shot at Animar at action **2708** after the small-creature removals. It did not globally prohibit nonlethal damage. Baseline R5 instead fired **eight** successive nonlethal shots at Animar, actions 2659–2666, which failed to destroy it.

Seat 1 (`manual-mirror-0`) was eliminated at **candidate action 2738** (turn 46), compared with **baseline action 3099** (turn 54). Seat 2 then lost at candidate action 2811 (turn 48) and Seat 3 won at 28 life.

## Qualification and next justified gate

**ACCEPTED AS A SUCCESSFUL SINGLE-SEED, ISOLATED BEHAVIORAL CANDIDATE—NOT PROMOTED TO `main`, frozen v0.7, `AiProfile.CURRENT`, or official C4/D2.** All R5 Pact/payment and original R4 terminal combo modules remain source-bound; the combined R7 advisor delegates original Statue and Ballista terminal witnesses.

A larger competitive-strength claim requires a *separately authorized, predeclared, bounded same-seed comparison across new seeds*, with original R5 and R7 candidate kept as distinct opt-in profiles, independent artifacts, unchanged rules/decks, and negative controls. Do not launch additional games or a 40-game batch automatically.

Independent Wan Shi Tong v0.8 card-slot challenger remains separately tested and unaccepted; see `gameplay/manual-v08-wan/GAMEPLAY-RESULTS-20261019.md` on its independent branch.
