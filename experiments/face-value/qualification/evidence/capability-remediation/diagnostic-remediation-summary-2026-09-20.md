# Face Value opponent-pilot diagnostic remediation summary

Date: 2026-09-20  
Classification: `DIAGNOSTIC_ONLY_NONQUALIFICATION_EVIDENCE`

No run listed here is matchup evidence. All diagnostic seeds are permanently retired and may not be replayed, reassigned, pooled, or used for deck tuning.

| Run | Diagnostic identity | Games | Disposition | Artifact SHA-256 |
|---|---|---:|---|---|
| [35532045305](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35532045305) | Preliminary opponent-pilot screen | 32 | Fail-closed; four pilots demonstrated, four unresolved | `7d900f0f14304b7bf30a6f625e3e9c16329e603a57e67c6a8ac34fc528c9b6be` |
| [35532849360](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35532849360) | Fresh unresolved-pilot replication | 32 | Fail-closed; Rally demonstrated once; three persistent zero-use cards confirmed | `3d8002ff7d3fce07ab7dd6033a976ef9d081950ad93330aa6143023b189299b6` |
| [35533640324](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35533640324) | Engine remediation smoke attempt 1 | 18 | Rejected configuration attempt; support-card exclusions were omitted | `2e1cbfd49af27100292a21381f083a3977d754dfef9ff0b4f3e814790119ae6c` |
| [35534116175](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35534116175) | Engine remediation smoke attempt 2 | 18 | Partial; Elves passed, Bargain executed, Highway remained unresolved | `924a384629b929ae94a952d0e102e0a6d692964a73208ccc47a94d269f6b6bfe` |
| [35534532807](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35534532807) | Isolated Highway remediation | 6 | Passed diagnostic smoke | `dfb6df6e58ca620a88dbd0a704c1a250b3d7da128792d9f572ccf86cd9a0ccc1` |

## Supported remediation components

- **Mono-Red Madness:** Faithless Looting exclusion removed. Highway Robbery receives Forge's existing `PayUnlessCost` choice mode, and that mode is admitted by the generic-choice precheck. The isolated smoke observed 12 Faithless Looting casts and 10 Highway Robbery casts in six terminal, unflagged games.
- **Mono-Blue Terror:** the diagnostic deck copy uses Forge's exact canonical `Lórien Revealed` identity. The preliminary screen observed both cycling and casting.
- **Grixis Affinity / Jund Wildfire:** Krark-Clan Shaman exclusion removed. Shaman casting and activation were demonstrated independently. Reckoner's Bargain receives a narrow positive-use path; the remediation attempts observed ten Bargain casts total.
- **Monster Tron:** Prophetic Prism exclusion removed. The preliminary screen observed three casts.
- **Elves:** Quirion Ranger and Winding Way exclusions removed. Winding Way receives a library-composition card-type choice. The corrected remediation smoke observed five Ranger casts, 21 Ranger activations, and three Winding Way casts.
- **Mono-Red Rally:** one fresh diagnostic joint Rally/Bushwhacker turn used the correct order, but the rejected Stage 1 block contains a material wrong-order trace. The combined profile must obtain multiple fresh joint exposures with zero violations.
- **Mono-Blue Faeries:** four fresh diagnostics terminated cleanly, but the rejected Stage 1 block contains a timeout with contradictory terminal bookkeeping. The combined profile must replicate clean termination over an expanded fresh diagnostic block.

## Gate consequence

Individual component smokes do not authorize replacement qualification seeds. A single frozen composite Forge profile must build and pass an integrated, fresh-seed, all-archetype capability gate. The frozen Face Value Final Forge 75 remains unchanged, and no challenger is authorized.
