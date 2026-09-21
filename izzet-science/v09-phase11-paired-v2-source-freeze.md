# v0.9 Phase 11 — Recovered Paired Pilot Source Freeze

Experimental source commit: `cd93b7e427b4e8c1b67cbf23560770d2ad19b5bc`

Experimental source tree: `d5b4ce36672181cdcb26b34644ca9dd56862ad4e`

Parent: `e4ac66407d3ae78e43d7fc0342d257b7d2d8eeac`

Accepted control SHA256:
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Recovered paired runner SHA256:
`2db5bdc6351836ffa3f5c76c98f9966b0a516fbeafc796eb9a2ae85f70932bbe`

Frozen execution identity:

- master seed: `0x00000001A22E7011`, assigned and unconsumed;
- matched pairs: 10,000;
- trajectories: 20,000;
- horizon: T1–T10;
- summary schema: `izzet-v09-capsize-paired-v1`;
- artifact schema: `izzet-v09-capsize-paired-artifact-v1`.

The source commit contains the Phase-10 shared exact activation engine, its three
selector→executor recovery fixtures, the unconditional retirement guard for the
failed runner, the isolated paired iterator, strict paired summary contract, new
runner, and artifact auditor. The locally staged tree matched the published GitHub
tree, and local/remote refs matched after publication.

This record is provenance only. It modifies no frozen source, authorizes no run,
consumes no seed, and exposes no outcome. A temporary manual workflow must check out
this exact commit, verify the accepted control and runner hashes, and pass the full
current seed-free preflight before the sole recovered run may begin.

Disposition: `V09_PHASE11_SOURCE_FROZEN`
