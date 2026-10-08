# Pest complete fixture evidence verifier v1

Authority: adoption 6052434153 of independent receipt 6052430482, PR #216.
Parent: 12c7a301c571eccff46e4064e40b0094db1e0535. Three additive files; all
accepted predecessor source, smoke, deck, vector and workflow bytes stay unchanged.

## Gap and bounded scope

The accepted producer has local checksum coverage and ordering guards. A later
consumer needs to distinguish a complete coherent fixture transcript from a
partial prefix or semantically altered records with recomputed checksums. This
read-only verifier supplies that check without replaying the producer or Git.
It does not seal, sign, authenticate, repair, resume or promote any experiment.

verify_fixture_evidence requires external exact profile/head/tree/checkout-inventory
and tracked-count pins. It reads only an existing directory of 126 regular,
single-link files. Symlinks, hardlinks, special files, root aliases, missing/extra
entries and bounded-size violations fail closed. Files are opened no-follow and
nonblocking, so a FIFO cannot stall the verifier. Single-owner local trust remains;
this is not an atomic filesystem snapshot or hostile-concurrency guarantee.

The verifier checks the canonical profile and fixed accepted submission-component
hash, fixture vector and receipt, externally pinned checkout record, all 96 ordered
events, all 24 raw token payloads, and exact twelve-slot zero-official-counter
summary. Expected raw payload bytes are computed from the fixed labels/tokens
without invoking the producer evaluator. The exact sorted manifest must cover all
125 other files, with no duplicate, omitted, unexpected or reordered lines.

The returned immutable record means only that the supplied bytes match this fixed
excluded-fixture schema and caller-provided pins. execution_authorized is always
false. Official loading always refuses. No source measurement, historical write
chronology, fsync/crash survival, remote preservation or trusted author is attested.
A fully forged coherent transcript and forged trusted pins can pass. Acceptance
must never be interpreted as proof that a game or even a fixture process ran.

## Qualification input and tests

Tests copy the original accepted submission integration evidence directory, whose
identity is fixed in the test pins. It came from bundle
Pest_Seedfree_Submission_Boundary_Evidence.zip (1391290 bytes, SHA-256
674dfc11d9a2e71332c53f47007fdfae5176415205785da6a8cfced46190809e), under
frozen-checkout-qualification/fixture-evidence. The prior run was local, not Actions.

Run once with a fresh output root:

```sh
PEST_SEALED_SOURCE_EVIDENCE=/absolute/original/fixture-evidence PEST_SEALED_TEST_OUTPUT=/absolute/new/audit-fixtures PYTHONDONTWRITEBYTECODE=1 python3 scripts/experiments/pest-control/test_monster_tron_replication_evidence.py
```

No Git fixture commits or producer submissions are needed by this suite. Positive
audit leaves evidence bytes unchanged. Negatives include stale/recomputed hashes,
raw outcome/payload mutations, event order/index/token mismatches, claimed official
counters, consumed claim, attempt two, wrong checkout/component/vector cells,
manifest duplicates/reorder, aliases/special files, oversize and duplicate JSON.
Adversarial resealing only changes excluded local copies; it is not an official
vector freeze or repair. Preserve all failures and special-file metadata. Do not
rerun an unchanged failed candidate. A separate frozen-checkout invocation audits
the original accepted evidence using the exact frozen new verifier source.

The inherited 93-test logs remain preserved accepted evidence and are not claimed
as newly rerun. Since the successor only adds a read-only consumer, qualification
focuses on its actual input boundary and the accepted artifact it consumes.

## Gates retained

All official bindings stay null. There is no entropy, seed allocation, claim API,
execution marker/ref/workflow, engine initialization, gameplay action or outcome.
The W/W/L/W 3-1 smoke is separate prior evidence, with consumed authorities. No
historical failed run is retried/replaced. Full exclusions, source/activation
authority, runtime integration, legal freshness, physical/remote durability and
fresh official execution authority remain blocking separate gates. The October 12
boundary is neither extended nor waived. Freeze this candidate for Reviewer A;
no self-review, self-adoption or activation follows passing verification.
