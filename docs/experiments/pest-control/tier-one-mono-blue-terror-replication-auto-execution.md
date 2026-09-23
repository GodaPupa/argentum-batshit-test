# Pest Control Tier-1 coverage — Mono-Blue Terror primary replication auto-execution

## Authorization

This gate authorizes exactly one automatic production execution of the already-frozen twelve-game
Mono-Blue Terror primary replication block.

Authorization:
`AUTOMATIC_ONE_SHOT_PRIMARY_REPLICATION_EXECUTION`

Trigger:
`PUSH_TO_MAIN_BY_AUTHORIZATION_RECORD_MERGE`

There is no manual workflow-dispatch surface.

## Bound accepted provenance

Harness gate:

- reviewed head: `e9c0b357ad2eca3d6d5cb99ce225f6532d640913`
- merge: `51a020b45ad278ec22883c6a6f977c77e2c32c54`
- standard CI: `35923355149`
- artifact validation: `35923355357`

Dormant production-stack gate:

- reviewed head: `728e2d5e150c527b2049e10b8bc304945e2b7d99`
- merge: `b8468b5b7865db5a2138852968b9e6ef89278273`
- standard CI: `35925031349`
- dedicated validation: `35925031432`

Frozen replication artifact:

- artifact ID: `10773131628`
- archive SHA-256: `4d3a19ef7febacb336911ff1271c14ab83a6ea1f99ed34ae154ae154d6ef5f25`
- ordered vector SHA-256: `445542e6cdf9902cc435b4db276a747e6b2200ff4f24ec9ac896b517a44bd34d`
- assignments CSV SHA-256: `24c1ce43362dbb2ae61fa79926437182fb74d0b76ff28a6ec44d040c2a434b70`
- freeze manifest SHA-256: `ad10e52e6b64d85aa4d890ff772aa31a271435ed100564490f63ec9afc1d6862`

Immutable authorization-record SHA-256:
`f35b4d178a7bfa3a2e019756c616799bea2e8afcd5cd977ae208e1cdeceaed8a`

## One-shot barriers

Before any artifact download or official initializer entry, the merge-triggered production job
requires:

- main-branch push context;
- GitHub run attempt exactly `1`;
- exact authorization-record digest;
- accepted execution-stack merge as an ancestor;
- no prior push run for this production workflow;
- no prior official replication evidence artifact with the reserved name.

After those checks, the exact frozen archive is downloaded and both its outer digest and internal
checksum inventory are verified.

A durable execution envelope is then written before the production runner starts.

For every game, the runner requires this order:

1. durable attempt record;
2. durable initialization-entry record;
3. official initializer;
4. qualified production driver;
5. durable raw-game record.

Any exception terminates the block immediately. There is no reroll, replacement, continuation,
resume, salvage or seed-regeneration path.

The workflow uploads partial durable evidence on execution failure.

## Inference boundary

The twelve-game replication block is the primary inference block for Mono-Blue Terror.

The accepted four-game smoke remains separate and is not pooled into the primary conclusion.
Pooling may be shown only as secondary descriptive context after the independent replication block
is accepted.

No deck changes, sideboarding or tuning are authorized by this gate.
