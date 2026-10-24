# Receive the original full activation qualification

Source: 07a99a5151dc2438a100e1d3ebb26118ebd6d3f9
Source tree: 56a235a456d88fc199761a8925d559efb8bc388d
Validation: 77310dfc6ba19133a0c280ec0e37772450428872
Validation tree: 0b2e96d5516d2efcb81ba9911e524039a9cc2788
Run: 40084539580; job: 120844646651; attempt/run_number: 1; event: push.
Expected artifact: manual-activation-target-capture-r1-40084539580.
Workflow blob: ef00ec76cead449bcc7e3a3e2ae5c2f4e216989f.

First reconcile live ledger/leases/workflows and newer receipts. Do not repeat the run. This receiver is prepared, not executed against a terminal artifact. Download the original archive, pin its metadata ID/bytes/SHA256, verify digest/CRC/safe unique members, then extract to activation-full-original beneath MANUAL_RECEIPT_ROOT. Preserve failure evidence even when success audit cannot run.

Populate activation-full-metadata.json using original GitHub responses: run; artifact; jobs (total_count and complete jobs); main (GET git/ref/heads/main); source_commit and validation_commit (GET git/commits); source_delta (compare 035e0ec3da8729c43018ffcc839aa821517dcda3...source); validation_delta (compare source...validation); artifact_id_pin, artifact_bytes_pin, artifact_sha256_pin. Do not infer identities from filenames alone.

Restore source-bound files without assuming filesystem persistence. MANUAL_REPO is an inspected source checkout containing the unchanged protected baseline and prior records. MANUAL_SOURCE_OVERLAY contains all14 changed paths from the immutable source, including four preserved preflight records. MANUAL_ACCEPTED_OVERLAY may supply accepted modal-spell035e0ec3 source/records. Compare every changed blob against GitHub's exact comparison metadata; the auditor verifies protected144 bindings,23 goldens,82 legacy fixtures and frozen deck. Fresh full source checkout can supply all files directly; do not mutate protected files.

Run RECEIVE_ACTIVATION.py with MANUAL_RECEIPT_ROOT, MANUAL_REPO, MANUAL_SOURCE_OVERLAY and MANUAL_ACCEPTED_OVERLAY set to these inspected locations. The auditor independently checks raw report identities/counts/multiplicities/skips, exact original baseline counters, full mutation patch, normal/mutant compile logs, executed locked commands, source/tree/workflow/job/attempt/artifact pins, clean restoration, Assay corpus bytes/metrics and51 unchanged findings. Required outcomes:18targeted passes;8mutant cases, exactly AC01/AC02/AC07 AssertionFailedError firstframes55/60/95 and5passes;800focused;14863broad including4088engine;18471ordinary passes+43 unchanged skips;568Assay units;2000touchstone cards;51 preserved differential findings. Compilation/missing reports/unrelated failures fail the control.

On a failed run, first preserve original archive/metadata/logs and list every raw case/error/skip and stage disposition. A failed normal test is not negative-control evidence. Never blindly rerun or weaken assertions. The success auditor intentionally rejects failed or incomplete runs; diagnose original evidence separately and publish a bounded failure receipt.

The original R1 injection failure is preserved at b2a2bfc4418781e9ac039fc89c2cc215c09abddd. R2 early-feedback receipt 96ddb8fbe1198917946e9f4b406c9fdde882a75a verifies compiled visit-only injection and8normal tests; it does not validate the10new tests or accept activation. Accepted modal-spell/modal-trigger components remain unchanged. No Spellskite/multiplayer/current-retarget/resolver/game/seed admission.

Preparation validation: Python syntax checked; expected locked recipes independently matched the frozen gate; negative gate13selfchecks and positive counts8selfchecks passed using synthetic rows explicitly labeled as static checks. No external independent review or runtime qualification is claimed by preparation.
