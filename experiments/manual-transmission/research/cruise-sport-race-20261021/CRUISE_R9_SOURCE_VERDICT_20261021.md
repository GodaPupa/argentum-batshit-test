# ARGENTUM — Manual Transmission CRUISE R9 source-only qualification

**Disposition: `QUALIFIED_SOURCE_ONLY_OPT_IN` · `NO_DECK_OR_PROFILE_PROMOTION` · `NO_NEW_FULL_GAMES`.**

Date: 2026-10-21. This record is a retrospective, deliberately narrow initial-state policy qualification, **not** a competitive Commander matchup/win-rate verdict or evidence that opponents would pass.

## Exact independent GREEN evidence

- Original, immutable source profile: `ManualTransmissionR5Candidate.profile` / `manual-transmission-r5-survival-opt-in`.
- Candidate: `ManualTransmissionCruiseR9Candidate.profile` / `manual-transmission-cruise-r9-executable-fetch-opt-in`.
- Qualified commit: `240fc8e56c2de383a625bfe8706ad8290f95028c` on `candidate/manual-cruise-r9-independent-qualification-20261021`.
- GitHub Actions [run 39655213014](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39655213014): **SUCCESS**.
- Preserved artifact: `manual-cruise-r9-source-qualification-39655213014`, ID `12372301885`, ZIP SHA-256 `8ff24c6c69ea71a26f8a16841ff456de2e31e2650416125907159dcbf9d1b117`.
- Source-pinned original R6 ZIPs: CONTROL `031011134d10e3ae002ad7dc304ff7dafcc5e14f9a117c436e510dfb984349cb`, Challenger A `a4396d43fdd831f5f67f2a6b865630af9a90ff1420c2b127be6e6b2f294a6efe`.
- Retained independent JUnit (16 XML suites total): **8/8 R9 predeclared exact cases** and **68/68 protected R5/Animar/mulligan regressions**, **0 failures, 0 errors, 0 skips**.

## Actual bounded decision

Original historical state: R6 seed `202610202141`, Seat `manual-mirror-1`, **41 exact prior original actions, events and life changes replayed per arm**, then action 42 is decided independently.

| Arm | Actual original R5 first land | R9 first land | Legality |
|---|---|---|---|
| Challenger A | Opal Palace | **Misty Rainforest** | Accepted by the actual ActionProcessor |
| Frozen CONTROL | Opal Palace | Opal Palace | Accepted, unchanged |

The source-bound candidate's diagnostic land score increased **2.950 → 3.075** (+0.125) **only** after conditional real-engine proof: play Misty, pay/sacrifice for fetch, offer a genuine legal basic Forest, enter it untapped, and submit a payable Birds of Paradise spell. Evolving Wilds' Forest enters tapped and earns **no** same-turn Birds credit.

The required source-defined negative controls held:
- No actual mana dork in hand → no override.
- No actual legal matching green-mana search target → no override.
- Fatal fetch life/payment → no override.
- Opponent priority or wrong phase → no override.
- Entered-tapped Forest → no override.
- Immediate processor-payable Lightning Bolt lethal → original R5 and R9 both chose Bolt and the bounded quiet-state verified terminal; opponent auto-passing is **not** an independently witnessed competitive win.

## Architectural isolation

- Original `Strategist.resolveCardName` deliberately excludes `PlayLand`. A separate **opt-in-only** `CardAdvisor.evaluateLandPlay` hook now intercepts registered land names *before* that legacy early return. Without a registered land advisor, original R5 and CURRENT continue through the historical, unchanged land-score return.
- Only the R9 explicit profile appends `ManualCruiseR9ExecutableFetchAdvisorModule`, currently handling Misty Rainforest and Evolving Wilds (Evolving is a negative control). The land-specific policy is conservatively early-game-only and awards **0.125** raw evaluation points, never a win score.
- The fetch's resolution in policy scoring assumes explicit *all-pass* opponent priority as a **conditional rules-mechanism probe**. It does **not** forecast real opponent responses, future hands or future turns, and cannot itself establish that Birds survives.
- Original `AiProfile.CURRENT`, accepted R5 profile source, `EngineAiPlayerController`, `ManaSolver`, CONTROL v0.7 deck, Challenger A deck, and `main` remain unchanged. The implementation and test branch are isolated; **nothing merged**.
- Precision full-game budget: **10/12 used, two reserved; R9 added 0**.

## Decision and next scientific gate

**Retain R9 as a qualified opt-in research candidate, not an adopted competitive profile.** R8's existing generic `sequenceLandsByUsableMana` flag remained HOLD; the specifically proven fetch-to-ramp policy meets its own bounded source objective.

The next useful work is an independent (non-duplicate) heldout state screen for fetch failures and opponent response timing plus a small performance-cost audit. Then run an attribution/composition review. Any new full four-player games against live/competitive opponent models require a distinct approval and budget allocation; do not spend the two reserved Precision games implicitly.

**This is a verified source-level decision improvement, not a validated increase in Manual Transmission's win rate.**
