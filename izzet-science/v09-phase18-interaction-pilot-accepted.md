# v0.9 Phase 18 — Paired Capsize Interaction Pilot Accepted

Disposition: `V09_PHASE18_INTERACTION_PILOT_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Frozen identities

- Experimental source commit: `9bc8b75cffc1fc8698bf135819c0465a0641bfbd`
- Experimental source tree: `9947c4b0bac55b7628ba8fa2d3bdedc34a84c461`
- Workflow runner commit: `6c1975435a17b8a74e9a4636a9d6e31a563331d6`
- Workflow runner tree: `796f81620aaa1f17111c5a3723ba33c079979a20`
- Temporary workflow SHA256: `ba4cf8979f55fd9e94c84e3efa206b9a807b715913e39bd6f473bf1068a0856b`
- Executed runner SHA256: `0c8b902dd014c6b7c92392dc59c19b64932fa49a276938a90cea0393fcd793aa`
- Master seed: `0x00000001A22E7013`, consumed and retired
- Matched pairs: 10,000; trajectories: 20,000; horizon: T1–T10

## GitHub execution and artifact

- Workflow run: `35556914552`, attempt 1 — success
- Job: `106202189793` — success
- Artifact: `10621021913`, `izzet-v09-phase18-interaction-pilot`
- Artifact size: 3,974 bytes
- Artifact ZIP SHA256: `014956c4cb53706eb22d8fe8281d0ea490c0169e23e605687243987410978542`
- Summary SHA256: `650d3b71f1d94ed72be9fde447e8f044714f17907e569c648c4e5cfa296d19fe`
- Audit transcript SHA256: `686db333b302dfae49b9bb146adcc5f3dfbe96ceeb6616fc652ad789f12cdde6`
- Manifest SHA256: `c733cd83f3025362f839542f5681ee9d91ed49b2ce0b06ec3456e9655d26a4e4`

The exact checkout, six bound file hashes, complete seed-free preflight, official
execution, strict summary audit, manifest construction, strict four-file artifact
audit, and upload all passed. Independent download reproduced GitHub's ZIP digest,
found exactly four expected files, and passed the frozen artifact auditor.

## T10 fixed-event results

| Estimand | Control | Accepted policy | Delta |
|---|---:|---:|---:|
| Capsize in residual response window | 16.50% | 41.97% | +25.47 pp |
| One-shot hostile-permanent response | 15.55% | 39.50% | +23.95 pp |
| Buyback-retained hostile-permanent response | 13.64% | 34.19% | +20.55 pp |
| Izzet Guildmage self-rescue | 14.98% | 37.47% | +22.49 pp |
| Opposing commander to hand | 15.55% | 39.50% | +23.95 pp |
| Opposing commander to command zone | 15.55% | 39.50% | +23.95 pp |
| Primary combo lethal now | 7.20% | 7.07% | -0.13 pp |
| Primary lethal by now | 7.20% | 7.09% | -0.11 pp |
| Commander on battlefield | 94.76% | 94.69% | -0.07 pp |

At T10 the policy had executed 2,877 Capsize tutor events: 1,528 through Merchant
Scroll and 1,349 through Drift of Phantasms. Early tutor spending reduced T3
commander presence from 54.66% to 52.80% (-1.86 points); the gap narrowed to -0.63
at T5, -0.35 at T7, and -0.07 at T10. Primary-lethal differences remained within
0.13 points at T10.

## Interpretation and decision

Accept the artifact and fixed-event readiness evidence. The accepted tutor policy
materially increases the modeled availability of one-shot interaction, retained
buyback, and Guildmage self-rescue in the residual post-policy response window,
while the modeled T10 primary-lethal and commander-presence guardrails remain nearly
unchanged.

These results do not measure how often an opponent presents a target, whether the
bounce creates useful tempo, whether Capsize survives opposing interaction, or any
game/match win rate. The identical opposing-commander branches establish cast
readiness only and imply no commander-tax advantage.

The Phase-14 tutor behavior policy remains accepted for future opponent-aware work;
no new behavior or card is promoted. v0.7 remains the exact card control. Retire
seed `0x00000001A22E7013` and the executed runner permanently; do not rerun, confirm,
replace, or pool this pilot. Remove the temporary workflow and restore ordinary CI.

The next justified gate is seed-free opponent-event and response-policy semantics,
not another goldfish or fixed-event pilot.
