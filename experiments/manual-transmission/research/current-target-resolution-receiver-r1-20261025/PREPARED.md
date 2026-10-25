# Current-target resolution R1 receiving audit

Prepared for source `41dfad5616b0143df07dae6aa098539085a94a99`, validation `818ff6ef7d597b9323b028497814ca3fe378b1f0`, original run `40158813599`, job `121065805389`, attempt 1. No acceptance or runtime result is claimed. Reuses accepted R5 audit infrastructure and saved identity baselines; does not audit R5 again.

First reconcile live CURRENT.json and newer receipts/successors. On terminal fetch exact run, attempt/job, artifact, source/validation commit and compare metadata plus main ref through connected GitHub. Pin the artifact ID, size, SHA256, workflow ID and exact expected artifact name in `resolver-metadata.json`. The metadata keys are: main, run, jobs, artifact, source_commit, validation_commit, source_delta (base R5 to R1), validation_delta (R1 to validation), run_id_pin, job_id_pin, artifact_id_pin, artifact_bytes_pin, artifact_sha256_pin.

Download the original ZIP once as `original-<artifact-id>.zip`; verify byte count, provider digest, unique safe ZIP entries, CRC, complete SHA256SUMS and every extracted member under `resolver-original`. Preserve the original unchanged. Missing artifacts or raw reports are not passing evidence. For terminal failure produce a precise failed receipt, retaining the original archive and all diagnostics; do not run the success-only auditor or substitute failure for negative behavior.

For success, use `RECEIVE_RESOLVER_R1.py` with MANUAL_RECEIPT_ROOT pointing to the receipt directory, MANUAL_REPO pointing to a verified source materialization, and MANUAL_SOURCE_ROOT / MANUAL_SOURCE_OVERLAY pointing to source R1. Any existing cached files must match the source metadata and hash manifests. This checks every original raw report, counts/identities/multiplicities/skips, all eight negative controls including first assertion frames, compile tasks, exact mutation patch, restoration, required recipes, client/actor/recording regressions, ordinary build, Assay and protected files.

Before adopting even a successful auditor result, explicitly resolve the inherited protocol prose listed in PREPARED.json against the frozen executable R1 contract. Preserve the old bytes and record the determination. No source or workflow mutation during qualification, no automatic card/multiplayer/game admission. Retain all original assertions and both reserved Precision games.

The ten synthetic checks exercise the frozen negative receiver with artificial reports. They are parser tests only. They are not Kotlin compilation or executable negative-control evidence.
