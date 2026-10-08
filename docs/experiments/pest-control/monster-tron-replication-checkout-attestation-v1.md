# Pest seed-free checkout attestation construction v1

Authority: PR #216 adoption 6050961663 of canonical independent review 6050952840
(10313 bytes; SHA-256 ab38e2cdff3e54ef8bc20300cd4a81a5399f56533cc4782addf6a27ded99116c).
Parent: c57aaa964fea1bfa6190eb55005fcc924c64aff6. All inherited paths are unchanged.
This three-file additive successor is subject to fresh independent review.

## What is measured

The new module reads Git locally with a fixed command list, no shell, sanitized
Git environment, replacement objects disabled, optional index writes disabled,
and lazy fetching disabled. Supplied expected HEAD and tree must be full lowercase
SHA-1 identifiers. These are comparison pins from a future independent receipt;
they are not execution authority and the module does not select its own identity.

The checkout must be its actual top-level directory without a root symlink alias.
The HEAD and tree must equal the supplied pins. Recursive tree entries and index
entries must agree exactly, with no unmerged entries or submodules. The filesystem
must contain exactly the tracked entries and their parent directories (excluding
root .git administration). Untracked and ignored files/directories are refused.
Every actual regular file or tracked symlink is read and its raw Git blob hash is
recomputed. Git executable-bit semantics are checked for regular files. SHA-256s,
byte lengths, paths, modes and blob identities form the measured inventory digest.
Assume-unchanged and skip-worktree flags do not exempt actual bytes from checking.
HEAD and index are rechecked after scanning. The returned measurement is immutable.

This is stronger than accepting a caller-supplied baseline label or checking HEAD
alone. The accepted baseline-label fixture loader remains byte-identical; the
new adapter composes that component with actual filesystem measurement.

## Fixture evidence boundary

Only VALIDATE_EXCLUDED_SEEDFREE_FIXTURE_ONLY is admitted. The evidence directory
must lie outside the measured source tree. Source attestation succeeds before
fixture evidence creation; inherited count/order/cell/baseline/claim/attempt
validation also precedes evidence creation. The adapter then uses the inherited
coordinator's exclusive durable writer to add fixture-checkout.json to its hash
inventory. A write failure closes and poisons the coordinator, retaining prefixes.

The adapter passes fixture transitions through unchanged. Before completion it
repeats checkout measurement and requires identity with the initial measurement.
A failed check poisons completion; the inherited coordinator additionally verifies
all evidence hashes, including fixture-checkout.json. No resume, overwrite or
replacement is introduced. Measurements occur at entry and completion, not at
every token transition, and do not prove continuous absence of intervening drift.

The direct inherited fixture component remains callable for its accepted tests;
this is composition, not an unbypassable sandbox. Official loading delegates to
the unchanged always-refusing official boundary. Official bindings remain null.
There is no entropy, official numeric seed allocation, official vector, official
claim creation, execution workflow, engine initialization, engine action or winner.
Local synthetic Git repositories created by tests are excluded fixtures only.

## Qualification

Run both suites once with fresh deterministic absolute output directories outside
the source tree; retain logs, exit codes and every negative fixture prefix:

```sh
PEST_SEEDFREE_TEST_OUTPUT=/absolute/new/inherited-output PYTHONDONTWRITEBYTECODE=1 python3 scripts/experiments/pest-control/test_monster_tron_replication_seedfree.py
PEST_CHECKOUT_TEST_OUTPUT=/absolute/new/checkout-output PYTHONDONTWRITEBYTECODE=1 python3 scripts/experiments/pest-control/test_monster_tron_replication_checkout.py
```

The new suite uses actual local Git commits with fixed author/date/content, not
mocked successful Git identities. It covers wrong head/tree/abbreviated pins,
tracked bytes and modes, staged drift, assume-unchanged/skip-worktree concealment,
untracked/ignored files, missing files, root aliases, symlink substitution, hostile
Git environment overrides, read failure, source drift before completion, receipt
and ACK failures, incomplete/corrupt evidence and durable-prefix/no-resume behavior.
The accepted 41-test fixture suite supplies regression coverage for its unchanged
ordering/durability negatives. A separate measurement of the frozen clean candidate
must bind its real head/tree, tracked count and inventory digest in the receipt.
No official runtime or gameplay is exercised by either suite or that measurement.

## Explicit limits and remaining gates

Single-owner, point-in-time local measurement assumes trusted Git, OS, interpreter,
module loading and out-of-band expected pins. It does not verify loaded-code memory,
Git's own binary/config/object-store integrity against a hostile owner, physical or
remote durability, or concurrent mutation between checks. SHA-1 object identities
follow the repository's existing Git format; SHA-256 inventory binds observed bytes.
Root .git administration is excluded from the content inventory. Execution context
sandboxing, remote evidence preservation, actual engine submission ordering and
always-upload behavior remain unimplemented and unqualified here.

No current rules/legal freshness or budget gate is requalified or extended. Smoke
37676510705 remains separate W/W/L/W prior evidence; all consumed authorities and
failed predecessors remain unchanged. Official adapters, exclusion inventory,
quarantine, vector freeze, claim reservation and workflow activation still require
separate reviewed/adopted successors and fresh authority. This candidate stops at
independent Reviewer A review: NO SEEDS, NO CLAIM, NO DISPATCH, NO GAMEPLAY.
