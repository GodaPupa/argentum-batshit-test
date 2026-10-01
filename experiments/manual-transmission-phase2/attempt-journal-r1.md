# Phase 2 local attempt journal and private evidence binding

Status: **IMPLEMENTED_FOR_DETERMINISTIC_RECEIVING_QUALIFICATION_ONLY**.

The frozen protocol requires a durable attempt before initialization and complete
replay/provenance. `attempt_journal.py` composes the accepted shared durability
primitives with Manual's accepted engine trace importer and 22-metric contract.
It adds no game driver, official claim, seed, pilot, allocation schedule or admission.
Manual v0.7 remains frozen; capability games remain **0/36**, primary **0/864**.

## Reuse and source provenance

The four shared source/test files are received byte-for-byte from canonical main
`9ca83110f5907e66a0c289f8205ed1e24575535c`, which accepted implementation
`2ec67cd048f89a735655f24d7fa90c81e6664785` after independent non-author source review,
39 local Python cases and broad CI. The receiving checks run all 39 cases again.
Historical Pest claims and experimental evidence are not modified or imported as
Manual gameplay evidence.

| Shared source | SHA-256 |
|---|---|
| `tools/evidence_durability.py` | `4a8cc9a00a73ca45734b58686dce72287c7c33d383e48827fe6f300df3a405c3` |
| `tools/repository_claim.py` | `80b32a6851c6212ef8584af7a525bebd0d718349cafe8bc606b53abbbc97741b` |

`repository_claim.py` is preserved as the prospective common reservation capability.
This component never calls its mutation path. Manual's canonical remote namespace,
reviewed payload, live-source checks, one-shot consumption and runtime authority
still require their own receiving integration and independent admission.

## Implemented lifecycle

An attempt is identified by protocol, pod, allocation and gear. Within one canonical
store, changing engine, input or pilot hashes cannot create another attempt for
the same member. The three declared gear members retain separate identities.

`AttemptJournal.create` exclusively creates the attempt and fsyncs its bytes and
parent directory. `initialize` writes and fsyncs an initialization-intent record
before invoking a trusted callback once. It then preserves the exact returned
private snapshot binding. Callback or write failure consumes the object and
pathname. There is no reopening, retry, overwrite or repair API. Failure records
are invalid technical attempts, never deck losses.

`finalize` accepts the original serialized engine trace bytes. It checks the trace
against the recorded initialization, imports it through `import_engine_trace.py`,
independently audits the resulting 22-metric record, and exports fixed-name files
once. The final journal binds every file digest and the actual derived outcome
classification. A final receipt binds the journal tail, immutable input bindings
and complete file set. Audit requires that receipt's externally retained digest;
truncation, extra journal records, edited files and mismatched initialization fail.

The private trace is a base64 byte envelope inside canonical JSON. Its byte count
and raw SHA-256 are checked. This preserves original JSON order, whitespace and
Unicode. Rewriting the trace as canonical JSON would reorder Kotlin's initial
state map and could invalidate its engine-serialization hash. Decoding the
envelope's `payload` reproduces the original private replay bytes exactly.

The layer boundary is internal evidence only: engine/SDK gameplay semantics,
server masking, legal actions and player UI are unchanged. Neither private
snapshot nor trace is a pilot-information surface.

## Qualification and remaining limits

The receiving workflow preserves the ten real Kotlin adapter/replay cases and
17 contract plus seven importer checks. It adds the 39 shared integrity cases
and 17 Manual journal cases, including two-process claim competition, durable
intent ordering, exceptions, changed caller objects, member identity, partial
write refusal, artifact tampering and preservation of original replay bytes.
The journal's actual-engine bridge uses the same emitted Kotlin fixture trace.
It binds those real trace bytes to the local journal; it does **not** claim that
the Python callback invoked the Kotlin initializer in that fixture.

An ordinary Python-only run without `MT_P2_ENGINE_TRACE_OUTPUT` explicitly skips
the real trace bridge. Full receiving qualification supplies the actual artifact
and requires that case to run. Initial local checks ran all 17 journal cases and
all seven importer cases against the previously accepted excluded Kotlin trace;
that reuse does not replace a fresh combined-source workflow or independent review.

Local exclusivity cannot prevent a new checkout from inventing a different root
directory. A future official runner must compose the globally unique repository
claim with an immutable canonical store, exact authorized source/inputs/pilots,
durable external tail checkpoints, per-action crash evidence and the actual
initializer/adapter lifecycle. Deterministic engine replay and all frozen card,
pilot, multiplayer, post-block priority, telemetry and capability admission gates
remain necessary. This component always reports `execution_allowed: false` and
`engine_replay_required: true`; it cannot accept itself for gameplay.
