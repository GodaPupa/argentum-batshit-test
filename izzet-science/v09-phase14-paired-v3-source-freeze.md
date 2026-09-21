# v0.9 Phase 14 — Qualified Paired Pilot v3 Source Freeze

Experimental source commit: `5a981b1f4a34b065af0630bf68113f7ac73e94b9`

Experimental source tree: `777c2d4b0219871dbed04dfe4daa8a928cb0dace`

Parent: `1052806841d50bd81261f04849f8e50f326593bb`

Accepted control SHA256:
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Paired runner v3 SHA256:
`760ce035e6dc81fc001b2f080e87ebfda97be7444819a7a1e2fd7f3afb3eb5b3`

Frozen execution identity:

- master seed: `0x00000001A22E7012`, assigned and unconsumed;
- matched pairs: 10,000;
- trajectories: 20,000;
- horizon: T1–T10;
- summary schema: `izzet-v09-capsize-paired-v2`;
- artifact schema: `izzet-v09-capsize-paired-artifact-v2`.

The source contains the shared exact payment engine, deterministic reacquisition
fixture, 1,024-pair invariant qualification, repeated-event summary contract, strict
v2 artifact auditor, v3 runner, and retirement guards for both exposed runners. The
locally staged tree matched the published GitHub tree, and local/remote refs matched
after publication.

This record is provenance only. It modifies no frozen source, authorizes no run,
consumes no seed, and exposes no outcome. A temporary manual workflow must check out
this exact commit and pass the complete current preflight before the sole run begins.

Disposition: `V09_PHASE14_SOURCE_FROZEN`
