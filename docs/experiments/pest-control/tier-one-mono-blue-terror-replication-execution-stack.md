# Pest Control Tier-1 coverage — Mono-Blue Terror replication execution stack

## Scope

The twelve-game primary Mono-Blue Terror replication stack is now fully constructed and this gate
authorizes exactly one automatic production attempt after merge. No official gameplay is permitted
during pull-request validation.

The execution stack was accepted at:

- stack PR head: `728e2d5e150c527b2049e10b8bc304945e2b7d99`
- stack merge: `b8468b5b7865db5a2138852968b9e6ef89278273`
- standard CI run: `35925031349`
- dedicated validation run: `35925031432`

The earlier harness provenance remains:

- reviewed harness head: `e9c0b357ad2eca3d6d5cb99ce225f6532d640913`
- harness merge: `51a020b45ad278ec22883c6a6f977c77e2c32c54`
- harness standard CI run: `35923355149`
- harness artifact-validation run: `35923355357`

## Frozen replication artifact

- artifact ID: `10773131628`
- archive SHA-256: `4d3a19ef7febacb336911ff1271c14ab83a6ea1f99ed34ae154ae154d6ef5f25`
- ordered vector SHA-256: `445542e6cdf9902cc435b4db276a747e6b2200ff4f24ec9ac896b517a44bd34d`
- assignments CSV SHA-256: `24c1ce43362dbb2ae61fa79926437182fb74d0b76ff28a6ec44d040c2a434b70`
- freeze manifest SHA-256: `ad10e52e6b64d85aa4d890ff772aa31a271435ed100564490f63ec9afc1d6862`
- qualified Pest V2 runner: `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`

## Execution safeguards

The replication path remains separate from the accepted four-game smoke implementation:

- replication-specific twelve-game initializer;
- exact assignment and frozen-hash binding;
- write-once durable attempt marker before initialization;
- write-once initialization-entry marker before gameplay;
- strict Games 1–12 ordering;
- exact production driver already qualified for this matchup;
- fail-closed coordinator;
- no reroll, replacement, resume, salvage or seed-regeneration path;
- partial durable evidence retained and uploaded when a production failure occurs;
- artifact reconciliation for both complete and rejected blocks.

## Authorization

Status: `PRIMARY_REPLICATION_EXECUTION_AUTHORIZED`

Authorization record:

`tier-one-mono-blue-terror-replication-auto-execution-authorization.json`

Authorization SHA-256:

`9668fde1aa109433b8b87b3677aab1f8ce4995efea98f145e7324df191085572`

The production workflow has no manual dispatch. Pull-request events validate the authorization only.
After the exact reviewed authorization PR merges to `main`, the push event may make the single
production attempt.

Before that merge-triggered run, official replication counters remain:

- games initialized: `0/12`;
- actions submitted: `0`;
- outcomes exposed: `0/12`.

## Replay barriers

The production job requires run attempt `1`, verifies the exact authorization record, verifies the
accepted execution-stack merge is an ancestor, rejects any prior push execution run for the same
workflow, and rejects any pre-existing official replication artifact.

The output artifact name is fixed:

`pest-control-tier-one-mono-blue-terror-replication-official-execution`

The whole twelve-game replication block remains the primary inference unit. The four-game smoke is
not pooled into the primary conclusion.
