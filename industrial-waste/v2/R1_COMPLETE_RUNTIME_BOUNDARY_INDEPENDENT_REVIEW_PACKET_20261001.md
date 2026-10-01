# INDUSTRIAL R1 — FOCUSED INDEPENDENT COMPLETE-RUNTIME RULE ADOPTION REVIEW PACKET

Date: 2026-10-01

## Qualifying review target

Repository: `GodaPupa/argentum-batshit-test`

Exact clean rule-repair branch:
`industrial/r1-complete-runtime-rule-repair-final-20261001`

Exact proposal repair commit:
`cf7e623d9ae2d7095820578d919c8cea64cb9596`

Exact proposal tree:
`17e980edc5c97606a30cfdcda4b68410713081ec`

Exact proposal file:
`industrial-waste/v2/R1_COMPLETE_RUNTIME_BOUNDARY_ADOPTION_PROPOSAL_20261001.json`

Exact proposal Git blob:
`5da4912ad51c478b33588eab13af5e7bd95448d5`

Immediate predecessor:
`7c1b3ff9153087aaf27ba5f249d9f9fbede2e24d`

Predecessor proposal blob:
`6dc90dd3be24c6a62596ea42ac30f140b0692847`

The predecessor-to-target comparison is exactly one commit and one modified file: the proposal above. No runtime-boundary implementation, worker, loader, manifest builder, workflow, receipt, marker, execution authority, seed, allocation, claim, or gameplay source is introduced by the qualifying target.

This packet branch is only a review-delivery branch and is NOT the qualifying target.

## Governing texts

Review the target against the unchanged governing documents at the target commit:

- `industrial-waste/v2/R1_CAPABILITY.md`
  - blob: `d86b946765d2b1637c3feac0d2e760c012063c94`
- `industrial-waste/v2/R1_COMPLETE_RUNTIME_BOUNDARY_PROPOSAL.md`
  - blob: `a2161dda29fec1ac9e59f8a9e6b2c37b4d5a09cc`

Existing accepted supplemental evidence remains scoped only to:
- run `36912341177`
- artifact `11190020145`
- artifact SHA-256 `d5c025ed75f61a53db78b2169f6c2da88c88d0b872ffd397917782a33a229d39`
- disposition `ACCEPTED_FOR_SUPPLEMENTAL_HERMETIC_CAPTURE_SCOPE_ONLY`

The capture found the two launch class censuses repeat-equivalent under the accepted capture rule, with the only multiplicity delta normalized to `java.lang.invoke.LambdaForm$MH/0x<HIDDEN>` from `__JVM_LookupDefineClass__`. This remains supporting evidence only. Receiving observations MUST NOT create expected manifest membership or widen a generated/hidden exception.

## Non-qualifying technical analysis

The earlier Pest/Industrial analysis is preserved on this review-delivery branch as:

`industrial-waste/v2/evidence/NONQUALIFYING_ARGENTUM_PEST216_INDUSTRIAL214_REVIEW_20261001.txt`

Exact SHA-256:
`5ec7f13d8ebf3322db7fec95cf6b7c403f11d95afbe2ee9453eab205283e0753`

It is **NON-QUALIFYING TECHNICAL ANALYSIS ONLY** because its reviewer participated in authoring/publishing the prior proposal. It may identify defects to inspect; it MUST NOT satisfy this independent adoption review.

## Demonstrated findings repaired in the exact target

### I-3 — generated/hidden rule was deferred

Disposition: **REPAIRED FOR RE-REVIEW**.

The target now makes generated/hidden treatment normative. Ordinary non-VM generated material must be eliminated or reproducibly generated before launch and included by exact bytes. Runtime application/framework bytecode generation, custom loaders, agents, instrumentation, retransformation and hot replacement are prohibited.

The sole proposed hidden exception is deliberately narrower than the supporting capture: only the pinned `java.base/java.lang.invoke.InvokerBytecodeGenerator` -> `java.lang.invoke.LambdaForm$*` lookup-defined hidden family is allowed, with exact generator/Lookup bytes, manifest-bound non-JDK inputs/call sites, the fixed Lookup hidden-definition mechanism, and no redefinition/shadowing. JDK lambda proxy classes, ProxyGenerator, Unsafe/defineClass, application/third-party generators, other hidden families and other definition mechanisms fail closed and require a separate prospective independently adopted amendment.

Review whether this is precise enough to satisfy the governing requirement for a pinned generator, immutable inputs, loading mechanism and non-redefinition constraints. Do not infer implementation/qualification from the capture.

### I-4 — complete-runtime surfaces were under-specified

Disposition: **REPAIRED FOR RE-REVIEW**.

The target now explicitly carries forward the complete source/build/Gradle/toolchain graph; ordered launcher/Gradle/worker classpath and module path; complete directory/archive/resource inventories; multi-release and duplicate precedence; JDK/container/native/dynamic-loader closure; Python interpreter/stdlib/import closure; shell/just/executable launcher chain; working directory and allowed environment; loader topology/delegations/code sources/mounts; and explicit rejection of undeclared PATH/PYTHONPATH/CLASSPATH/JAVA-option/init/plugin/native-library influence, network fallback, runtime compilation, or classpath append.

Review completeness against the governing document; do not treat the list as permission to omit a required surface.

### I-5 — generated/transient reproducibility was non-normative

Disposition: **REPAIRED FOR RE-REVIEW**.

The target now requires every generated/transient pre-launch byte to have a prospectively bound generation recipe, generator bytes and complete immutable inputs, and to be deterministically reproducible before manifest publication. Non-reproducible drift is a qualification failure and cannot be normalized, learned, omitted or reblessed.

### I-6 — closure failure did not explicitly prohibit manifest/attestation/admission

Disposition: **REPAIRED FOR RE-REVIEW**.

The target now states that any unresolved graph edge, loader/delegation, native dependency, Python import, launcher component, environment influence, archive/directory member, generated/transient byte or provenance relation permits only a failure record. It expressly prohibits canonical-manifest emission/adoption, prepared-worker attestation, Python admission, claim/allocation/seed read and game initialization.

## Review criteria

Return a source/rule disposition on the exact clean target only. Explicitly decide:

1. prospectivity and non-retrospective expected membership;
2. deterministic canonical construction and generated/transient reproducibility;
3. complete launcher/build-tool/loader/resource/native/platform/Python/shell/environment coverage;
4. immutable dependency-store versus generated/transient distinction;
5. generated/hidden class classification, including the single exact pinned generator/input/loading/non-redefinition exception and hard failure of every other family;
6. whether the rule is precise enough for one atomic later implementation without inventing missing authority;
7. no-manifest/no-attestation/no-admission behavior on incomplete closure;
8. prohibition on learning expected authority from receiving/capture evidence;
9. separation of rule adoption, implementation, independent source/runtime qualification, sealed-launch acceptance and R1 permit.

A green build, successful prior capture, source-author validation, or this packet is not adoption.

## Reviewer independence requirement

A qualifying reviewer must be genuinely fresh for this exact target: they must not have authored the proposal, authored this repair, published the target bytes, or participated in the implementation conversation that produced them.

If that independence condition is not satisfied, preserve any useful technical findings only as non-qualifying analysis and do not issue a qualifying adoption disposition.

## Prohibited actions during this review

Do not:
- execute or rerun the supplemental capture;
- execute a worker;
- generate/adopt an expected manifest;
- implement the runtime boundary;
- create acceptance/authorization receipts or markers;
- accept a sealed launch;
- issue an R1 permit;
- read/generate official seeds;
- allocate, claim, initialize, or play games.

## Requested disposition

Return exactly one qualifying rule-level outcome bound to commit `cf7e623d9ae2d7095820578d919c8cea64cb9596`, tree `17e980edc5c97606a30cfdcda4b68410713081ec`, and proposal blob `5da4912ad51c478b33588eab13af5e7bd95448d5`:

- `ADOPTABLE_AS_WRITTEN_FOR_IMPLEMENTATION_STAGE_ONLY`, or
- `BLOCKED` with concrete rule-level findings tied to the governing text.

Even an adoption outcome would authorize only the subsequent prospective implementation stage. It would not itself adopt an expected manifest, accept a sealed launch, issue an R1 permit, or authorize seed/allocation/claim/gameplay.
