# Pest Control v1.0 — Goldfish Sample #2 Take 8 Seed Freeze

Status: executed exactly once, accepted as Sample #2 independent goldfish evidence, and permanently
retired/hard-disabled. This vector belongs only to Project Pest Control and must never be replayed,
rehabilitated, replaced, optimized against, or reused.

## Validated baseline and immutable conditions

- Sequencing/observability implementation:
  `6cbe6ea11bdc5c0f286f7763b35db45d198bc58a` (CI #240 green)
- Final validated base:
  `c6b41a560d139a1f17f8ac1a68fd75cd29bf2ae8` (CI #241 and Argentum Validation #159 green)
- Vector: `gym/src/test/resources/pest-control-v10-goldfish-sample-2-take-8-seeds.csv`
- Size: exactly 30 unique deterministic seeds
- Ordered seed-value SHA-256: `d20e57b5e588546911d6f16b63d274651038bd49b53580f27f9a208448859f2f`
- Seed CSV SHA-256: `02dca916c2d4b6d5b921517f6dadc97c5bb4b515c25daf72a0ffe9f6cc32c45f`
- Permanent-control SHA-256: `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`
- Permanent-control encoding: the frozen `PEST_CONTROL_V10` insertion order serialized as
  `<card-name>,<count>\n` in UTF-8
- Agent: the exact production candidate/expiring-condition policy at the validated base
- Mulligans: enabled, no hand smoother
- Horizon: 20 turns
- Starting player: Pest Control, fixed `startingPlayerIndex = 0`
- Deck, engine behavior, semantic focal-action correction, production-equivalent telemetry, metric
  definitions, final artifact schema, audit-completeness requirements, and acceptance criteria:
  unchanged from the validated base and accepted Sample #1 protocol

The whole-block standard remains mandatory: any clear rules/state, telemetry, observability,
sequencing, mana-provenance, or agent-policy defect rejects all 30 games. No individual game may be
excluded, repaired, rerolled, or replaced.

## Deterministic derivation

- Domain: `Project Pest Control v1.0 Goldfish Sample #2 Take 8 independent replication after final-green head c6b41a560d139a1f17f8ac1a68fd75cd29bf2ae8 2026-09-13`
- Procedure: for counters beginning at 1, compute SHA-256 of `<domain>:<counter>`, interpret the
  first eight bytes as an unsigned big-endian integer, clear the sign bit, and reject zero,
  duplicates, and every value in the repository-wide exclusion set.
- Counters examined: 1 through 30.
- Counters accepted, in order: 1 through 30. No rejected counter or replacement derivation was
  needed.

| Game | Seed |
|---:|---:|
| 1 | 718620613069169780 |
| 2 | 8289979516787564821 |
| 3 | 4998383267747964528 |
| 4 | 3519678507793106129 |
| 5 | 5632363832495154027 |
| 6 | 7056187859636035158 |
| 7 | 6816544898911003090 |
| 8 | 8027038329331529430 |
| 9 | 259285062150193925 |
| 10 | 210178771163059854 |
| 11 | 311757519596920712 |
| 12 | 1866774883132621922 |
| 13 | 5657035247406533734 |
| 14 | 5890427938127663096 |
| 15 | 3527798735013321863 |
| 16 | 6896580252279229396 |
| 17 | 6337962774376585068 |
| 18 | 3806353330022179691 |
| 19 | 1215507224669582323 |
| 20 | 490191905181765602 |
| 21 | 3526562424318806578 |
| 22 | 2372982257168396487 |
| 23 | 991834575015853282 |
| 24 | 5039019184683776502 |
| 25 | 5747373386543945717 |
| 26 | 9158510558145178212 |
| 27 | 1708264680612289308 |
| 28 | 2124598257841188765 |
| 29 | 5694119010394762622 |
| 30 | 7982717047505249434 |

## Repository-wide collision audit

Immediately before derivation, branch and pull-request refs were refreshed from `origin`. The audit
inspected all 41 available local, remote, and pull-request refs and collapsed identical tips to 25
distinct trees. Scope comprised the two available local Pest refs; every fetched `origin/*` branch,
including all Pest Control, Batshit Economics, and Project X branches; and `refs/remotes/pull/1`
through `refs/remotes/pull/12`.

Distinct audited trees:

`05f7852beb97c7ff4af113390416770ef5a232c0`, `3656e94eb5891f467bcd1e7ea887f96c8aa10acd`,
`382a3ceaff792c518b5fe29d90c61fe79c3548fc`, `3b7cdf3cc0d5c3d019ddfb3c6cf6cc781e2c9509`,
`49b131ed964c6fc9fb04b933dc8ece5fc0701c23`, `61a269e91b2c9dc8a8b2d432f4c4f6bdae245e00`,
`691d3a9d570762651ac1dab9d2fffa3f3f7cdf84`, `6e921a11eaed912f31848deed6906fdb351289db`,
`755ae70d70d1db5e1c753dbfec943b6d1871eb8a`, `7ead88370752d679567d4da565023b35f0c3dd19`,
`993efed6247f5978fd8c803a2b54cf05a1063f03`, `a335bc748f3c45d3b071ded7bc6a2b3b2548553f`,
`a43c7f6e0361b7c0ebe4542c2647bd36ba04abaa`, `a6eb1a628be895b14fe497ca1ca7dd891b1b57f1`,
`a77915dc3413b8a8459f80f7a5ea66e1213ff037`, `a8b3371eb55527eebb67a2e2aa23d26c78491833`,
`b076266708201f23e96b4a32d26a8dc79edafb06`, `b3d4271448eba67e8a625a6e457d26b2c39bc49d`,
`c5bc096f33b79b6a52d8746104835524c1177f9b`, `c6548a1c7754f80046f589e4580152cb8de7f257`,
`c99b6acaf098ef97637a5abff7bcb082247085dc`, `cde38239b49e09a89039734968060fe643678e93`,
`d61660b1cf034d77c44f670bcbfa4bce6558d8a4`, `e07a4d5ecae5aa1b97034192e7d4db7cfd1a830a`,
and `ef5a603fc769c2fd48028220f42107c0d8b83bb9`.

The audit traversed 676,917 path instances and 27,314 unique blobs. Seed-category paths were those
whose path contained `seed`, `rng`, `smoke`, `sample`, `performance`, `optimization`, `regression`,
`replay`, `goldfish`, or `experiment`: 2,858 path instances resolving to 240 unique blobs. It parsed
27,230 UTF-8 text blobs totaling 167,307,990 bytes; the complete blob population totaled 224,445,525
bytes, and 84 binary blobs were not parsed as text. It took the union of every positive `Long` in
seed-category files and every positive `Long` on category-bearing lines elsewhere.

The resulting exclusion set contained 2,063 normalized positive `Long` values. It includes all known
development, deterministic-scenario, smoke, regression, replay, performance, optimization,
replication, rejected, retired, hard-disabled, and previously frozen/unaccepted seeds visible in the
audited refs. The Take 8 vector has zero overlap with that set, including zero overlap with Take 7
ordered vector `af294f8238ba796082333994450f6f98d9a5b371e166591ba17c60de76aa7488` and every earlier Pest
vector.

## Execution prohibition

The Take 8 runner is committed with `enabled = false`. Every earlier Pest gameplay runner remains
disabled, and the focused readiness checks do not invoke Batshit Economics or Project X runners.
Take 8 ran exactly once in committed CSV order from freeze commit
`3894ed0dbcad38b61fd2ac0640823dfe3b8778e8`. All 30 games completed normally, the complete artifact
and manual strategic audit were clean, and Take 8 is accepted as Sample #2 independent goldfish
performance/engine evidence. The runner was returned to disabled immediately after execution. The
vector is permanently retired and hard-disabled; no rerun, replay, replacement, exclusion,
substitution, reorder, optimization use, or reuse is permitted.

The execution hashes, completeness findings, complete strategic audit, independent descriptive
result, and formal disposition are recorded in
`docs/experiments/pest-control/goldfish-sample-2-take-8-acceptance-audit.md`.

Sample #1 remains the sole accepted Pest Control performance/engine sample. Take 7 and every earlier
rejected vector remain permanently retired and hard-disabled. Pest Control v1.0 remains exact, and
the challenger remains audit-only and unconstructed. No seed was executed and no game ran during
this seed-generation and freeze phase.
