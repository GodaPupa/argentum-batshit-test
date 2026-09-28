# R1 complete executable boundary — prospective review proposal

Status: DRAFT_REQUIRES_INDEPENDENT_ADOPTION_AND_IMPLEMENTATION.
Parent source: `4a8b31bc7c87e3331272758a764aaa442b6ca63c`.
This document contains no expected byte values, adopted manifest, qualification,
claim, execution authorization, or assertion of readiness. Official allocations
remain zero. It proposes a complete universe rule, not another sampled class list.

## Governing constraints preserved

The frozen `protocol-v2-r1.json` execution gates require exact effective rules,
engine commit/tree, compiled card snapshot, pilot, runner, telemetry and artifact
identity; faithful used mechanics; deterministic non-corpus qualification; durable
exclusive claim before initialization; and separately recorded execution authority.
The `r1-runtime-composition-contract.json` additionally requires published immutable
path/digest/commit bindings, independent source review, receiving/combined-source
qualification, accepted checkpoint clarification, and no retry or replacement.
All remain prerequisites. Frozen decks, orderings, seeds, metrics, margins, caps,
execution order and interpretation are unchanged.

The rejected eight-class capture remains candidate evidence only. Existing 3/3
loaded-resource tests and 2/2 synthetic authority tests retain their narrow scope;
neither constitutes completeness or adoption. This proposal authorizes no new
capture, calibration or official execution.

## Complete universe rule

The expected universe is the complete executable and resource supply of the
qualified launch and worker, including code that has not yet been loaded. It is
derived from independently qualified build artifacts and their dependency closure,
not from observed candidate-worker classes, reflection over a chosen type list,
source filenames, or a successful test run. No reachable-only whitelist is allowed.

The manifest must bind:

1. Exact source commit/tree and all build inputs: wrapper script/JAR/properties,
   Gradle distribution, settings and included builds, buildSrc/convention plugins,
   version catalogs, dependency locks and verification metadata, repositories and
   resolution/substitution rules, compiler and plugins, flags and toolchains.
2. Complete resolved compile/plugin/build-tool dependency graph and complete
   worker runtime graph: coordinates, variants/classifiers, project outputs,
   edges, selected versions, artifact lengths and SHA-256 values. Dynamic,
   changing or unverified dependencies are forbidden. Resolve once into an
   immutable verified store; no network resolution or fallback during admission
   or execution. A lockfile alone is not byte identity.
3. Ordered effective classpath/module path for every launcher/Gradle/worker
   process, including Gradle bootstrap and test-worker implementation, test
   framework, AI main/test outputs, rules-engine main/test fixtures, SDK,
   mtg-sets aggregate/core/every included era, Kotlin/serialization/reflection,
   logging, and every transitive artifact. This list describes categories, not
   an exhaustive hardcoded artifact list: the independently reviewed resolved
   graphs and actual ordered process paths must establish exact closure.
4. Every file in each directory output and every archive member, including all
   classes, generated serializers, top-level/nested/anonymous/synthetic classes,
   service descriptors and other resources. Include complete archive bytes and
   member inventory. Specify multi-release selection for the pinned JDK; reject
   ambiguous names, traversal, duplicate ZIP members, symlinks, missing roots,
   unexpected roots and duplicate binary names across effective loaders.
   Any intentional duplicate resource needs exact independently reviewed
   precedence; no silent shadowing.
5. Exact JDK distribution/image digest, release/modules, executables and native
   libraries; OS/container image digest, architecture, dynamic loader and native
   library dependencies; Python interpreter/standard library/import closure;
   shell/just and executable launcher chain; exact command arguments, working
   directory and allowed environment. Source pins remain source identity only.
   Reject undeclared PATH, PYTHONPATH, CLASSPATH, module-path, boot-path,
   JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS, Gradle user init scripts,
   user-home plugins and native-library-path influence.
6. Loader topology and delegations, effective code sources and immutable mount
   mapping. The expected graph distinguishes Gradle launcher from test worker.
   A Gradle loader is not automatically trusted merely because Gradle needs it:
   its exact implementation, delegation and loading behavior require review.

Inventory serialization must be versioned and canonical: UTF-8 relative names,
explicit ordinal for ordered paths, byte lengths, lowercase SHA-256, exact
artifact/entry type and loader identity; reject duplicate keys and unknown
fields. Digest the complete canonical manifest and bind its immutable publication.
The implementer must specify and independently qualify the canonical algorithm
before generating any adoptable manifest.

## Independent expected-build provenance

Use an isolated seed-free qualification build from the exact declared source and
locked verified input store. Archive build invocation, clean-output provenance,
resolved graphs, exact artifacts, inventories and original qualification results.
The build must neither read official orderings for execution nor initialize an
official allocation. Expected byte inventory may be computed mechanically from
those archived outputs, but it remains a candidate until a separate reviewer
checks complete graph coverage, build provenance, loader assumptions and artifact
identity and publishes acceptance bound to its exact digest and source.

The receiving worker must not generate or update expected values, accept its own
observed inventory as authority, learn missing entries, or substitute current
repository outputs for archived expected artifacts. Non-reproducible byte drift
fails qualification; it is not normalized away. Any necessary reproducibility
rule must be specified prospectively and independently reviewed.

## Constrained loading policy

Propose no Java/native agents, instrumentation, retransformation, hot replacement,
runtime compilation, downloaded classes, application-defined custom loaders,
unreviewed bytecode generation, append-to-classpath mechanisms or unverified
native loading. Disable dynamic attach and reject debug/agent/patch-module
arguments and undeclared environment/options. Enforcement requires a reviewed
immutable process/container configuration; checking arguments alone is insufficient.

Allow only the exact reviewed JDK loaders and, if required, pinned Gradle/Kotest
loader implementations whose behavior demonstrably loads unmodified bytes from
the sealed declared sources. No open-ended framework exemption is allowed.
Generated/hidden classes (including JVM lambda/proxy machinery) must be classified
explicitly: either eliminate them, or independently accept their precise pinned
generator, inputs, loading mechanism and non-redefinition constraints as a
separate provenance category. They cannot be represented as ordinary archived
class digests or silently excluded. Until that category is fully specified and
accepted, readiness is blocked. If the current test framework cannot satisfy
this policy, redesign the dormant launcher prospectively and qualify it; do not
weaken the policy during a receiving attempt.

Under these accepted constraints, archive identity plus verified loading
provenance establishes the relationship to executable definitions. A
`ClassLoader.getResourceAsStream` digest alone proves resource bytes, not the
actual class-definition bytes. Code-source URLs alone also do not prove identity.
If constraints cannot establish that relationship, require a separately reviewed
definition-time attestation mechanism and its own trusted agent/toolchain boundary;
that is a new proposal, not an implicit exemption to this no-agent policy.

## Admission and lifetime enforcement

Before any claim creation, require independently accepted complete expected
manifest, this boundary's adopted version, immutable build/qualification evidence
and exact published reviewer binding. Prepare must fail closed if any is absent.

The worker then verifies its actual process paths, immutable archive bytes and
complete inventories against that authority before calling Python `admit`,
deriving an allocation request, or initializing a game. Bind the verified worker
to source, manifest/review digests, runtime receipt, execution owner, unique
worker process instance, canonical output and existing claim/consumption binding.
This attestation is evidence, not an execution permit. Python must independently
validate the binding before admitting its first allocation; a caller-supplied
"verified" Boolean or replayed JSON file is insufficient.

The implementation must specify a reviewed authenticated process-channel or
equivalent sealed-launch attestation mechanism, including freshness and replay
rejection. This document does not claim that mechanism exists. It must distinguish
the preclaim expected-authority gate from the later prepared-worker gate so no
worker under an existing claim can bypass receiving verification.

Keep executable sources sealed for the entire process lifetime, disable extension
paths, and enforce loader policy for later loads. A startup inventory snapshot
alone cannot rule out later mutation. Record class-definition/source provenance
as permitted by the accepted constrained loading mechanism. Any unknown source,
loader, definition mechanism, artifact mismatch or integrity failure stops before
the next admission; after initialization, quarantine under the frozen no-retry rule.
No fallback, repair-in-place, second worker, continuation or replacement sample.

## Required implementation and independent acceptance gates

Before replacing any current authority schema, independently review this proposal
and publish its exact accepted digest. A subsequent atomic implementation must
introduce the canonical complete-manifest schema, graph/inventory verifier,
sealed launch and lifetime policy, prepared-worker attestation and Python admission
receiver, and update all affected source pins together. Existing v1 class-map
acceptance cannot silently stand in for this boundary.

Use deterministic non-corpus fixtures for: missing/extra dependency/root/class/
resource; changed class/resource/JAR/JDK/launcher/Python bytes; classpath order;
duplicate/shadow and multi-release handling; loader/agent/option injection;
later mutation/load; missing/stale/replayed/wrong-worker attestation; source or
review mismatch; and refusal before admit/initialization. Qualify the combined
receiving source through `just` under the repository verify skill, retain original
artifacts, and conduct independent source review. Do not rerun exhausted capture
or calibration gates unchanged.

A review must explicitly decide universe completeness, dependency/build
provenance, constrained loader/generated-class coverage, native/platform trust,
attestation authenticity, immutable lifetime enforcement and every fail-closed
boundary. Only then may the separate frozen runtime/receiving/authorization/claim
gates be evaluated. The draft itself closes none of them.

## Authority requested

This is a prospective supplement detailing existing runtime-identity requirements,
not an amendment to experimental design or permission to execute. The examined
frozen requirements demand independent review; they do not specify that only a
human may author or review this supplement. Separate independent adoption of the
complete boundary and later exact manifest/implementation is required. No
unavailable human-only authority is asserted, and the author must not self-adopt.
