# v0.9 Phase 9 — Paired Capsize Pilot Source Freeze

Experimental source commit: `2035adc350368f8eeec87a2f960ea05c40763fb8`

Experimental source tree: `b2972f92fb1afaf994e9020362960b2c944a76c1`

Parent: `a5c7dfc6cf22341839fd27b05a696440f2ef813b`

Accepted control SHA256:
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Paired runner SHA256:
`1dce15e3555f937964ae571140ca43e763f52747af56c2be71d53a7b7bcebb36`

Frozen execution identity:

- master seed: `0x00000001A22E7010`, assigned and unconsumed;
- matched pairs: 10,000;
- trajectories: 20,000;
- horizon: T1–T10;
- summary schema: `izzet-v09-capsize-paired-v1`;
- artifact schema: `izzet-v09-capsize-paired-artifact-v1`.

The source commit contains the validated Phase-5 policy, Phase-6 activation and
telemetry, Phase-7 isolated paired RNG iterator, Phase-8 paired output contract, and
Phase-9 runner plus artifact auditor. The source tree matched the locally staged tree
before publication, and local/remote refs matched after publication.

This record is provenance only. It does not modify the frozen experimental source,
authorize a run, consume the seed, or expose outcomes. A temporary manual workflow
must check out this exact commit and pass the complete seed-free preflight before the
sole run is authorized.

Disposition: `V09_PHASE9_SOURCE_FROZEN`
