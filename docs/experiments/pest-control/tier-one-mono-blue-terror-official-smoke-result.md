# Pest Control Tier-1 coverage — Mono-Blue Terror official smoke result

## Disposition

Status: `ACCEPTED_NONEXPERIMENTAL_SMOKE`

This result is accepted as a clean four-game preboard smoke for the exact frozen Pest Control v1.0
versus Serpico_CC Mono-Blue Terror matchup and pilot identities. It is **not** a matchup
qualification, metagame-wide conclusion, or Tier-1 claim.

Protocol:
`PEST_CONTROL_V10_VS_SERPICO_CC_MONO_BLUE_TERROR_2026_09_20_PREBOARD_V1`

Block:
`PEST_CONTROL_V10_VS_SERPICO_CC_MONO_BLUE_TERROR_2026_09_20_PREBOARD_V1_NONEXPERIMENTAL_SMOKE_4`

Official workflow run:
`35894018961`

Execution commit:
`d85ecb9281fc2c7993d9651e72484311af89b4d9`

Execution tree:
`f37894f02aad0883afc446e0fc43efb356d5b093`

Evidence artifact:
`10766612589`

Artifact archive SHA-256:
`91c71fb9624e274e8b540e55a52be1ffe0e1deb7d334276b80993756d757b495`

Frozen vector SHA-256:
`ca508c842886fff2af7db1c966fbedbb8ae801796c5043e26b22def056c724ea`

Assignments CSV SHA-256:
`7950f90c94e8b92cd04ffcbfe31ba4682351d45b5f19b05ef6d606410bf92129`

Freeze manifest SHA-256:
`4a68f7a9bdbcbf7fd2b58caca86da6cd0c9868a2f8726eee40ee251e2758b5e3`

## Result

Pest Control finished **4-0**.

| Game | Pest seat | Starting deck | Winner | Terminal turn | Actions | Raw SHA-256 |
|---|---|---|---|---:|---:|---|
| 1 | seat zero | Pest Control | Pest Control | 15 | 373 | `0a6d5683b834ee1e837afdb0de35c57da132ff49c0eb46eddc718ef1cdcf2321` |
| 2 | seat zero | Mono-Blue Terror | Pest Control | 39 | 837 | `c5a183f68a64f0792b11fea9fa315d40d5e57702f2c4d13381b3e2a32df3c0ff` |
| 3 | seat one | Pest Control | Pest Control | 17 | 359 | `67a8e350e13f8ad13285dcd9bccba547e1b8b2665938b7d2479c46f6e97ea7a0` |
| 4 | seat one | Mono-Blue Terror | Pest Control | 16 | 398 | `89723528f421a19b185099008ee9a4910ce75c1582edcc2348f4eb4679e20dc5` |

Pest Control on the play: `2-0`.

Pest Control on the draw: `2-0`.

Pest Control from seat zero: `2-0`.

Pest Control from seat one: `2-0`.

These splits are descriptive only. Four games are too few for a reliable matchup-strength,
play/draw, or seat estimate.

## Integrity audit

The official workflow and artifact independently reconcile:

- run event: `push`
- run attempt: `1`
- execution disposition: `VALIDATED`
- attempted games: `1,2,3,4`
- initialized games: `1,2,3,4`
- recorded games: `1,2,3,4`
- coordinator transitions: exact attempt → initialization-entry → record order for every game
- rerolls: `0`
- replacements: `0`
- seed regeneration: `0`
- artifact archive digest matches GitHub's recorded digest
- summary SHA-256 matches the artifact index
- every raw-game SHA-256 matches both the durable per-game record and artifact index
- every action trace is sequential
- every recorded action is accepted with no rejection reason
- every raw game reached a terminal state

The one-shot replay barriers passed before the frozen artifact was downloaded. The production run
used the merge-triggered authorization record and required no manual workflow action.

## Interpretation

The 4-0 smoke is a strong implementation/readiness signal for this exact matchup and makes a deck
change unjustified at this stage. It is still only four observations and therefore does not qualify
Mono-Blue Terror as a favorable matchup or establish Pest Control as Tier 1.

The correct next step is replication, not tuning.

## Next justified gate

Authorize a **fresh, independently frozen 12-game Mono-Blue Terror replication block**, preboard
only, preserving the exact Pest Control v1.0 and Serpico_CC Mono-Blue Terror identities.

Design requirements:

- 12 fresh nonzero seeds;
- collision audit against all retired Pest seed identities, including these four smoke seeds;
- the prior exclusion universe therefore advances from `550` to `554` identities;
- no reuse of the four smoke seeds;
- exact 6/6 Pest play/draw balance;
- exact 6/6 Pest seat-zero/seat-one balance;
- exact 3 observations in each seat × starting-deck cell;
- freeze before any gameplay;
- one logical block with no rerolls, replacements, or outcome-conditioned continuation;
- whole-block rejection on protocol or integrity failure;
- no deck, pilot, sideboard, or protocol change;
- no postboard testing yet.

The 12-game result must remain separate from the four-game smoke for primary inference. Pooling may
be reported only as secondary descriptive context after the replication block is independently
accepted.

No replication seeds are created by this document.
