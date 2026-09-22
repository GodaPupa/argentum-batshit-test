# Pest Control Tier-1 coverage — Grixis SALVAGE_CONTINUATION_11 result

## Disposition

Status: `ACCEPTED_SALVAGE_CONTINUATION_11`

Official continuation workflow run: `35670905674`

Execution commit: `9e23b9cff24752f3278d8826ca2dfd4aba4ac6a6`

Evidence artifact: `10673238745`

Artifact archive SHA-256:
`7d2ba6a179d474c2840365f3cc90a72387f121ba496443712829393961f89d9c`

Frozen vector SHA-256:
`5cd8a78fb62a59495d07ed31c4579fab7bafe2bc9c075aa7757a7f67953c75d4`

Assignments SHA-256:
`d85a30dcf46132609bdcd29d3a2b2e8621dba82b4e2111d95a385fb7642b4fab`

Manifest SHA-256:
`5adea1d05defd4232d5117822c5a0afdf01532d75e05f180ea488b1b83e41a65`

## Experimental boundary

The originally planned replication contained 12 frozen assignments.

Game 1 was durably attempted in terminal run `35654021529` but failed during initialization because
the reused smoke initializer required the old four-game vector identity. No terminal raw game or
matchup outcome was produced. Game 1 is permanently consumed/incomplete and was not replayed,
replaced, regenerated, or scored.

Run `35670905674` executed only untouched original Games 2-12 under the pre-registered
`SALVAGE_CONTINUATION_11` methodology.

## Result

Pest Control finished **4-7** in the eleven-game salvage continuation.

| Original game | Winner | Terminal turn |
|---:|---|---:|
| 2 | Pest Control | 16 |
| 3 | Grixis Affinity | 20 |
| 4 | Grixis Affinity | 25 |
| 5 | Pest Control | 21 |
| 6 | Pest Control | 52 |
| 7 | Grixis Affinity | 16 |
| 8 | Grixis Affinity | 15 |
| 9 | Grixis Affinity | 32 |
| 10 | Pest Control | 18 |
| 11 | Grixis Affinity | 16 |
| 12 | Grixis Affinity | 21 |

Continuation win rate: `4/11 = 36.36%`.

The planned balance was broken by the consumed Game 1. The continuation must retain its actual
denominators rather than being described as a balanced replication.

## Integrity

The accepted evidence reports:

- classification: `SALVAGE_CONTINUATION_11`;
- excluded consumed game: `1`;
- attempted games: `2,3,4,5,6,7,8,9,10,11,12`;
- recorded games: `2,3,4,5,6,7,8,9,10,11,12`;
- disposition: `VALIDATED`;
- rerolls: `0`;
- replacements: `0`;
- seed regeneration: `0`.

The workflow completed successfully and uploaded the full evidence artifact.

## Relation to the earlier smoke

The accepted four-game smoke was Pest Control 3-1. It remains a separate exploratory/readiness smoke
and is not pooled into the salvage continuation for primary inference.

Secondary descriptive context only:

- smoke: 3-1;
- salvage continuation: 4-7;
- all completed Grixis games across both blocks: 7-8 (46.67%).

The 4-7 continuation did **not** reproduce the strong 3-1 smoke result. Current Grixis evidence is
therefore mixed and does not support treating the matchup as established favorable coverage.

## Research consequence

This result closes the Grixis replication/salvage gate. No further Grixis seed generation,
continuation, replay, or outcome-conditioned extension is authorized from this block.

The Tier-1 coverage program should preserve this as a non-confirmatory Grixis result and move to the
next pre-specified metagame coverage question rather than repeatedly sampling Grixis until a preferred
record appears.
