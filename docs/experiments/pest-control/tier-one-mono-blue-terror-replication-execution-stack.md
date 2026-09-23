# Pest Control Tier-1 coverage — Mono-Blue Terror replication execution stack

## Scope

This gate builds the operational plumbing required for the already-frozen twelve-game primary
Mono-Blue Terror replication block while keeping official gameplay disabled.

It follows merged harness gate PR #122 and binds the exact reviewed harness provenance:

- reviewed PR head: `e9c0b357ad2eca3d6d5cb99ce225f6532d640913`
- harness merge: `51a020b45ad278ec22883c6a6f977c77e2c32c54`
- standard CI run: `35923355149`
- dedicated artifact validation run: `35923355357`

## Frozen replication artifact

- artifact ID: `10773131628`
- archive SHA-256: `4d3a19ef7febacb336911ff1271c14ab83a6ea1f99ed34ae154ae154d6ef5f25`
- ordered vector SHA-256: `445542e6cdf9902cc435b4db276a747e6b2200ff4f24ec9ac896b517a44bd34d`
- assignments CSV SHA-256: `24c1ce43362dbb2ae61fa79926437182fb74d0b76ff28a6ec44d040c2a434b70`
- freeze manifest SHA-256: `ad10e52e6b64d85aa4d890ff772aa31a271435ed100564490f63ec9afc1d6862`
- qualified Pest V2 runner: `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`

## Added execution-stack safeguards

The replication path receives its own twelve-game components rather than weakening or repurposing
the accepted four-game smoke barriers:

- replication-specific authorization boundary;
- replication-specific initializer bound to the twelve-game hashes and exact assignment cells;
- write-once durable attempt / initialization-entry / result journal for Games 1–12;
- replication evidence-index reconciliation contract;
- dormant production runner using the already-qualified Mono-Blue Terror production driver.

The durable journal requires the exact frozen vector hash and exact assignment order. It has no
reroll, replacement, resume, overwrite or regeneration path.

## Current authorization state

Status: `REPLICATION_EXECUTION_STACK_VALIDATED_NOT_AUTHORIZED`

Official replication execution remains disabled. The initializer requires a later explicit
authorization state before it can create a game environment, and the dormant production runner is
not reachable from any push or workflow-dispatch trigger in this gate.

The validate-only workflow re-downloads artifact `10773131628`, verifies the pinned outer and
internal hashes, runs the non-gameplay composed checks, and re-runs the read-only official artifact
loader validation.

Official replication counters remain:

- games initialized: `0/12`;
- actions submitted: `0`;
- outcomes exposed: `0/12`.

## Next gate

Only after this execution stack is reviewed, fully green and merged should a separate PR authorize
one automatic merge-triggered production attempt. That later gate must preserve:

- exact artifact binding;
- durable attempt evidence before initialization;
- exact Games 1–12 assignment order;
- one production attempt only;
- no manual dispatch;
- no reruns, replacements, salvage or seed regeneration;
- partial durable evidence upload on failure;
- whole-block disposition according to the frozen replication protocol.
