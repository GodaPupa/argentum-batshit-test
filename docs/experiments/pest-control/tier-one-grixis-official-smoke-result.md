# Pest Control Tier-1 coverage — Grixis Affinity official smoke result

## Disposition

Status: `ACCEPTED_NONEXPERIMENTAL_SMOKE`

This result is accepted as a clean four-game preboard smoke for the exact frozen matchup and pilot
identities. It is **not** a matchup qualification, metagame-wide conclusion, or Tier-1 claim.

Protocol:
`PEST_CONTROL_V10_VS_PASQUALE_GRIXIS_AFFINITY_2026_09_07_PREBOARD_V1`

Block:
`PEST_CONTROL_V10_VS_PASQUALE_GRIXIS_AFFINITY_2026_09_07_PREBOARD_V1_NONEXPERIMENTAL_SMOKE_4`

Official workflow run:
`35632206268`

Execution commit:
`d7051af65b30a6d5d2e35780e60ea5b5b4d5e055`

Execution tree:
`0a702842777aacb8de7153b5648d38fc747f909e`

Evidence artifact:
`10655603968`

Artifact archive SHA-256:
`9335e770e486d7f379fc9aa51aaa6e5857f71541ea6949bb81d90bf9fa6259ea`

Frozen vector SHA-256:
`99eb94c4ec28f073534c008b367f3384df25abebd29dde0a9574227599cb60eb`

Assignments CSV SHA-256:
`0edad899b718accf979198749452e5b750adecf6a6e53a2a4ef56f94093a5017`

Freeze manifest SHA-256:
`3d9e4d3918954220addd22db0942637fa378f743492fc30265a49860152d60c2`

## Result

Pest Control finished **3-1**.

| Game | Pest seat | Starting deck | Winner | Terminal turn | Raw SHA-256 |
|---|---|---|---|---:|---|
| 1 | seat zero | Pest Control | Pest Control | 11 | `e4ace0b5f215bce5e76f1626214bcdcba17276a994c1ce2bcc1d01da1febefe6` |
| 2 | seat zero | Grixis Affinity | Pest Control | 20 | `8ddb850b769273b92e67e40e3e74913e29288d87075da39f76cbf275787e571b` |
| 3 | seat one | Pest Control | Grixis Affinity | 22 | `5a838bde0bf5cc2f25041ae5ccc830fa81e192f3950173fbdb397f7f14b3727b` |
| 4 | seat one | Grixis Affinity | Pest Control | 20 | `fdbe85da0905be941f23be24286396aba451ec121c83611475f672ec1092af6d` |

Pest Control on the play: `1-1`.

Pest Control on the draw: `2-0`.

This split is descriptive only. Four games are too few for a reliable play/draw or matchup-strength
estimate.

## Integrity audit

The official artifact reports:

- disposition: `VALIDATED`
- attempted games: `1,2,3,4`
- recorded games: `1,2,3,4`
- rerolls: `0`
- replacements: `0`
- seed regeneration: `0`

The artifact index binds all four attempts to the exact frozen-vector prefix and reconciles all four
raw-game hashes. Each raw game reached a terminal state and the production driver accepted every
recorded action.

The five earlier execution workflow identities are not experimental attempts. They failed before the
frozen artifact was downloaded and are permanently retired as preflight-only infrastructure
incidents:

- `35611002403`
- `35614730818`
- `35619166656`
- `35621541278`
- `35625731799`

No result from those runs is pooled with this smoke.

## Next justified gate

The 3-1 smoke clears the implementation/readiness question for this exact Grixis matchup but does not
provide enough precision to qualify the matchup.

The next authorized research step is a **fresh, independently frozen 12-game Grixis replication
block**, preboard only, preserving the exact Pest Control v1.0 and Pasquale Grixis identities.

Design requirements:

- 12 fresh nonzero seeds, collision-audited against all retired Pest seed identities;
- no reuse of the four smoke seeds;
- exact 6/6 Pest play/draw balance;
- exact 6/6 Pest seat-zero/seat-one balance;
- exact 3 observations in each seat × starting-deck cell;
- freeze before any gameplay;
- one logical block with no rerolls, replacements, or outcome-conditioned continuation;
- whole-block rejection on protocol/integrity failure;
- no deck, pilot, sideboard, or protocol change;
- no postboard testing yet.

The 12-game result must remain separate from the four-game smoke for primary inference. Pooling may
be reported only as secondary descriptive context after the replication block is independently
accepted.

No replication seeds are created by this document.
