# v0.9 Phase 18 — Qualified Interaction Pilot Source Freeze

Experimental source commit: `9bc8b75cffc1fc8698bf135819c0465a0641bfbd`

Experimental source tree: `9947c4b0bac55b7628ba8fa2d3bdedc34a84c461`

Parent: `6f1eb4a260376dda57817e8c458244ffca4f9dc5`

Accepted control SHA256:
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Paired interaction runner SHA256:
`0c8b902dd014c6b7c92392dc59c19b64932fa49a276938a90cea0393fcd793aa`

Frozen execution identity:

- master seed: `0x00000001A22E7013`, assigned and unconsumed;
- matched pairs: 10,000;
- trajectories: 20,000;
- horizon: T1–T10;
- summary schema: `izzet-v09-capsize-interaction-paired-v1`;
- artifact schema: `izzet-v09-capsize-interaction-artifact-v1`.

The source contains the accepted exact-payment and tutor-policy semantics, per-pair
RNG isolation, Capsize resolution model, strict fixed-event summary, strict artifact
auditor, the successful 1,024-pair invariant qualification, the official runner,
and retirement guards for all three prior paired runners. The locally staged tree
matched the published GitHub tree, and local and remote refs matched after
publication.

This record is provenance only. It modifies no frozen source, authorizes no run,
consumes no seed, and exposes no outcome. A temporary workflow must check out this
exact commit and pass the complete current preflight before the sole run begins.

Disposition: `V09_PHASE18_SOURCE_FROZEN`
