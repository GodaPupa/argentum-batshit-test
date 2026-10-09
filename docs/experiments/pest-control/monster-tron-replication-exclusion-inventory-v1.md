# Excluded offline exclusion-inventory correspondence v1

Authority: PR216 decision6074041245. Exact base71fddde1cc8db2e9189793663dd4b62ede7124f6.
Exactly three additions; no accepted source, test, workflow or policy bytes changed.

## Contract and limits

The pure Python API accepts canonical catalogue bytes, an externally supplied catalogue
SHA-256, explicit source-ID/byte pairs and CHECK_EXCLUDED_INVENTORY_ONLY.
No files, network, Git, registry, entropy, engine or official loader are accessed.
Only bounded EXCLUDED_ labels are accepted; no numeric/official seed interface exists.
Catalogue/source/member/row schemas are exact; unknown authority fields are refused.
Catalogue official_bindings must be null. Each source raw digest and each canonical
member digest is bound; every designated source/member must be present exactly once.

Sources contain named members of identity/status/reservation rows. Allowed statuses:
RETIRED, RESERVED, QUARANTINED, ACCEPTED, REJECTED, FIXTURE. These are excluded fixture
labels, not operations on real inventory. Reservation must be an excluded label only
for RESERVED; otherwise null. Two different reservation labels, or RESERVED mixed
with nonreserved status for one identity, fail closed. This is a conservative static
snapshot contract; it does not resolve historical transitions or choose latest status.

Duplicate source IDs, member IDs and identical rows within a source are errors,
including repeated identical rows across members. Identical rows across sources are
legitimate and retained with every source/member reference. Different nonreservation
statuses also retain their full provenance. Provenance means recorded correspondence,
not authenticated origin. Sorted unique identity union and sorted provenance are
deterministic; source-pair transport order changes no output. Reordering document
contents changes raw pins as it must; semantic union/provenance remain sorted.

Output is immutable canonical JSON bytes, not a credential. All official bindings
remain null and live_inventory_complete, draw_eligible, provenance_authenticated,
historical_execution_proven and execution_authorized remain false. A caller can omit
a real source from its own coherent catalogue and still pass correspondence. A positive
limitation test demonstrates this. No claim of actual live exclusion completeness,
honest acquisition or historical execution is made;574 remains only a historical floor.
Bounded input sizes do not establish hostile-process security. Caller pins are trusted
comparison inputs. No automatic discovery, signing, external enrollment or policy change.

## Focused qualification

From checkout root, with fresh outside-checkout output directory:

```sh
PEST_INVENTORY_TEST_OUTPUT=/absolute/new/excluded-cases PYTHONDONTWRITEBYTECODE=1 python3 scripts/experiments/pest-control/test_monster_tron_replication_exclusion_inventory.py
```

24 tests use deterministic excluded in-memory documents only. Each actual API invocation
is preserved before execution, then augmented with output/refusal, including subcases,
so restored fixture state does not replace intermediate evidence. No accepted bank,
producer, fixture engine or official loader is invoked. Tests cover ordering/union,
many-source provenance, reservations, coverage/digest/schema failures, duplicate rows,
authority escalation and coherent omission. One original qualification is authorized:
preserve any failure and stop for classification; no automatic repair/rerun/replacement.

## Named requirement and next gates

This slice supports gate M's catalogue-checking mechanism only. Even a passing suite
does not close actual complete historical exclusion acquisition/reconciliation or
machinery acceptance. Later gates still require separately authorized one-draw
quarantine/vector audit/allocation; exact runtime/source/deck/pilot/profile/budget
and failure bindings; publication/ref; distinct dispatch/create-only claim authority;
complete original results and independent adoption. Preserve all failures, the separate
W/W/L/W3-1 smoke and consumed authorities. Owner-trust/shared-control limits and unknown
access remain unchanged. October12 freshness is not extended.

Freeze exact source and evidence for independent review; disclose material participation
in this actual target. WORK A cannot self-review or adopt. No official entropy, seeds,
vector, allocation, claim, marker, execution ref, dispatch, initialization, gameplay,
outcomes, retries or reruns are authorized by this component.
