# Pest Control Tier-1 coverage — Mono-Blue Terror primary replication result

## Disposition

Status: `ACCEPTED_PRIMARY_REPLICATION`

The exact frozen twelve-game preboard replication block completed once and is accepted as clean
primary matchup evidence for Pest Control v1.0 versus the accepted Serpico_CC Mono-Blue Terror list.

This acceptance qualifies this exact preboard matchup as favorable evidence inside the Pest Control
Tier-1 qualification program. It does **not** by itself establish that Pest Control is Tier 1 across
the Pauper metagame, and it does not authorize a deck change.

Protocol:
`PEST_CONTROL_V10_VS_SERPICO_CC_MONO_BLUE_TERROR_2026_09_20_PREBOARD_V1`

Block:
`PEST_CONTROL_V10_VS_SERPICO_CC_MONO_BLUE_TERROR_2026_09_20_PREBOARD_V1_REPLICATION_12`

## Frozen identities

Pest Control v1.0 main SHA-256:
`7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`

Qualified Pest V2 runner:
`9829ee98869343cd48dceaa9a27c56ed27c6b3bc`

Mono-Blue Terror main SHA-256:
`6c678f94112c56b0856c1fe4c008f77e7d0897bdeb290f3e2a0ec9d034147c62`

Mono-Blue Terror sideboard SHA-256:
`af4296c5e6af4be3b05b96d0267bd04da12188126e774ef8e63bb16c00bc908c`

Mono-Blue Terror 75 SHA-256:
`ae25ede2663cbc2fa41b4413381485df962b272791e1b3f49e2d69c45054e7d9`

Frozen replication artifact:
`10773131628`

Frozen archive SHA-256:
`4d3a19ef7febacb336911ff1271c14ab83a6ea1f99ed34ae154ae154d6ef5f25`

Ordered vector SHA-256:
`445542e6cdf9902cc435b4db276a747e6b2200ff4f24ec9ac896b517a44bd34d`

Assignments CSV SHA-256:
`24c1ce43362dbb2ae61fa79926437182fb74d0b76ff28a6ec44d040c2a434b70`

Freeze manifest SHA-256:
`ad10e52e6b64d85aa4d890ff772aa31a271435ed100564490f63ec9afc1d6862`

## Production execution

Authorization merge / execution commit:
`73b98896eeb4f47c4ed3ec97a10a9a3aeb5dc62e`

Execution tree:
`83438c52d4783557c7e5d38c73e277ae8aa9c77b`

Official workflow run:
`35927279604`

Run attempt:
`1`

Official evidence artifact:
`10780871469`

Official artifact archive SHA-256:
`f873c808fa12c9591e2ee9657a5e048cbf3862f3bcd63fa5b0f162740809cebb`

Summary SHA-256:
`2329ba3c87e6810e802631edb9dd6178d644afc1ef911a2115f1ed538cb8313b`

## Primary result

Pest Control finished **11-1**.

| Game | Pest seat | Pest play/draw | Winner | Terminal turn | Actions | Raw SHA-256 |
|---|---|---|---|---:|---:|---|
| 1 | seat zero | play | Pest Control | 15 | 350 | `fc09fd728c55edc5d7261cfde64a68850efcb72f4e4fed7675eb8aeeac09c738` |
| 2 | seat zero | draw | Pest Control | 32 | 825 | `a2fcde92916aca94c575da884316b02d8edd655b5f39d87474f8fc87d7e44c09` |
| 3 | seat one | play | Pest Control | 15 | 368 | `06fbbb6cb8791e299753e40fdc4774d4fe5eb1130055fcda4104dfcb55ea20ba` |
| 4 | seat one | draw | Pest Control | 24 | 548 | `21de585bdb7511ab54bf43dd014ff1e6945a81b144d34ddb0914d5cd9fb8c382` |
| 5 | seat zero | play | Pest Control | 15 | 369 | `f818ed46ce213b77c63e4fda1447704513735f591179cfa0ee4aad5546cb8cc6` |
| 6 | seat zero | draw | Pest Control | 24 | 500 | `ea0d9a3c9151a1535cb2a5bb86ead2d22105ca198476ba45a483d7eddc899639` |
| 7 | seat one | play | Pest Control | 15 | 351 | `2d87d2506d9d8a2ed97ed257ce7326bb56ba85e2f441b279a26be4404584dba2` |
| 8 | seat one | draw | Pest Control | 14 | 330 | `89970613f2d53a557c85d759e813f3c0a26bc2c88fe34075fdf98be18fb2c613` |
| 9 | seat zero | play | Pest Control | 17 | 384 | `adb31b34e5937dd889b9eead8d1965fc26740e39c137bcdb22567fe32572b609` |
| 10 | seat zero | draw | Mono-Blue Terror | 21 | 524 | `10ad36175aef49326ff964b63edb82008d05ea4f14d4f1f08005cd4d0b76832d` |
| 11 | seat one | play | Pest Control | 13 | 299 | `83ab4c2f873e59139ffb62df4de246b7b17b27d301836b562cc3b177a8d02f7a` |
| 12 | seat one | draw | Pest Control | 24 | 574 | `9372e1f7b06ed4853ff76b10f59e3457de06120939aecd759a46e7b32e8efb26` |

Primary observed win rate: **91.7% (11/12)**.

Descriptive 95% Wilson interval: approximately **64.6%–98.5%**. The interval is intentionally
reported to show the uncertainty remaining in a twelve-game sample rather than to imply a precise
long-run matchup percentage.

Play/draw split:

- Pest on the play: **6-0**
- Pest on the draw: **5-1**

Seat split:

- Pest seat zero: **5-1**
- Pest seat one: **6-0**

Joint frozen cells:

- seat zero / Pest starts: **3-0**
- seat zero / Terror starts: **2-1**
- seat one / Pest starts: **3-0**
- seat one / Terror starts: **3-0**

The single loss was Game 10: Pest seat zero, Pest on the draw, terminal turn 21.

## Integrity audit

The official production run and downloaded evidence independently reconcile:

- workflow event: merge-triggered `push` to `main`;
- workflow run attempt: `1`;
- execution disposition: `VALIDATED`;
- exact Games 1–12 attempted once;
- exact Games 1–12 initialized once;
- exact Games 1–12 recorded once;
- exact attempt → initialization-entry → durable-record order for every game;
- no missing or duplicate game numbers;
- no rejection marker;
- rerolls: `0`;
- replacements: `0`;
- seed regeneration: `0`;
- official artifact digest matches GitHub's recorded digest;
- frozen input archive digest matches the sealed freeze digest;
- every production assignment matches the frozen CSV identity, seat, starting deck and seed exactly;
- every raw SHA-256 matches both its durable record and the artifact index;
- the summary SHA-256 matches the artifact index;
- every action trace is sequential from 1 without gaps;
- every submitted action is accepted with no rejection reason;
- all twelve raw games reached a terminal state.

No game was excluded, replaced, salvaged or rerun.

## Inference

The twelve-game replication is the primary inference block. It stands on its own and is **not**
pooled with the earlier 4-0 smoke for the primary conclusion.

The primary evidence is strong enough to treat the exact Serpico_CC Mono-Blue Terror preboard
matchup as favorable for the frozen Pest Control v1.0 control under the qualified production pilot.
The result also makes a card change unjustified at this stage.

The earlier four-game smoke remains separate. A 15-1 pooled number may be mentioned only as
secondary descriptive context and must not replace the independent 11-1 primary result.

## Next justified gate

Retire the one-shot production trigger and preserve this block as immutable accepted evidence.

Then continue Tier-1 qualification with another independently frozen representative preboard
matchup. Do not tune the deck from this result, do not regenerate these seeds, and do not begin
postboard Mono-Blue Terror testing until a separate gate explicitly authorizes it.
