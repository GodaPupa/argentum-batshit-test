# Project Pest Control — Status

- Status: Sample #2 Take 8 vector frozen and unexecuted; gameplay requires separate explicit authorization after the exact freeze head is remotely green
- Laboratory: Project Pest Control
- Branch: `pest-control/lab`
- Validated base: `47882cd645caf126afee6cf13a65909806fa40ab`
- Accepted rules-complete remote head: `b08d8fad6e6709db4834e8e956e6853152efb3b5`
- Accepted Phase 3 remote head: `8dd4a429e0799b60e7449c214a540dd8414b1f1c`
- Accepted Weather regression replay remote head: `8a13f2d8f9322b85ba6c52de571c591fea966a31`
- Game 16/Game 28 correction implementation head: `dd0572ab61c6a2dd3d2e76a578999c2fa822b2c0`
- Game 16/Game 28 remotely validated head: `9e5adc4416c1d5c2684befa9ea699be28fe6c2f0` (CI #217)
- Sample #2 Take 2 correction remotely validated head:
  `14bea9a1f0c67be74aae8f48704186e2277de1b1` (CI #220)
- Sample #2 Take 3 observability implementation head:
  `e316117ee71b30b170e241c8eb110bf8d4d20117` (CI #224)
- Sample #2 Take 4 observability implementation head:
  `860d2f72b63bac460b90a04a1cc561bbb40ac9a9` (CI #228)
- Sample #2 Take 5 modal-removal policy implementation head:
  `6385a6e79f6bef5ce527129de560131a4dc7d68e` (CI #232)
- Sample #2 Take 6 same-turn sequencing/telemetry implementation head:
  `2cbfa7cd4c94fc6007288a27e2427183e9a158a8` (CI #236)
- Take 7 sequencing/observability correction implementation head:
  `6cbe6ea11bdc5c0f286f7763b35db45d198bc58a` (CI #240)
- Sample #2 Take 7 ordered seed-value SHA-256:
  `af294f8238ba796082333994450f6f98d9a5b371e166591ba17c60de76aa7488`
- Sample #2 Take 8 ordered seed-value SHA-256:
  `d20e57b5e588546911d6f16b63d274651038bd49b53580f27f9a208448859f2f`
- Control version: Pest Control v1.0 (permanent, immutable)
- Candidate status: proposed Tier-1 architecture only; not approved, constructed, or run

## Goldfish Sample #2 independent replication authorization

### Sample #2 Take 8 seed freeze

Starting from exact validated head `c6b41a560d139a1f17f8ac1a68fd75cd29bf2ae8`, one new Take 8
vector was deterministically derived and frozen before any gameplay. It contains exactly 30 unique
seeds. Its ordered-vector SHA-256 is
`d20e57b5e588546911d6f16b63d274651038bd49b53580f27f9a208448859f2f`, its seed CSV SHA-256 is
`02dca916c2d4b6d5b921517f6dadc97c5bb4b515c25daf72a0ffe9f6cc32c45f`, and the permanent-control
SHA-256 remains `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`.

After refreshing all available local, remote, and pull-request refs, a read-only audit covered 41
refs, 25 distinct tip trees, 676,917 path instances, 27,314 unique blobs, and 240 unique
seed-category blobs. It produced a 2,063-value normalized positive-`Long` exclusion set containing
every known Pest Control, Batshit Economics, and Project X development, scenario, smoke, regression,
replay, performance, optimization, replication, rejected, retired, and previously frozen seed. Take
8 has zero overlap with that set and every prior Pest vector.

The complete derivation, ordered vector, collision scope, hashes, and immutable execution contract
are recorded in `docs/experiments/pest-control/goldfish-sample-2-take-8-seed-freeze.md`. The Take 8
runner is hard-disabled, every prior Pest runner remains disabled, and no Batshit or Project X runner
is part of this readiness gate. Zero seeds and zero games executed. Gameplay requires separate
explicit authorization after this exact freeze head passes remote CI.

### Sample #2 Take 7 disposition

Freeze commit `1b80acf203facb0eed3a4ae1ae165a786d1a7253` passed CI #238. From an otherwise clean
checkout of that exact head, one authorized invocation executed all 30 Take 7 seeds exactly once in
the committed CSV order. The ordered seed-value SHA-256 remained
`af294f8238ba796082333994450f6f98d9a5b371e166591ba17c60de76aa7488`, and the permanent-control
SHA-256 remained `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`.

Artifact completeness passed: the final JSON contains 30 distinct completed games in exact order,
normal engine terminal records, all mandatory sequencing and removal fields (including explicit
zero/false/empty/null/default values), and a selected `policyApplied=true` record for every executed
friendly removal. Weather copy accounting, Follow conditions, Researcher/Mascot counters,
Carrier/Scion provenance, mana/bottleneck telemetry, and terminal records were internally consistent.

Take 7 is **rejected in full**. Ten complete-sequence audits across Games 7, 13, 15, 16, 18, 19,
22, and 30 found production casting Weather before a validated materially superior setup-first line.
The affected setup actions were Follow the Lumarets, Cast Down, Chainer's Edict, or Bone Shards;
Game 30 also preserved the legal land-unlocked Forest → Bone Shards → Weather line. This is a
clear production-policy/validated-telemetry disagreement under the predeclared whole-block standard.
No correction is authorized or included, and no pooled analysis is admissible.

The vector is permanently retired and hard-disabled and may never be replayed, rehabilitated,
replaced, compared against, optimized against, or reused. The raw artifact, report, and detailed
manual audit are preserved under `docs/experiments/pest-control/`. Sample #1 remains the sole
accepted Pest Control performance/engine evidence; Pest Control v1.0 remains exact; the challenger
remains audit-only and unconstructed.

#### Take 7 duplicate-focal and sequencing-observability correction gate

**SHARED ARGENTUM CHANGE: yes.** Implementation
`6cbe6ea11bdc5c0f286f7763b35db45d198bc58a` is remotely green in CI #240.

Production's land-unlocked deferral previously matched only the physical card ID selected by its
bounded planner. With two otherwise interchangeable copies of the focal action, the unselected copy
bypassed the deferral and could be cast before the validated land-first line. Deferral now compares
complete semantic cast identity: card definition/face, controller and source zone, modes, targets,
X, alternative and additional costs, resolved payment requirement, timing/permission metadata, and
copy-specific legality/outcome distinctions. Only physical source identity and the interchangeable
choice of legal mana sources are ignored for this comparison; exact entity IDs remain authoritative
for legality, payment, execution, zone movement, and telemetry. The two-copy reconstruction now
selects the exact legal land action, while non-equivalent cards, modes, targets, costs, permissions,
and outcomes remain distinct. No other demonstrated production behavior changed.

`PreSpellSetupTelemetry` remains observational and does not select actions. Its shadow comparisons
now consume fresh production materializations, production hold/admissibility results, and the same
relevant sequencing adjustments. Below-margin friendly removal, null forced sacrifice, strategically
null Storm setup, and premature expiring-condition follow-ups cannot become missed-superior setup.
Productive removal and genuine Game 16 and Take 6 Game 18 setup-first lines remain eligible. Both
orders use equivalent bounded same-turn horizons with explicit legal mana-source plans. Structured
records preserve physical and semantic identities, production admissibility/rejection reason,
static and adjusted values, pass value, continuation horizon, and complete comparison lines.

The hard final-JSON contract requires those fields—including zero, false, empty, and null values—to
survive round-trip serialization. Focused duplicate-copy, sequencing, self/modal-removal, null-action,
telemetry, and artifact-contract regressions passed. Complete AI, Gym, rules-engine, and card-scenario
suites passed locally; all Pest gameplay runners were skipped. CI #240 passed the complete remote
matrix. No Pest seed or gameplay vector executed. Take 7 and every earlier rejected vector remain
retired and hard-disabled; Sample #1 remains the sole accepted performance evidence; the permanent
control and audit-only challenger are unchanged. At that correction gate, no Take 8 seed existed and
seed generation still required separate explicit authorization; the subsequent authorized freeze is
recorded above.

### Sample #2 Take 6 seed-readiness gate

The hard pre-seed gates passed against the exact accepted implementation: the final serialized
`PestGoldfishBlock` artifact contract is green, the nested/modal targeted-removal regression reaches
the general friendly-removal policy and serializes `policyApplied=true`, and the permanent-control/
retired-vector guard is green with every gameplay runner skipped.

Only after those gates passed, the complete new 30-seed Take 6 vector was derived and frozen before
Game 1. Its ordered seed-value SHA-256 is
`79659bf0a8823c94e288b2df46f246385d02dba236ce3b70f9a4c6dc477eb79b`; the exact permanent-control
hash is `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`. A read-only audit of all
40 repository refs (26 distinct trees) produced a 1,903-value exclusion set and confirmed zero
overlap with every known Pest Control, Batshit Economics, and Project X seed vector. The full
derivation, ordered vector, hashes, and immutable execution contract are recorded in
`docs/experiments/pest-control/goldfish-sample-2-take-6-seed-freeze.md`.

Freeze commit `eeb8321d5f0f1dc84cf206a2767ffaaec134ca54` passed CI #234. One preliminary invocation
failed during Kotlin test compilation and executed zero games. After a clean disabled-state rebuild,
the single gameplay invocation executed all 30 seeds exactly once in frozen order.

Take 6 is **formally rejected in full**. Game 18 cast Weather, then played an untapped Swamp, then
cast Blood Researcher. Its preserved land history proves that land → Researcher → Weather was fully
executable for the same five mana and would have put an additional relevant counter on the second
Researcher. The detector nevertheless serialized no evaluated candidate, no land-unlocked spell,
and no missed-superior flag, while separately recording the later Researcher cast. This is a clear
land-drop sequencing and telemetry defect. No correction is authorized or included.

The Take 6 vector and ordered SHA-256
`79659bf0a8823c94e288b2df46f246385d02dba236ce3b70f9a4c6dc477eb79b` are permanently retired and
hard-disabled. The raw artifact and report are quarantined audit history, not performance evidence;
no pooled analysis is admissible. Complete execution and audit details are in
`docs/experiments/pest-control/goldfish-sample-2-take-6-rejection-audit.md`. Sample #1 remains the
sole accepted Pest performance/engine sample. No replacement vector may be generated without new
authorization.

#### Sample #2 Take 6 Game 18 correction gate

**SHARED ARGENTUM CHANGE: yes.** Implementation
`2cbfa7cd4c94fc6007288a27e2427183e9a158a8` is remotely green in CI #236.

The planner's bounded same-turn search was Storm-specific and depended on an incidental automatic
mana-source choice. It could therefore miss a legal complete line when the first spell's automatic
payment consumed a color needed by the second, and it did not generally compare deploying a new
immediate payoff before an event against taking the event first. Production sequencing now inspects
immediate event-producing actions structurally, explores authoritative explicit mana-source choices,
and compares the same land and cards in the complete setup-first and focal-first orders. It prefers
setup first only when the full line is executable and exceeds the existing materiality margin;
survival, resource conflicts, irrelevant deployments, temporary-condition loss, and future-capability
costs remain valid reasons to take the focal action first.

The deterministic Game 18 reconstruction now chooses Swamp → Blood Researcher → Weather rather than
Weather → Swamp → Researcher. A separate counter/payoff conversion probe also deploys a newly
land-unlocked lifegain payoff before the event, while an insufficient-mana reconstruction declines
the reorder. Game 16 land → relevant spell → Weather, Games 1/8/30 equivalent double-Weather
non-superiority, temporary-condition/Follow sequencing, and targeted/modal and friendly-removal
behavior remain green.

Sequencing telemetry no longer accepts an automatic payment as the only feasibility answer and no
longer compares the proposed setup with a different focal-first continuation. Every complete
evaluation now preserves both source-specific action sequences, active payoffs before and after the
deployment, the immediate payoff-value delta, both final resource states, and whether those resources
are equivalent. The hard final-JSON contract requires the full Game-18-style land, deployment,
orders, payoff delta, resource comparison, classification, and superiority result while retaining all
modal/friendly-removal requirements.

Focused Game 18, sequencing, telemetry, artifact-contract, modal/friendly-removal, Game 16,
double-Weather, and Follow regressions passed. The complete AI, Gym/Pest, rules-engine, and card-
scenario suites passed locally; CI #236 passed the complete remote matrix. All retired Pest gameplay
runners remained skipped, and no retired seed or Pest gameplay vector executed.

At completion of this historical correction gate, the project was stopped at the Sample #2 Take 7
seed-readiness gate and no Take 7 vector yet existed. Subsequent Take 7 freeze and execution history
is recorded in the current disposition above.

The Take 5 modal-removal policy correction is accepted as remotely green. Implementation
`6385a6e79f6bef5ce527129de560131a4dc7d68e` passed CI #232. **SHARED ARGENTUM CHANGE: yes.**

The bypass was in production action classification: the strategist used the deliberately broad,
historically frozen whole-card intent fold, which treats `ModalEffect` interiors as opaque. A
concrete targeted-removal cast nested in a chosen mode therefore reached simulation and complete
friendly-removal observability, but not the established fair-trade hold policy. Production
classification now inspects the concrete cast's selected semantic effect branch, adds only
targeted-answer intent tags, and leaves whole-card rating and unrelated modal choices unchanged.
Top-level and selected modal targeted removal consequently receive the same general policy.

Deterministic reconstructions preserve the complete additional-cost and resulting-state valuation.
Game 2-, 26-, and 28-equivalent structures now apply the existing margin and reject below-margin
Carrier death lines, including sacrifice of the target itself, discarded-interaction opportunity
cost, and incidental Scion/lifegain value. A Game 23-equivalent productive line still clears the
margin and remains selectable; the productive Cast Down control remains selectable as well. An
opposing valuable target remains preferred, passing remains preferred when no line clears the bar,
and a selected non-removal mode is not misclassified from an unchosen removal mode.

The final-JSON artifact contract remains green and now requires the nested modal-removal audit to
serialize `policyApplied=true` with its complete target, additional cost, pass/hold, fair-trade, and
selection data. Focused action-intent, Take 5 reconstruction, friendly-removal, artifact-contract,
sequencing, Bone Shards/Pest scenario, and frozen-control/vector tests passed locally; the complete
AI suite passed. Every retired gameplay runner was skipped. No retired seed or Pest seed vector was
executed.

Take 5 remains rejected in full. Its vector SHA-256
`4e239b8b76df587ec3e14f05f564a288480de42abcd681670c7330323631c1f0` remains permanently retired
and hard-disabled. Sample #1 remains the sole accepted Pest Control performance/engine sample,
Pest Control v1.0 remains exact, and the challenger remains audit-only and unconstructed. No Take 6
seed has been generated; new seed generation requires separate explicit authorization.

### Sample #2 Take 5 authorization and freeze

The Take 4 observability-correction gate is formally accepted. Implementation
`860d2f72b63bac460b90a04a1cc561bbb40ac9a9` is green in CI #228 and final head
`5152f50b463889c9282c7bf1ea0d6a8484fdeb0e` is green in CI #229. Take 4 remains rejected
permanently; its complete vector and SHA-256
`9f53ae30fd233bc8a980163aff9a7df1d6b41095356072e520a2cad16f97a7ad` remain retired and
hard-disabled. Games 8, 18, and 23 remain permanently unclassifiable historical actions, not policy
failures.

Before seed generation, the hard final-JSON artifact contract passed on exact current implementation
head `5152f50b463889c9282c7bf1ea0d6a8484fdeb0e`. It generated the actual final artifact and proved
all five sequencing classifications, prefixes and complete continuations, actual/counterfactual
lines, target/controller and modal/additional-cost identities, pass/hold and fair-trade values,
selection status/reason, selected executed-friendly-removal audits, and mandatory zero/false/empty/
null/default fields survive final serialization.

After that green prerequisite, a new 30-seed vector was deterministically derived and frozen before
Game 1. All 40 refreshed refs (24 distinct trees) were audited read-only across 2,486 seed-category
path instances, 219 unique seed-category blobs, and 27,208 text blobs, producing an exclusion set of
1,899 normalized positive `Long` values. Overlap is zero. The vector SHA-256 is
`4e239b8b76df587ec3e14f05f564a288480de42abcd681670c7330323631c1f0`; its derivation, complete
vector path, audit scope, and immutable contract are recorded in
`docs/experiments/pest-control/goldfish-sample-2-take-5-seed-freeze.md`.

At this freeze point no Take 5 game has executed. The vector and permanent-control guards must pass
locally and the freeze head must be remotely green before the single authorized execution. Sample #1
remains the sole accepted performance/engine sample, Pest Control v1.0 remains exact, and the
challenger remains audit-only and unconstructed.

#### Sample #2 Take 5 disposition

Freeze head `b5ea795833de919f6f94ed314cce6cd8c15dd17a` passed CI #230 before Game 1. A first local
invocation stopped during Kotlin test compilation and consumed zero seeds. After the identical
disabled freeze state recompiled green, one forced opt-in invocation executed every seed exactly once
in frozen CSV order. No seed was rerun, replaced, excluded, or substituted, and no deck, policy,
telemetry, or mid-sample change occurred.

The audit-completeness gate passed before performance interpretation. The final JSON has the exact
frozen order and vector hash, zero built-in audit errors, complete structured sequencing records, and
a selected friendly-removal audit corresponding to each of the five executed friendly removals.
Rules/state, mana, Weather/Storm, Follow conditions, trigger/counter accounting, Carrier/Scion
provenance, Ent decisions, bottleneck records, and terminal records are internally clean.

Take 5 is nevertheless **formally rejected in full** for a demonstrated general removal-policy
coverage defect. Bone Shards' removal effect remains unclassified inside its modal wrapper, so its
friendly-removal candidates record `policyApplied=false`. Games 2, 26, and 28 selected self-removal
despite fair-trade surpluses of approximately `-1.155`, `-0.919`, and `-2.074`. Those lines did not
clear the existing margin for the destroyed permanent, removal resource, mana/additional cost, and
future interaction. Game 23's Bone Shards line had positive surplus and is not independently judged
bad; Game 9's Cast Down line applied the policy and cleared its margin. The complete diagnosis is in
`docs/experiments/pest-control/goldfish-sample-2-take-5-rejection-audit.md`. No policy correction is
authorized or included.

The Take 5 vector and SHA-256
`4e239b8b76df587ec3e14f05f564a288480de42abcd681670c7330323631c1f0` are now permanently retired
and hard-disabled. They may never be replayed, rehabilitated, replaced, compared against, optimized
against, or reused. The losslessly compressed raw artifact and complete human report are preserved
under `docs/experiments/pest-control/`; their aggregates are quarantined and are not performance
evidence. No pooled Sample #1 + Take 5 analysis is admissible. Sample #1 remains the sole accepted
Pest Control performance/engine sample, Pest Control v1.0 remains exact, and the challenger remains
audit-only and unconstructed.

### Sample #2 Take 4 authorization and freeze

The Take 3 observability correction gate is formally accepted. Implementation
`e316117ee71b30b170e241c8eb110bf8d4d20117` is green in CI #224 and final documentation head
`aeb0df41c25c762acd37f2abf301f612fa7c2c28` is green in CI #225. Take 3 remains rejected permanently;
its full vector is retired and hard-disabled, and Games 13 and 14 remain historically unclassifiable
rather than policy failures.

After explicit authorization, a new ordered 30-seed Take 4 vector was deterministically derived and
frozen before Game 1. A read-only collision audit covered all 40 fetched local, remote, and pull-
request refs (24 distinct tip trees), 548 seed-category path instances, 103 unique seed-category
blobs, 73,693 seed/category-bearing lines, and 1,692 normalized positive `Long` values. Overlap is
zero. The vector SHA-256 is
`9f53ae30fd233bc8a980163aff9a7df1d6b41095356072e520a2cad16f97a7ad`; its derivation, audit scope,
and immutable execution contract are recorded in
`docs/experiments/pest-control/goldfish-sample-2-take-4-seed-freeze.md`.

Take 4 uses exactly the validated Pest Control v1.0 laboratory configuration, Sample #1 metric set,
and complete Take 3 observability schema. Before gameplay can be interpreted, the machine-readable
artifact must pass a predeclared completeness gate covering every sequencing evaluation and every
friendly-removal evaluation or execution. The sample must execute once in frozen order and is rejected
in full for any clear rules/state, telemetry, observability, mana-provenance, sequencing, or agent-
policy defect. At this freeze point no Take 4 game has executed. Sample #1 remains the sole accepted
performance/engine sample, and the challenger remains audit-only and unconstructed.

#### Sample #2 Take 4 disposition

Freeze head `0e5966c5069f8b8b71f8557aceaeaecd0920c0c3` passed CI #226 before Game 1. One forced opt-in
invocation then executed all 30 seeds exactly once in frozen order. The vector order and SHA-256
matched the committed freeze, and no reroll, replay, replacement, exclusion, substitution, or mid-
sample change occurred.

Take 4 is **formally rejected in full**. Games 8, 18, and 23 executed Bone Shards, but the artifact
contains no selected friendly-removal audit for those completed actions; their targets, additional-
cost choices, pass/hold comparisons, alternatives, resulting boards, and net selection reasons are
therefore unavailable. The predeclared completeness checker also produced five false failures because
it compared abbreviated non-superior setup prefixes with full proposed setup-plus-Weather sequences.
Game 23's targeted Bone Shards was not considered by the pre-Weather sequencing detector, and the
missing target/cost data prevents conclusive counterfactual classification. These are observability
and audit-coverage defects, not demonstrated gameplay-policy failures.

The complete Take 4 vector is permanently retired and hard-disabled. It may never be replayed,
rehabilitated, replaced, compared against, optimized against, or reused. Its losslessly compressed raw
JSON artifact, generated report, and complete rejection audit are preserved under
`docs/experiments/pest-control/` as `goldfish-sample-2-take-4-raw.json.gz`,
`goldfish-sample-2-take-4-report.md`, and
`goldfish-sample-2-take-4-rejection-audit.md`. Quarantined aggregates are not
performance evidence and no pooled 60-game analysis is admissible. Sample #1 remains the sole accepted
Pest Control performance/engine sample; Pest Control v1.0 remains exact; the challenger remains audit-
only and unconstructed. No corrective implementation is authorized by this disposition.

#### Sample #2 Take 4 observability correction gate

**SHARED ARGENTUM CHANGE: yes.** Implementation
`860d2f72b63bac460b90a04a1cc561bbb40ac9a9` is remotely green in CI #228. The detailed correction
record is `docs/experiments/pest-control/sample-2-take-4-observability-corrections.md`.

Bone Shards candidates were already concrete targeted casts with an additional-cost payment when
scored, but the general intent fold did not classify the removal effect nested in its modal wrapper.
The friendly-removal audit therefore returned before inspecting the resolved target, payment, and
events. The audit path now recognizes a concrete friendly-targeted removal outcome independently of
that broad intent tag and records whether the production removal policy itself was applied. The
production hold/targeting policy is unchanged.

The final Pest JSON writer now emits defaults and nulls explicitly. Friendly-removal records preserve
the selected target, target/controller/value, mana and additional-cost mode/payment identities,
removal and future-interaction costs, friendly and opposing alternatives, pass value, triggers and
created resources, complete resulting friendly board, engine/lethal/prevention/resource-transition
effects, net/fair-trade comparison, policy applicability, and selection/rejection reason. Executed
friendly removal must have a selected audit record.

Sequencing telemetry now materializes only authoritative legal target and additional-cost
combinations and can evaluate a complete targeted/modal setup-to-Weather continuation. It serializes
the setup prefix separately from the complete setup continuation, actual line, complete
counterfactual, and complete comparison line. The completeness gate compares equivalent
representations, eliminating the five Take 4 prefix-versus-continuation false failures while
preserving all five classifications and the genuine Game-16-style regression.

A hard pre-sample artifact-contract test constructs deterministic synthetic decisions and all five
sequencing classifications, writes the exact final `PestGoldfishBlock` JSON, reparses it, and proves
that target/cost identities, selected audits, complete sequences, pass/fair-trade comparisons, and
zero, false, empty, null, and default-valued fields survive. No future seed freeze or generation is
permitted unless this exact contract is green in CI and seed generation is separately authorized.

The preserved Take 4 artifact lacks the target, additional-cost identity, pass comparison, complete
alternatives, resulting board, and selection reason needed to reconstruct Games 8, 18, or 23 exactly.
They remain permanently unclassifiable historical actions, not policy failures. Deterministic
synthetic coverage demonstrated an auditable, productive Carrier-death line that creates a Scion and
deterministic lethal; it did not demonstrate a new production gameplay-policy defect. No policy was
changed.

Focused schema/artifact-contract, sequencing, and removal-agent regressions; the complete AI suite;
Pest rules/engine scenarios; and frozen-control/vector guards are green. CI #228 is green across the
full runnable matrix. Every retired Pest runner remained disabled, and no retired seed or Pest seed
vector executed during correction or validation.

The project is stopped at the Sample #2 Take 5 seed-readiness gate. No Take 5 vector exists or has
been generated. Pest Control v1.0 remains exact; Sample #1 remains the sole accepted performance/
engine evidence; the challenger remains audit-only and unconstructed; and gameplay, optimization,
opponent self-play, and Sample #3 remain prohibited.

### Sample #2 Take 3 authorization and freeze

The Sample #2 Take 2 correction gate is formally accepted: implementation
`14bea9a1f0c67be74aae8f48704186e2277de1b1` is green in CI #220, and final documentation head
`99be2946c20c3a10f17b9e2a9d1c0c86809a877f` is green in CI #221. Take 2 remains rejected at
`2b770b353a3d3ec387e9c5607c938cbaa2007eaf`; its full vector remains permanently retired and
hard-disabled.

After explicit authorization, a new ordered 30-seed Take 3 vector was deterministically derived and
frozen before Game 1. A read-only collision audit covered all 40 available local, remote, and
pull-request refs (25 distinct tip trees), 1,694 seed-category path instances, 171 unique relevant
blobs, 216,475 seed/category-bearing lines, and 1,704 normalized positive-`Long` values. Overlap is
zero. The vector SHA-256 is
`341fc7936a8a415d19d198662d1f6f2b2120ecb70d055a3a7a6fb76dd1ec8c47`; its derivation and immutable
execution contract are recorded in
`docs/experiments/pest-control/goldfish-sample-2-take-3-seed-freeze.md`.

Take 3 uses exactly the current validated Pest Control v1.0 laboratory configuration and complete
Sample #1 metric set. It must execute once in frozen order and be rejected in full for any clear
rules/state, telemetry, mana-provenance, sequencing, or agent-policy defect. At this freeze point no
Take 3 game has executed. Sample #1 remains the sole accepted performance/engine sample, and the
challenger remains audit-only and unconstructed.

#### Sample #2 Take 3 disposition

The freeze passed CI #222 at exact head `d9bddfe67f78232e12b09003a239a0890c4f65d8`. A single forced
opt-in invocation then executed all 30 seeds once in frozen order. The automatic runner completed
green with no built-in audit errors, and trace-wide count/order/rules consistency checks passed.

Take 3 is nevertheless **formally rejected in full**. The persisted Weather telemetry omitted the
required `still unexecutable after land` and `executable but not materially superior` categories even
though the corrected classifier computes them. The action timeline also omitted removal targets and
Bone Shards' additional-cost choice. Consequently the two friendly-removal casts in Games 13 and 14
cannot be audited against their exact pass line, resource expenditure, resulting board, and lethal
necessity. These are clear telemetry/auditability defects under the predeclared whole-block rejection
standard. Details are preserved in
`docs/experiments/pest-control/goldfish-sample-2-take-3-rejection-audit.md`.

The Take 3 vector is permanently retired and hard-disabled. It may never be replayed, rehabilitated,
replaced, compared, optimized against, or reused. Its aggregates are quarantined and may not be pooled
with Sample #1. Sample #1 remains the sole accepted performance/engine evidence. Pest Control v1.0 is
unchanged and the challenger remains audit-only and unconstructed. No corrective implementation is
authorized by this rejection record.

#### Sample #2 Take 3 observability correction gate

**SHARED ARGENTUM CHANGE: yes.** Implementation
`e316117ee71b30b170e241c8eb110bf8d4d20117` is remotely green in CI #224. The change is limited to
audit observability and deterministic regression coverage; the existing removal hold/target policy
comparison remains the production decision rule.

The rejected Take 3 artifact omitted two complete sequencing categories (`still unexecutable after a
legal land` and `executable but not materially superior`) and, for friendly removal, the exact target,
controller and value, additional-cost choice, payment/resources consumed, pre/post board and
downstream effects, pass/hold comparison, opposing alternatives, and final net/fair-trade reason.
Sample reporting now preserves all five sequencing classifications as machine-readable evaluations,
including the proposed land and tapped state, mana-source snapshots, source-specific payment,
requirements, targets, temporary conditions, ordered actions, resources after each step, actual and
counterfactual lines, scores, material-superiority result, and reason.

Friendly-removal evaluations now preserve generic structured audit records for the action and exact
target, ownership and battlefield value, mana/life/additional costs, removal-resource and future-
interaction costs, death triggers and resources created, resulting board/engine/lethal/prevention or
resource-transition effects, pass/hold value, legal opposing alternatives, net/fair-trade comparison,
selection disposition, and reason. The data is derived from the same simulated actions and events used
by the existing policy; no card, game number, or Pest-specific target exception was added.

The preserved Take 3 records for Games 13 and 14 are insufficient to reconstruct the exact strategic
decision: they contain only the removal spell name and surrounding aggregate events, not the selected
target, Bone Shards additional cost, exact pre/post battlefield, pass comparison, or lethal necessity.
They remain unclassifiable historical actions. Deterministic generic scenarios did not demonstrate a
production gameplay-policy defect, so no gameplay policy was changed.

Focused sequencing and friendly-removal telemetry regressions, the complete AI suite, Pest
rules/engine scenarios, frozen-control/vector guards, and full CI are green. The Game-16-style genuine
land-unlocked opportunity and Games-1/8/30 non-superior double-Weather regressions remain covered.
All retired Pest runners, including Take 3, remained disabled and were not executed during validation.

The project is stopped at the Sample #2 Take 4 seed-readiness gate. No Take 4 vector has been generated
and no new gameplay, challenger construction, optimization, or opponent self-play is authorized.

### Sample #2 Take 2 authorization and freeze

CI #217 is fully green at tested head
`9e5adc4416c1d5c2684befa9ea699be28fe6c2f0`. That head contains the Game 16/Game 28
implementation commit `dd0572ab61c6a2dd3d2e76a578999c2fa822b2c0`, the implementation-status commit
`c50c7dfb0f93356adbfb6a9bc1d6f189e5f09042`, and only the director-map addition after the status
commit. The correction gate is accepted as remotely validated. CI #215 created zero jobs and remains
formally stranded/non-validation; it must never be cited as successful or failed validation.

After explicit authorization, a completely new 30-seed deterministic performance vector was derived
following a read-only collision audit of all 40 fetched refs (24 distinct trees), 12,721 seed-bearing
ref/path instances, 652 unique blobs, and 11,856 normalized positive-`Long` values. It has zero overlap
with every known Pest Control, Batshit Economics, and Project X development, regression, smoke,
performance, and optimization seed available in the repository. The ordered vector was frozen before
Game 1 with SHA-256 `1db3fb1fcf4b969d9229bb2a060491a556ff5e1c8c80d327caf37698ca1c3cb7`;
its derivation and immutable execution contract are preserved in
`docs/experiments/pest-control/goldfish-sample-2-take-2-seed-freeze.md`.

Take 2 uses the exact accepted Sample #1 deck, agent, engine, telemetry definitions, mulligan behavior,
horizon, and acceptance standard, plus the remotely validated sequencing corrections. At the time of
freeze, no Take 2 game had executed. The previously rejected Sample #2 and its complete original
30-seed vector remain permanently retired and hard-disabled; they are not replayed, rehabilitated,
compared, or reused. Pest Control v1.0 remains exact, and the challenger remains audit-only and
unconstructed.

#### Sample #2 Take 2 disposition

The immutable freeze is remotely green in CI #218 at exact head
`8be1f0ba160a0c817595356f4c9f5391ed570889`. All 30 seeds then executed exactly once in frozen order.
Two setup invocations ran zero games (one Gradle up-to-date no-op and one pre-test infrastructure
failure); the single gameplay invocation completed the full block. There was no reroll, replay,
replacement, exclusion, seed substitution, deck/policy/telemetry change, or mid-sample correction.

The sample is **formally rejected in full**. The automatic audit produced three errors in Games 1, 8,
and 30: it labeled `play Swamp → Weather → Weather` superior to the executed
`Weather → play Swamp → Weather`, although a basic land is not a spell and both orders have identical
Storm, mana, trigger, hand, and battlefield outcomes. This is a clear false-positive sequencing-
telemetry defect. Manual review also found unproductive self-removal in Games 9 and 29: each used Cast
Down on its own Carrier Thrall with no opponent creature, Warden trigger, same-turn Scion mana use, or
Storm setup.

The exact Take 2 vector is now permanently retired and hard-disabled. It may never be replayed,
rehabilitated, compared, sampled, optimized against, or reused. Its raw block, generated report, and
all-30 rejection audit are preserved as
`docs/experiments/pest-control/goldfish-sample-2-take-2-{raw.json,report.md,rejection-audit.md}`.
Quarantined aggregates are retained only for historical completeness; no Sample #2 performance
inference or pooled 60-game comparison is admissible. Sample #1 remains the sole accepted Pest Control
performance/engine sample. No correction, replacement vector, Sample #3, challenger construction,
optimization, or opponent self-play has begun.

#### Sample #2 Take 2 focused correction gate

**SHARED ARGENTUM CHANGE: yes.** The authorized Games 1/8/30 telemetry and Games 9/29 removal-policy
corrections use only preserved traces and deterministic scenario construction. No retired vector was
executed. The implementation is described in
`docs/experiments/pest-control/sample-2-take-2-corrections.md`.

The land/setup detector now validates complete source-paid sequences and compares a setup-first line
against the best same-length focal-first continuation. This rejects the three outcome-equivalent
`land → Weather → Weather` classifications while preserving the genuinely order-sensitive Game 16
`land → relevant spell → Weather` detection. Telemetry separates currently executable, land-unlocked,
still-unexecutable-after-land, executable-but-not-superior, and genuine missed-superior states.

General one-card removal valuation now requires a friendly-targeted removal leaf to exceed preserving
the permanent, removal option, mana, and future interaction by a concrete positive margin. It is not a
self-target ban: deterministic lethal and demonstrably superior death-trigger/resource/engine
transitions remain available. Deterministic reconstructions cover both rejected terminal decisions,
ordinary opposing-target preference, holding when neither target is worthwhile, and productive
Carrier/Scion-style death conversion.

Local focused telemetry and agent tests, both Pest rules/engine scenario classes, the frozen baseline
guard, and the complete AI suite are green. CI #220 completed green on exact correction head
`14bea9a1f0c67be74aae8f48704186e2277de1b1`: all eight runnable test/frontend jobs and the aggregate
backend gate succeeded; the main-only coverage job was correctly skipped. The correction gate is
remotely validated.

Sample #1 is still the sole accepted performance/engine evidence. The original rejected Sample #2
and Take 2 vectors remain permanently retired/hard-disabled. Pest Control v1.0 remains exact and the
challenger remains audit-only and unconstructed. The project is ready to receive a separate explicit
authorization for a completely new Take 3 vector. At the time this correction-gate entry was written,
no Take 3 seed generation had been authorized or begun; the later authorization and freeze are
recorded above.

Goldfish Sample #1 is formally accepted at
`4ccd4f097ade866a8eb3eff42e897229cd34e29f` and its vector is permanently retired and
hard-disabled. Pest Control v1.0 remains exact; the challenger remains audit-only and unconstructed.

An independent 30-seed Sample #2 vector was generated after a read-only audit of all 28 available
refs and 1,500 normalized seed-like values. It has zero overlap with every visible Pest Control,
Batshit, and Project X seed. The ordered vector was frozen before Game 1 with SHA-256
`1e4247fceaa9a7d2f438ab8fa29733782f624cbcde383ec858153de1367e522d`; the derivation and immutable
execution contract are preserved in
`docs/experiments/pest-control/goldfish-sample-2-seed-freeze.md`.

Sample #2 uses the exact Sample #1 deck, agent, engine, telemetry, mulligan behavior, horizon, and
acceptance criteria. Mana-role classification will be derived post-execution only from safely
observable bottleneck card names; it does not change telemetry or gameplay. No Sample #2 game had
been executed when this freeze was recorded.

### Goldfish Sample #2 disposition

Preflight CI run 213 was fully green at frozen head
`4dcf6f122bda469017611a9afdaf60c597e37d01`. All 30 seeds then executed exactly once in frozen order
without a reroll, replacement, exclusion, replay, or deck/policy/telemetry change. Automated
rules/state invariants reported zero errors, but manual review found blocking defects in Games 16 and
28:

- Game 16 cast Weather at Storm 0 before a legal land-plus-Carrier sequence that would have made
  Storm 1 and produced another Mascot trigger. The pre-Weather candidate field also failed to account
  for the legal land play, making this a telemetry defect as well as a policy failure.
- Game 28 activated Food at 21 life with no payoff or survival need and failed to use it for an
  available same-turn enhanced Follow; on the next turn it cast Follow normally despite an executable
  land, Weather, enhanced Follow, and Carrier line.

Therefore Sample #2 is **formally rejected**. Its vector is permanently retired and hard-disabled.
The raw block, human report, and rejection audit are preserved under
`docs/experiments/pest-control/goldfish-sample-2-*`. No Sample #2 performance inference, mana-role
aggregate, or pooled 60-game report was produced. No correction, replacement sample, Sample #3,
challenger construction, optimization, or opponent self-play was started. Pest Control v1.0 remains
exact.

### Game 16/Game 28 sequencing correction

The Sample #2 rejection is formally accepted at remote head
`8b4e4d04ab4d8d9701795554b447f049137c9fa1`. Its complete 30-seed vector is permanently retired and
hard-disabled: it may never be executed, replayed, rehabilitated, replaced, sampled, optimized
against, used for performance inference, or used for variant comparison. Sample #1 remains the only
accepted Pest Control performance/engine sample.

Deterministic reconstructions from the preserved Game 16 and Game 28 traces exposed two related,
general one-ply planning gaps:

- Game 16: Storm setup inspection considered only spells executable before the land drop. The legal
  land action, the productive spell it unlocked, the resulting Storm count/payoff events, and the
  remaining mana were therefore absent from both policy comparison and telemetry.
- Game 28: a pure-lifegain resource could receive value for making a temporary
  life-gained-this-turn condition true without committing to consume that condition in the same
  turn. A combat shortcut or the next greedy decision could strand the paid-for condition, and the
  planner could not see a land-unlocked resource-to-enhanced-consumer-to-follow-up line.

The correction adds bounded, simulator-backed same-turn comparison for two general action shapes:

1. legal land play, materially productive spell, then a still-payable Storm spell; and
2. legal land play where needed, pure condition-establishing action, materially enhanced consumer,
   then an optional productive follow-up.

The planner compares each completed line with the immediately available alternative, refuses setup
whose cost outweighs its Storm/payoff benefit, preserves immediate survival actions, and does not
count strategically null spells as setup. Once a resource is spent solely to establish an expiring
condition, the proven consumer is retained across stack resolution and selected during the valid
turn window. The condition enabler is held when the enhanced mode has no material value or cannot be
consumed before expiry. This uses action types, structural effect intents, stack/turn state, actual
mana availability, and simulated state value; it contains no Pest Control, Game 16/Game 28, Weather,
Carrier Thrall, Swamp, Food, Follow, or Mascot name heuristic.

Pest-owned pre-Weather telemetry now distinguishes spells executable before the focal cast from
spells unlocked by a legal land play. It records the best fully validated setup sequence and flags a
focal cast made before a materially superior sequence. This replaces the earlier hand/mana estimate
that could not see land-unlocked actions.

Deterministic regressions prove:

- the Game 16 shape selects land, productive spell, then Storm and observes Storm 1;
- survival still selects immediate lifegain;
- a land drop does not justify a strategically null Storm-building spell;
- an expensive setup is rejected when its cost exceeds the extra Storm/payoff value;
- a profitable pure-lifegain-to-enhanced-consumer line is completed in the same turn;
- the enabler is held when the temporary condition would expire unused or adds no material value;
- the Game 28 shape completes land, lifegain, enhanced consumer, then the useful follow-up; and
- telemetry separately reports current setup, land-unlocked setup, and the superior completed line.

**SHARED ARGENTUM CHANGE: yes.** The planner correction is general. The telemetry change is confined
to Pest-owned observation/reporting and does not alter gameplay semantics. Focused Pest agent tests
(64), the full AI suite (649 tests; 11 existing skips), Pest telemetry regressions (12), and the
frozen-deck/vector guard are green locally. Every retired Pest execution test remained skipped; no
Pest Control seed was executed. The complete project compilation and Gym trainer suite are also
green. As previously documented, the monolithic offline `test` graph is blocked during dependency
resolution because this container lacks cached Byte Buddy 1.10.9 and kotlinx-serialization-core
1.9.0 artifacts for `:mtg-search:test`; no production or test-semantic accommodation was made.
Authoritative remote full-CI validation is recorded here after the implementation commit is pushed.

The permanent Pest Control v1.0 deck remains exact. The challenger remains audit-only and
unconstructed. No replacement vector, optimization, or opponent self-play has begun. The laboratory
stops at the fresh Sample #1 seed-readiness gate.

## New untouched Goldfish Sample #1 authorization

The Game 8 pending-effect/redundant-resource correction is accepted as green at remote head
`aff4a349390d3c8ac4dfcbd3479fe69d56c481da`. **SHARED ARGENTUM CHANGE: yes.** The immutable Pest
Control v1.0 list remains exact, and the challenger remains audit-only and unconstructed.

Every earlier Pest Control goldfish vector and execution entry point remains permanently retired and
hard-disabled. A new 30-seed deterministic vector was generated only after a read-only collision audit
of all 28 available local/remote refs and 1,469 normalized numeric seed-like values. It has zero
overlap with the audit set and every prior Pest vector. The ordered vector was frozen before Game 1
with SHA-256 `f7012b5807621453692699055c90037bcf44526b4bc59edb37205c221aff9365`;
its derivation and immutable execution contract are preserved in
`docs/experiments/pest-control/goldfish-sample-1-untouched-seed-freeze.md`.

Pest-only observation telemetry was finalized before execution to snapshot payoff state, Follow
availability, survival pressure, pending stack sources, mana-plausible useful pre-Weather spells,
and pure-lifegain activations at decision time. This does not change engine, Gym production, agent
policy, cards, deck composition, mulligans, or gameplay semantics. No game had been executed when
this freeze was recorded.

### New untouched Goldfish Sample #1 result

Preflight CI run 211 was fully green at frozen remote head
`4e47607c435e280a4531b1d977fb176955bc8742`. The 30 frozen seeds then ran exactly once in order with
no reroll, replacement, exclusion, replay, or deck/policy/telemetry change. All automated invariants
reported zero errors, and manual review of every Weather, Food, Follow, removal, Thrall/Scion, Ent,
mana-bottleneck, and terminal decision found no clear defect.

The sample is **formally accepted as Pest Control v1.0 development/engine goldfish performance
evidence**, not matchup evidence. Complete raw JSON, the human-readable per-game report, and the
acceptance audit are preserved under `docs/experiments/pest-control/goldfish-sample-1-untouched-*`.
The vector is now hard-disabled and no Sample #2, challenger construction, optimization, or opponent
self-play was started. Pest Control v1.0 remains byte-for-byte exact.

## Laboratory boundary

This document and `docs/experiments/pest-control/` belong exclusively to Project Pest Control.
Batshit Economics/Affinity, `mayhem/project-x`, and their seed vectors and experiment protocols are
read-only dependencies and are outside this laboratory's write and workflow scope. Only explicitly
authorized Pest Control vectors have been executed: the permanently retired regression vector and the
separately frozen fresh Sample #1 vector described below. No challenger construction, opponent
self-play, optimization, or Sample #2 was performed.

## Fresh Goldfish Sample #1 disposition

The accepted regression gate is `27b82421d34cf2e61b7eb106b59ed2809bb50013`. Before fresh execution,
30 new deterministic seeds were derived and frozen with vector SHA-256
`50d831076ff08c5df70aaa21e6edf7deaf9c269eeb74f0b5f9981b8be51caad8`. A read-only audit of all 27
available remote refs inspected 50,553 seed-related lines/files and excluded 1,711 numeric seed values;
the new vector had zero overlap. The exact deck assertion, seed checksum, focused Pest tests, and full
CI run 200 were green at preflight head `1ecb976cb41c369edf195681e4c9d80ddf7e894f`.

The vector then ran exactly once, in order, with no rerolls, replacements, exclusions, substitutions,
deck changes, policy changes, or telemetry changes. All 30 engine games completed and the automatic
audit reported no rules/state, mana-provenance, trigger/counter, Storm-copy, Thrall/Scion, mana-legality,
or terminal inconsistency. Manual agent-sanity audit nevertheless found a clear policy defect:

- Games 1, 21, and 23 cast Weather the Storm at Storm 0 with no Researcher/Mascot payoff, no opposing
  pressure, and no survival need. Game 21 did so at 35 life.
- Game 22 cast two Weather copies (Storm 0 then Storm 1) with no payoff or opposing pressure.
- These casts only increased life in a blank-opponent environment. They violate the validated general
  requirements not to maximize life automatically or spend Weather merely because it is executable.

Therefore the entire fresh Sample #1 is **formally rejected**. None of its win clock, engine rates, or
other aggregates are accepted as Pest Control baseline, matchup, optimization, or variant-comparison
evidence. All 30 games are preserved intact in
`docs/experiments/pest-control/goldfish-sample-1-fresh-rejected.{md,json}`; no affected game was removed,
rerun, or replaced. The subsequent approved correction does not authorize replay, a new vector,
Sample #2, challenger construction, optimization, or opponent self-play.

## Fresh Sample #1 Weather-policy correction

The rejected games exposed a general one-ply valuation gap: raw life increased the board score even
when a pure lifegain spell had no opposing pressure, visible life-event payoff, or executable enhanced
follow-up. The correction classifies pure lifegain structurally from the resolving effect tree and
floors such legal-but-null casts below passing. It preserves casting when life is needed against
visible lethal pressure, when a permanent listens to its controller's lifegain events, or when the
event makes an affordable life-gained-this-turn spell meaningfully better. Spells with any concrete
non-life rider are excluded from the hold policy.

Payoff recognition is likewise structural: repeatable permanents triggered by their controller
gaining life receive a general intent tag. This covers Blood Researcher, Pest Mascot, and Marauding
Blight-Priest despite their different results, and does not tag Essence Warden merely because it
produces life. No Pest Control or card-name heuristic was introduced.

Focused deterministic coverage proves:

- pure lifegain is held with no pressure/payoff, including low-life and nonzero-Storm states;
- emergency Weather remains cast under visible lethal pressure;
- Weather remains cast for a visible Researcher/Mascot/Blight-Priest event payoff;
- lifegain may precede an executable enhanced Follow the Lumarets;
- Pulse of Murasa remains usable for its concrete recursion rider; and
- the existing Storm sequencing, independent-event, and deterministic-lethal cases remain green.

**SHARED ARGENTUM CHANGE: yes.** This is a general effect/result and trigger-property policy. The
permanent v1.0 list, mulligan policy, Weather rules, Follow rules, telemetry, engine, Gym, and card
definitions are unchanged. The rejected fresh vector has not been replayed, replaced, or used for
performance inference during the correction. Its one authorized regression replay is documented
below; any new sample requires separate authorization.

Focused validation passed all 46 Pest Control agent decisions plus the full structural-intent
analyzer suite. The full local AI suite is green. The frozen-deck/vector test passed with all three
opt-in goldfish executions skipped. The monolithic offline local `test` entry point remains blocked
before execution by the previously documented missing Byte Buddy 1.10.9 and
kotlinx-serialization-core 1.9.0 artifacts; no accommodation was made. Remote CI run 202 is fully
green on implementation head `6c13b8fd100040d60a0a0b33b66d7b484281e9d7`, including frontend,
engine, every scenario partition, content, tools, server, and the aggregate backend gate. The named
Argentum Validation workflow remains unavailable on this non-main branch without manual dispatch.

## Corrected fresh-vector regression gate

After explicit authorization, the exact rejected fresh Sample #1 vector was replayed once from
accepted Weather-policy head `1528ad2906e2b8e85b50f9672efb964622739f71`. All 30 seeds ran once,
unchanged and in their original CSV order. The vector checksum remained
`50d831076ff08c5df70aaa21e6edf7deaf9c269eeb74f0b5f9981b8be51caad8`.

All automated invariants and the manual suspicious-decision audit are clean. Every Weather had a
visible Researcher/Mascot payoff or enabled enhanced Follow; all Storm copies and separate life events
matched; the original null Weather behavior in Games 1, 21, 22, and 23 did not recur. Chainer's Edict
and Bone Shards remained held against the empty opponent, Thrall/Scion and Warden event counts matched,
Researcher/Mascot triggers equaled counters, Scion provenance balanced, actionable bottlenecks were
relevant and deduplicated, Follow modes and Ent decisions were legal, and every game had clean mana
and terminal reporting. The detailed cast-by-cast audit is preserved in
`docs/experiments/pest-control/goldfish-sample-1-fresh-regression-replay-accepted.md`.

The corrected replay is **accepted only as regression-validation evidence**. The original fresh
Goldfish Sample #1 remains permanently rejected as performance/baseline evidence, and neither run's
aggregates may support performance or variant inference. The exact vector is permanently retired and
must never be executed again, optimized against, sampled, or compared. No replacement Sample #1 was
generated.

**SHARED ARGENTUM CHANGE: yes.** This records the already accepted general Weather-policy correction;
the replay introduced no new policy, engine, Gym, telemetry, card, or deck change.

## Fresh performance Sample #1 authorization and freeze

The corrected same-seed Weather replay is formally accepted as clean at remote head
`8a13f2d8f9322b85ba6c52de571c591fea966a31`. **SHARED ARGENTUM CHANGE: yes.** Its exact seed vector
is permanently retired and must never be reused for performance, optimization, variant comparison,
or sampling.

A new, separately identified 30-seed performance vector was derived and frozen before execution.
Its SHA-256 is `c674ee12b4a3ebce6584d8d2c0c285a57f2400519e99fd08be058d76c3ad7513`.
A read-only audit of 28 local/remote refs inspected 135,029 seed-related lines/file contents, 1,019
seed-path instances, and 2,495 distinct numeric values; overlap was zero. The vector, derivation,
scope, and immutable execution contract are preserved in
`docs/experiments/pest-control/goldfish-sample-1-performance-seed-freeze.md`.

Before freezing, Pest-owned report telemetry was extended to expose opening-color, counter,
all-three-coexistence, Weather/payoff, and counterfactual-Warden totals. The counterfactual now counts
only creature entries while Researcher/Mascot is present and no Essence Warden is present, and
reports potential Researcher and Mascot counters separately. This is descriptive test telemetry
only; no engine, production agent, deck, mulligan, card, Weather, Follow, or gameplay policy changed.
Execution remained blocked until the frozen vector and telemetry passed preflight validation.

### Fresh performance Sample #1 result

Preflight CI run 205 was fully green at `9f3f3c1cc8270c3bbd55b8017c0935dbbee7771f`. The frozen vector
then executed exactly once, in order, without a reroll, replacement, exclusion, substitution, or
deck/policy/telemetry change. All 30 games completed and all automated rules/state, Storm-copy,
lifegain-event, counter, Thrall/Scion, provenance, mana-legality, and terminal invariants passed.

Manual audit found a clear residual agent-policy defect in Game 15: the agent cast Weather at Storm 0
and 21 life with no payoff, survival pressure, or same-turn enhanced Follow, then spent Food for more
irrelevant life. On turn 6 it had another Weather, Follow, and four lands but cast normal Follow
without first enabling the enhanced mode. The entire sample is therefore **rejected** and none of its
clock or engine aggregates are accepted as baseline, optimization, matchup, or variant evidence.

The complete raw JSON and human report are preserved as
`docs/experiments/pest-control/goldfish-sample-1-performance-rejected.{json,md}`. The manual audit and
descriptive rejected-run metrics are in
`docs/experiments/pest-control/goldfish-sample-1-performance-audit-rejected.md`. No game was removed
or replayed, and no correction was made. A separate approval is required before investigation or any
same-seed validation. The permanent v1.0 list remains exact and the challenger remains unconstructed.

Descriptive rejected-run facts, retained for diagnosis only: 9/30 games mulliganed (11 total);
meaningful deployment reached 6/14/27 games by T1/T2/T3; all 30 modeled terminals were combat lethal
(median T7; 0/2/9/21 by T4/T5/T6/T7); 140 separate lifegain events gained 250 life; Researcher and
Mascot recorded 60/60 and 77/77 trigger/counter totals; 27 Weather casts had exact Storm-copy counts
(`0`: 12, `1`: 15); four Thrall deaths created four Scions; no Scion was sacrificed for mana; and the
functional-state classifier reported 26 engine-functional and four fair-creature-functional games.

The rejection is formally accepted at remote head
`23da31d777e3e1cd45f9655295565f2fd1a59662`. The complete 30-seed vector (SHA-256
`c674ee12b4a3ebce6584d8d2c0c285a57f2400519e99fd08be058d76c3ad7513`) is permanently
disqualified from performance/baseline evidence. It may not be used for sampling, optimization,
performance inference, or variant comparison. No game was rerun, removed, replaced, or repaired,
and no replacement vector was generated.

### Game 15 lifegain-resource correction

The deterministic reproduction separated Game 15's three linked decisions without executing its
retired seed: a high-life pure lifegain spell with no survival need or event payoff; a subsequent
pure lifegain activation after life had already been gained; and an executable life-event into an
enhanced life-gained-this-turn follow-up.

The complete root cause was a scope mismatch in the general null-lifegain policy. Pure lifegain
spells were classified structurally and held unless survival, a visible repeatable payoff, or an
affordable enhanced follow-up made the event concrete. Printed activated abilities bypassed that
classification entirely, so sacrificing a Food for three strategically irrelevant life points
could outrank passing and interrupt the already-enabled Follow line. The next greedy decision then
saw the enhanced line only after the enabling event had been wasted; Game 15 later took normal
Follow instead of the independently available materially superior Weather-to-enhanced-Follow line.

The correction adds a strict structural pure-lifegain classifier for printed activated abilities
and routes both casts and activations through the same general utility gate. A pure lifegain
resource is held below passing when it has no survival role, visible repeatable event payoff, or
executable enhanced follow-up. Abilities and spells with another effect leaf remain outside this
hold rule. Existing general sequencing continues to prefer an enabling life event followed by the
enhanced spell when that two-action line is executable, while normal Follow remains correct when
the enabler cannot profitably precede it. The survival, payoff-driven Storm-0, useful Storm setup,
and anti-junk-Storm cases remain green.

**SHARED ARGENTUM CHANGE: yes.** This is a general effect/resource policy correction in the AI
intent catalog and strategist. No card-name, Game 15, or Pest Control heuristic was added. No rules,
engine, Gym, telemetry, mulligan, deck, Weather, Follow, or card-definition behavior changed.

Focused deterministic coverage is green for the four new Game 15 chain scenarios and the existing
high-life hold, survival Weather, payoff-driven Weather, useful Storm setup, and anti-junk-Storm
regressions. The structural intent suite and complete AI test suite are green locally. The rejected
vector has not been replayed, and a same-seed regression replay is ready only for separate explicit
authorization after full CI is green.

### Game 15 same-seed regression replay

The separately authorized replay executed all 30 performance-sample seeds exactly once, unchanged
and in their original order, from corrected-policy remote head
`132ffbc83473c950a74cff7800f9e8085a7c6e50`. No reroll, replacement, exclusion, deck change, seed
change, telemetry change, or policy change occurred during execution. The first opt-in command was
reported `UP-TO-DATE` by Gradle and executed no games; the subsequent forced task was the sole actual
replay execution.

Game 15's original chain is corrected. Its turn-five Storm-0 Weather at high life now has the
concrete purpose of enabling Follow the Lumarets, which is cast immediately in enhanced mode. Food
is not activated, and the original null Weather/Food/later-normal-Follow sequence does not recur.

The replay is nevertheless **rejected in full**. Game 8 casts Weather at Storm 0 with no
Researcher/Mascot or survival need, then activates Food while Weather is still pending, and finally
casts enhanced Follow. The LIFO resolution order records Food's life event before Weather's. The
Food event has no payoff and cannot improve Follow beyond the enhancement already guaranteed by the
pending Weather, so it is a strategically null resource expenditure. The corrected policy observes
resolved life-gained-this-turn state but does not account for a guaranteed pending life event on the
stack when valuing another pure-lifegain activation.

All automated rules/state and telemetry invariants passed, and the remainder of the manual audit
found no additional clear defect. Every Weather copy and separate life event matched; all other
Weather and pure-lifegain decisions had a visible payoff, enabled Follow, or represented useful
Storm sequencing. Normal Follow decisions were either not profitably preceded by Weather or
preserved Weather for a later payoff line. Edict and Bone Shards stayed unspent against the empty
opponent; Researcher/Mascot trigger-to-counter accounting, Thrall-to-Scion creation, Scion
provenance, actionable bottleneck deduplication, Ent decisions, mana legality, and terminal reports
were internally consistent.

The rejected original and rejected replay are preserved separately. Replay artifacts:

- `goldfish-sample-1-performance-regression-replay-rejected.json`
- `goldfish-sample-1-performance-regression-replay-rejected.md`
- `goldfish-sample-1-performance-regression-replay-audit-rejected.md`

The vector is now permanently retired from any further execution. It remains disqualified from
sampling, optimization, performance inference, baseline evidence, and variant comparison. This
replay does not make the laboratory ready for a new Sample #1 vector; further correction or
execution requires separate authorization.

### Game 8 pending-effect / redundant-resource correction

The corrected same-seed replay is formally preserved and rejected at remote head
`538666b012fe9577ecb8e261c95e1357f43767e3`. The original Sample #1 and replay remain separate
rejected artifacts. Their 30-seed vector is permanently retired from all further execution; the
correction did not execute it or any other Pest Control seed. The opt-in performance runner is now
unconditionally disabled to enforce that disposition.

The residual root cause was generic pending-condition accounting. The pure-lifegain resource gate
saw only resolved life-gained-this-turn state, so Food was credited as the enhanced-Follow enabler
even though a controlled Weather already pending on the stack guaranteed that condition. The Food
event added no survival, repeatable-payoff, threshold, or additional-line value.

The general intent catalog now recognizes only fixed, unconditional, controller-directed lifegain
on stack objects as guaranteed. The strategist includes that pending amount in survival and
follow-up-unlock valuation, while conservatively declining certainty for conditional, optional,
dynamic, targeted, mixed, counter-exposed, or hidden-response-exposed effects. Additional lifegain
remains usable when pending life is insufficient for survival or a repeatable payoff values each
event. Non-lifegain resource actions remain outside the suppression rule.

**SHARED ARGENTUM CHANGE: yes.** Only generic AI intent/action valuation changed. Rules, engine,
Gym behavior, telemetry, cards, the permanent control, and the challenger are unchanged.

Six focused regressions cover redundant pending lifegain, insufficient survival gain, additional
repeatable-payoff value, no-pending Follow enablement, disruptable pending effects, and an
independently valuable non-lifegain action. The complete deterministic Game 8 reconstruction now
passes priority with Food intact, resolves Weather, and casts enhanced Follow while preserving the
resource. The full Pest decision suite, complete AI suite, frozen-control assertion, and Pest
telemetry regressions are green locally. All opt-in goldfish cases were skipped. Full remote CI is
green in run 209 on implementation head `cbff8feaa80489fc3f2d37b0e9d92f57f93346cf`, including
frontend, engine, every scenario partition, content, tools, server, and the aggregate backend gate.
The named Argentum Validation workflow is main/manual-only and is not dispatchable on this branch
through the available connection; run 209 executes its compile/test/Gym coverage and the broader CI
matrix. The laboratory stops at fresh Sample #1 seed-readiness. No new vector is authorized or
generated by this correction.

## Rejected Sample #1 disposition and correction scope

Goldfish Sample #1 is formally and permanently rejected as performance, baseline, matchup, or
optimization evidence. Its exact 30-seed vector (SHA-256
`87d8624e407286bfa9b1c9d4629fd29163ae8bbec4b98ab51ace7f9c9e1d764f`) is frozen solely as a
regression vector. It must never be used to tune the deck or agent, select changes, or infer future
Pest Control performance.

Three narrowly scoped general corrections are implemented and validated:

- pure opponent-directed forced-sacrifice spells are held when their resolved result sacrifices no
  opposing permanent;
- sacrifice-for-mana permanents require a newly executable follow-up whose resolved state is
  strategically productive, and generic provenance records source, activation, production,
  consumption, funded action, and unused mana; and
- actionable mana telemetry waits until legal land play has been considered, uses the authoritative
  mana solver (including sacrifice sources), separates total/color/tapland constraints, excludes
  strategically irrelevant interaction, and deduplicates a card's unresolved constraint.

**SHARED ARGENTUM CHANGE: yes.** These are generic agent-policy and Gym telemetry corrections. No
Pest Control card-name heuristic or deck change is involved.

Game 25 provenance was resolved before the correction: its Scion was sacrificed on turn 13,
produced one colorless mana, consumed zero mana, funded no action, and expired with one unused mana.
The corrected isolated reproduction preserves the Scion and reports no activation or provenance
error.

## Correction replay audit and final regression gate

Two exact-order replay attempts used the same frozen vector, without rerolls, replacements,
exclusions, deck changes, or seed substitutions. Both are invalid regression evidence and none of
their performance or engine aggregates may be used:

1. The first attempt exposed opponent-dependent removal being counted as actionable against the
   blank opponent merely because it controlled a land.
2. After that relevance fix and green CI run 195, the second attempt held Chainer's Edict in games
   9, 13, 19, and 27 and preserved the Game 25 Scion, but manual audit found 21 false Bone Shards
   bottlenecks. Bone Shards' nonmodal `ModalEffect` cost fork is intentionally opaque to the
   historical whole-card intent scorer, so telemetry had failed to inspect its removal modes.

The follow-up correction reads every mode structurally, uses the authoritative target finder to
require a legal opposing permanent, and leaves historical card scoring unchanged. Ten focused Gym
telemetry regressions now cover both the null and valid-target modal-removal cases, along with the
requested land, total-mana, color, tapland, Scion-mana, provenance, and deduplication cases. CI run
196 is fully green on remote correction head `c7f84ad8799b21a5b651492073273d0682e48465`.

After explicit renewed authorization, the final replay ran all 30 seeds unchanged and in their
original order from the fully green correction head. Every game passed the harness audit and the
manual cross-check found no rules/state, telemetry, mana-legality, terminal, or agent-sanity error:

- Chainer's Edict remained held in games 9, 13, 19, and 27;
- Game 25 created one Scion and preserved it, with no mana activation or provenance record;
- no opponent-dependent interaction appeared as an actionable mana bottleneck and no bottleneck
  key was counted twice;
- every Weather copy count matched its Storm count and every original/copy produced its own
  three-life event;
- Researcher and Mascot trigger totals exactly matched their counters added, including independent
  fan-out when multiple payoff permanents were present;
- every Carrier Thrall death created exactly one Scion, with all Scion provenance balanced;
- Follow used only its engine-reported normal or enhanced mode, Ent actions were legal, all life
  events had a positive amount and known source, and every terminal was engine-reported; and
- the replay contained all 30 distinct frozen seeds in the exact CSV order.

The corrected replay is accepted solely as regression-validation evidence. Goldfish Sample #1
remains permanently rejected for performance, baseline, matchup, and optimization purposes. The
30-seed vector is now permanently retired and must never be executed again or used for optimization
or future performance inference. The laboratory is ready for a separately authorized fresh Sample
#1, but no fresh seeds or games were generated automatically.

## Goldfish Sample #1 original audit result

The exact frozen 30-seed vector was executed once, in order, without rerolls, replacements,
exclusions, deck changes, policy changes, or seed substitutions. The run completed all 30 games,
but **Sample #1 is rejected and is not a Pest Control performance/engine baseline**.

Three independent audit blockers were found:

1. The solitaire agent cast Chainer's Edict into an empty opposing battlefield in games 9, 13, 19,
   and 27. This is a genuine general agent-policy defect, not ordinary interaction stranded in hand.
2. In game 25, a Carrier Thrall Scion was sacrificed for mana, but no spell recorded that Scion as
   a mana source. This is an unresolved agent-sanity or mana-provenance telemetry defect.
3. The generated “genuine mana bottleneck” metric samples pre-land-drop and repeated priority
   states. It therefore reports false positives (including missing green with a Forest in the kept
   hand) and cannot support the requested bottleneck aggregate.

The rules/state portions that can be audited from the run were internally consistent: 142 separate
life-gain events gained 234 life; Researcher recorded 41 triggers and 41 counters; Mascot recorded
71 triggers and 71 counters; all 23 Weather casts had the expected Storm-copy count; four Carrier
Thrall deaths created four Scions; all 30 terminals were engine-reported combat lethal. These facts
do not cure the invalidating defects above.

The complete rejected-run record is preserved under `docs/experiments/pest-control/`. The vector is
not a performance sample; only the explicitly authorized correction replay may execute it again.
The permanent control remains exact and the challenger remains audit-only and unconstructed.

## Phase 3 deterministic agent-validation result

Thirty-seven deterministic positions now validate the requested Pest Control strategy surface
through the live production-candidate agent and rules engine. They are board-state probes, not deck
construction, seeds, goldfish games, matchup samples, or optimization data.

Existing generic policy passed unchanged for supported Blood Researcher/Pest Mascot deployment,
survival-first Weather the Storm, refusing irrelevant Storm inflation, Blight-Priest drain lethal,
Carrier Thrall blocking, all four Bone Shards sacrifice/discard comparisons, removal target value,
hexproof/ward use of Chainer's Edict, flashback, removal patience, ordinary-cost Snuff Out at low
life, race-sensitive Snuff Out, productive Carrier Thrall death, Follow the Lumarets setup and
immediate normal-mode use, late-game Ent/Wildling casting, non-lifegain winning lines, and general
pending-lethal recognition.

The deterministic coverage also demonstrated genuine general gaps, corrected without card-name
heuristics:

- positive +1/+1-counter effects now advertise a lasting `PUMP` payoff to structural intent;
- life-gain-enhanced selection and basic-land tutor faces have explicit structural intent tags;
- land typecycling is recognized from the cycling mechanic's search metadata, independently from
  an Omen face;
- useful pre-Storm spells receive bounded option value for the additional event and visible
  repeatable counter/drain payoffs, while a spell that is already worse than passing receives none;
- a non-mana alternative cost receives bounded tempo value only when the preserved mana makes a
  relevant follow-up executable;
- early land-tutor/typecycle actions receive bounded development value, while feasible late-game
  bodies retain their normal board value;
- sacrifice-for-mana actions are considered only when their resulting mana unlocks an executable
  spell, and are not used when the spell is already executable or no productive use exists;
- targeted protection is held until opposing targeting or committed combat creates a credible
  window; and
- `SacrificeSelf` mana abilities now expose their irreversible payment in generic legal-action
  metadata, making the existing meaningful-action policy usable for Scions, Spawn, Treasure-like
  resources, and future equivalents.

**SHARED ARGENTUM CHANGE: yes.** The changes above are general intent, sequencing, protection-window,
and legal-action metadata corrections. No Pest Control card-name heuristic, Gym change, seed vector,
or opponent policy was added. No requested strategic behavior remains unresolved at this gate.

The frozen Pest Control v1.0 main deck and sideboard blocks below remain byte-for-byte identical to
the accepted rules-complete head. The proposed Tier-1 challenger remains audit-only and has not been
constructed.

## Phase 2 rules-complete result

The approved shared addition is complete: `PredefinedTokens.EldraziScion` is the authoritative,
reusable 1/1 colorless Creature — Eldrazi Scion definition with the mana ability “Sacrifice this
creature: Add `{C}`.” `Effects.CreateEldraziScion` provides fixed- and dynamic-count creation
facades. Generic SDK and rules-engine tests pin its characteristics, colorlessness, creature types,
cost payment, zone departure, ordinary use of the resulting mana, and visibility of both creature-
entry and death events to normal triggers.

The eight previously missing cards are individually implemented and registered:

| Card | Implemented rules | Focused proof |
|---|---|---|
| Carrier Thrall | Dies trigger creates exactly one predefined Eldrazi Scion | Direct death/token scenario plus Pest engine integration |
| Bone Shards | Choose sacrifice or discard as an additional cost; destroy target creature or planeswalker | Both additional-cost branches |
| Nature's Claim | Destroy target artifact or enchantment; its controller gains 4 | Target removal and controller life gain |
| Snuff Out | Conditional `{0}` alternative cost while controlling a Swamp, pay 4 life, nonblack-creature restriction, no regeneration | Alternative-cost cast and life payment |
| Suffocating Fumes | Opposing creatures get -1/-1 until end of turn; cycling `{2}` | One-sided characteristic change; cycling supplied by the shared primitive |
| Pulse of Murasa | Return target creature or land card from a graveyard to its owner's hand; gain 6 | Graveyard return and life gain |
| Nihil Spellbomb | Tap/sacrifice to exile target player's graveyard; battlefield-to-graveyard `{B}` may-pay draw trigger | Graveyard exile, sacrifice, optional mana payment, and draw |
| Masked Vandal | Changeling; ETB may exile a creature card from your graveyard to exile an opponent's artifact/enchantment | Targeting, optional graveyard payment, both exile movements |

The generated/approximate markers were removed only after human review of Chainer's Edict,
Marauding Blight-Priest, and Unearth. Chainer's Edict now uses canonical target-player sacrifice
wording and its flashback path is directly tested. Marauding Blight-Priest's one trigger per life-
gain event is directly tested. Unearth's existing return scenario remains green after review.

Sagu Wildling was corrected from an Adventure approximation to the existing Omen primitive. Its
Roost Seek face now searches for a basic land and shuffles the card into its owner's library. It is
not modeled as landcycling. Generous Ent's actual Forestcycling is directly tested.

## Deterministic Pest Control interactions

The rules suite now proves:

1. Essence Warden creates one life-gain event for each qualifying creature entry, including an
   opponent's entry.
2. Bogwater Lumaret creates life-gain events only for qualifying entries under its controller.
3. Blood Researcher receives exactly one +1/+1 counter per separate life-gain event.
4. Pest Mascot follows its implemented Oracle text and receives one +1/+1 counter per separate
   life-gain event.
5. Multiple Wardens and Lumarets create independent triggers, not an aggregated gain.
6. Weather the Storm's original and Storm copies resolve into separate gain-3 events; each produces
   independent Researcher, Mascot, and Blight-Priest triggers.
7. Marauding Blight-Priest produces one opponent-life-loss trigger per qualifying gain event.
8. Carrier Thrall creates exactly one Scion on death, and that Scion's entry participates normally
   in the Warden/Lumaret engine.
9. Sacrificing the Scion removes it, produces usable `{C}`, and creates no false life-gain event.
10. Follow the Lumarets' existing direct scenario distinguishes its normal maximum-one behavior from
    its life-gained-this-turn maximum-two behavior.
11. Bone Shards separately validates sacrifice and discard additional costs.
12. Chainer's Edict validates sacrifice and flashback, including exile after flashback resolution.
13. Snuff Out validates its alternative cost and four-life payment.
14. Generous Ent validates actual Forestcycling.
15. Sagu Wildling validates actual Roost Seek/Omen resolution and shuffle-back behavior.

No new production rules-engine primitive was needed beyond the approved reusable Scion token and
creation facade. No Gym or agent-policy behavior was changed.

## Validation record

- Focused Scion SDK/rules tests: green.
- Focused card and Pest interaction scenarios: green.
- Card-definition golden snapshots: regenerated through the authoritative exporter and green,
  including JSON round trips.
- Full CI: run 188 on accepted remote head `b08d8fad6e6709db4834e8e956e6853152efb3b5`
  is the authoritative green validation for the rules-complete gate.
- Argentum Validation workflow-equivalent local run: compilation completed; all Pest-relevant,
  engine, AI, and Gym tests reached in the run were green. The monolithic `test` task was ultimately
  red only because this container forbids Byte Buddy's dynamic self-attachment used by unrelated
  server mocking tests; the same server suite is green in CI run 187. A separate local `:gym:test`
  completed green. The GitHub connection available to this laboratory does not expose
  `workflow_dispatch`, so the named workflow itself could not be dispatched on this non-main branch.
- Phase 3 focused validation: 37 Pest agent decisions, the full structural-intent analyzer suite,
  and the generic mana-ability enumerator suite are green.
- Phase 3 full local engine and AI suites: green. The monolithic offline `test` entry point cannot
  construct `:mtg-search:testRuntimeClasspath` in this container because its cache lacks Byte Buddy
  1.10.9 and kotlinx-serialization-core 1.9.0. Separately, this container forbids Byte Buddy dynamic
  self-attachment in the unrelated server mocking tests. Neither environment limitation is being
  accommodated by production changes or altered test semantics.
- Phase 3 full CI: run 189 on implementation commit
  `7ba1c8568ca68e64c6e0230fa90cad7d560a9417` is the authoritative green full-matrix gate. Frontend,
  engine, all scenario partitions, tools, server, content, and the aggregate backend job passed.
- Pest correction focused validation: all 41 deterministic agent decisions and all 10 actionable
  mana/provenance telemetry regressions are green.
- Pest correction full CI: run 196 on remote head
  `c7f84ad8799b21a5b651492073273d0682e48465` is fully green across frontend, engine, all scenario
  partitions, content, tools, server, and the aggregate backend gate.
- The named Argentum Validation workflow cannot be dispatched on this non-main branch through the
  available GitHub connection. Its exact local task graph is blocked before execution because this
  offline container lacks Byte Buddy 1.10.9 and kotlinx-serialization-core 1.9.0. No production code
  or test semantics were changed to accommodate that environment limitation; CI run 196 supplies
  the authoritative clean dependency environment and full backend coverage.

The laboratory stops at the fresh-sample-readiness gate. Challenger construction, new seeds,
gameplay sampling, matchup self-play, and optimization remain prohibited pending separate approval.

## 1. Branch and base

The dedicated branch `pest-control/lab` was created directly from
`47882cd645caf126afee6cf13a65909806fa40ab` (`Replay frozen Affinity vector on residual policy
candidate`). At inspection time this was the current `main` head and the latest head with completed,
successful CI, Argentum Validation, and Frozen Grixis Affinity Regression Replay checks. A later E2E
Nightly run on the same SHA was cancelled; it did not supersede or fail those completed validation
gates. The previously shared green head `004ccc27...` was eight commits behind this head.

## 2. Permanent Pest Control v1.0 control

The following list is frozen exactly. It must never be silently updated or replaced.

### Main deck — 60 cards

```text
4 Essence Warden
4 Carrier Thrall
4 Blood Researcher
4 Pest Mascot
4 Fierce Witchstalker
3 Generous Ent
4 Follow the Lumarets
4 Weather the Storm
4 Cast Down
2 Bone Shards
2 Chainer's Edict
10 Forest
7 Swamp
4 Jungle Hollow
```

Count verification: 23 creatures + 16 noncreature spells + 21 lands = 60.

### Sideboard — 15 cards

```text
4 Tamiyo's Safekeeping
3 Nature's Claim
3 Snuff Out
3 Suffocating Fumes
2 Pulse of Murasa
```

Count verification: 4 + 3 + 3 + 3 + 2 = 15.

## Proposed Tier-1 challenger (audit-only)

The proposed challenger also counts to 60 cards with a 15-card sideboard. It remains a candidate
architecture only. It has not been built, seeded, played, or approved as a replacement.

```text
4 Essence Warden
4 Bogwater Lumaret
4 Blood Researcher
4 Pest Mascot
3 Marauding Blight-Priest
3 Sagu Wildling
4 Follow the Lumarets
4 Unearth
3 Cast Down
3 Snuff Out
2 Tamiyo's Safekeeping
2 Weather the Storm
6 Forest
5 Swamp
4 Jungle Hollow
3 Illegitimate Business
2 Khalni Garden
```

```text
Sideboard
3 Duress
3 Nihil Spellbomb
3 Suffocating Fumes
2 Masked Vandal
2 Tamiyo's Safekeeping
2 Weather the Storm
```

## Phase 1 audit record (superseded)

Sections 3–7 below preserve the accepted Phase 1 audit as a historical record. Their “missing” and
“recommended” labels describe the pre-implementation state and are superseded by the Phase 2 result
and validation record above.

## 3. Card coverage

Registry legality means the repository legality registry contains `PAUPER`; it does not imply that a
production definition exists or is authoritative. Every one of the 28 unique names across both lists
(including Forest and Swamp) is Pauper-legal as represented by the registry. Cast Down and Chainer's
Edict have uncommon canonical source definitions but are Pauper-legal through common printings
represented by that registry.

| Card | Deck use | Definition | Scenario coverage | Audit result |
|---|---|---|---|---|
| Essence Warden | Both | Present | None direct | Hand-authored trigger watches every other creature entering under any player's control. Needs multiplayer-side and event-multiplicity tests. |
| Carrier Thrall | Control | **Missing** | None | Registry-legal. Dies-to-Eldrazi-Scion needs a new shared predefined token/facade; this is the one identified shared SDK vocabulary gap. |
| Blood Researcher | Both | Present | None direct | Hand-authored `YouGainLife` trigger adds one +1/+1 counter per trigger resolution. Separate-event interaction is unpinned. |
| Pest Mascot | Both | Present | None direct | Hand-authored 2/3 trample with the same per-lifegain counter trigger. It is not a Pest token. Hunt for Specimens tests the separate 1/1 Pest token's death lifegain only. |
| Fierce Witchstalker | Control | Present | None direct | Hand-authored ETB creates Food. Card-specific ETB/token coverage is absent. |
| Generous Ent | Control | Present | None direct | Hand-authored ETB creates Food and Forestcycling `{1}`. No direct ETB or typecycling scenario. |
| Follow the Lumarets | Both | Present | **Direct** | Direct scenario covers maximum one without prior lifegain and maximum two after lifegain, including hand/bottom movement. |
| Weather the Storm | Both | Present | None direct | Hand-authored Storm plus gain 3. Generic Storm copying exists, but no scenario pins each copy as a separate lifegain event or its trigger fan-out. |
| Cast Down | Both | Present | Gym smoke only | Hand-authored nonlegendary-creature target and destruction. No one-card scenario. |
| Bone Shards | Control | **Missing** | None | Registry-legal. Existing choice, sacrifice, discard, and destroy primitives appear sufficient; needs both additional-cost branches and target tests. |
| Chainer's Edict | Control | **Generated / approximate** | None direct | Generated definition composes forced sacrifice and flashback `{5}{B}{B}`. Requires human review and hand-authored normal/flashback scenarios. |
| Forest | Both | Built-in basic | Generic | Baseline land behavior is broadly covered. |
| Swamp | Both | Built-in basic | Generic | Baseline land behavior is broadly covered. |
| Jungle Hollow | Both | Present | Partial direct | Direct scenario pins entering tapped from hand and off-stack. ETB gain 1 and both mana abilities are not card-specifically pinned. |
| Tamiyo's Safekeeping | Both | Present | None direct | Hand-authored hexproof + indestructible until EOT, then gain 2. Needs response-window and lifegain-trigger scenarios. |
| Nature's Claim | Control | **Missing** | None | Registry-legal. Existing artifact/enchantment destruction and target-controller lifegain primitives appear sufficient. |
| Snuff Out | Both | **Missing** | None | Registry-legal. Existing conditional self-alternative-cost, pay-life, color filtering, and destruction primitives appear sufficient; needs normal/alternate path and AI life-payment tests. |
| Suffocating Fumes | Both | **Missing** | None | Registry-legal. Existing opponent-creature group modification and cycling primitives appear sufficient. |
| Pulse of Murasa | Control | **Missing** | None | Registry-legal. Existing graveyard target, return-to-hand, and gain-life primitives appear sufficient. |
| Bogwater Lumaret | Challenger | Present | **Direct** | Direct scenario covers its own ETB, another controlled creature's ETB, and the opponent-creature negative case. |
| Marauding Blight-Priest | Challenger | **Generated / approximate** | None direct | Generated definition drains each opponent once for each `YouGainLife` trigger. Requires human review and separate-event/fan-out tests. |
| Sagu Wildling | Challenger | Present | Partial direct | Direct scenario covers cast/ETB gain 3. Its Roost Seek Omen basic-land search and later creature recast are untested. This card has an Omen, not landcycling. |
| Unearth | Challenger | **Generated / approximate** | Partial direct | Direct scenario exercises a special prepared/Craft return plus agent target choice elsewhere. Ordinary mana-value ≤3, ownership, invalid-target, and cycling boundaries remain unpinned. |
| Illegitimate Business | Challenger | Present | None direct | Hand-authored enters tapped, gain 1, and black/green mana abilities. Needs land sequencing plus lifegain-trigger coverage. |
| Khalni Garden | Challenger | Present | None direct | Hand-authored enters tapped, creates a 0/1 Plant, and taps for green. Needs token/trigger and sequencing coverage. |
| Duress | Challenger SB | Present | None direct | Hand-authored reveal/select noncreature nonland/discard composition. Needs legal-choice and whiff scenarios. |
| Nihil Spellbomb | Challenger SB | **Missing** | None | Registry-legal. Existing targeted graveyard exile, tap/sacrifice, leaves-battlefield, optional mana payment, and draw primitives appear sufficient. |
| Masked Vandal | Challenger SB | **Missing** | None | Registry-legal. Changeling is implemented and tested generically. Existing graveyard exile and artifact/enchantment exile primitives appear sufficient, but the optional “if you do” ETB chain needs a focused composition proof. |

Summary: 18 named nonbasic cards have production definitions, 8 named nonbasic cards are missing,
and Forest/Swamp are built-ins. Three present definitions are explicitly generated/approximate and
require human review: Chainer's Edict, Marauding Blight-Priest, and Unearth.

## 4. Rules and test gaps

No new general rules-engine mechanic is presently proven necessary for the lifegain core. The engine
already emits a gain-life event per resolved gain-life effect; Storm copies are distinct stack
objects, so Weather the Storm should produce separately resolving gain-life effects. The required
proof is missing, however. Before any games, add deterministic scenarios that demonstrate:

1. One and multiple Essence Warden triggers, including opponent creature entry.
2. Blood Researcher and Pest Mascot receiving one counter for each separate lifegain event, and no
   extra counter merely for the amount of life gained.
3. Weather the Storm's original plus Storm copies resolving as separate lifegain events and producing
   the expected Warden/Researcher/Mascot/Blight-Priest trigger fan-out.
4. Follow the Lumarets before and after any earlier life-gain event in the turn.
5. Marauding Blight-Priest draining separately for separate events and each opponent where relevant.
6. Unearth's ordinary target boundaries, cycling, and resolution when the target becomes illegal.
7. Bone Shards' sacrifice and discard choices as additional costs, including nonrefundable costs on
   counter/illegal resolution and sensible automatic payment choices.
8. Chainer's Edict normal cast, opponent sacrifice choice, flashback permission/cost, and exile after
   flashback resolution.
9. Snuff Out's Swamp condition, black-creature exclusion, normal mana cost, four-life alternative
   cost, and low-life legality/agent behavior.
10. Generous Ent Forestcycling; Sagu Wildling separately through its actual Roost Seek Omen flow.
11. Jungle Hollow, Illegitimate Business, and Khalni Garden entering tapped, generating ETB life/token
    events, and supporting black/green sequencing.
12. Each sideboard card's legal targets, costs, zone movements, and negative cases.

## 5. Generic agent-policy coverage and gaps

| Concern | Existing generic coverage | Remaining gap before experiments |
|---|---|---|
| Lifegain-engine deployment | Simulation observes resulting life totals; gain-life/drain effects receive card-intent tags. | Creature permanents do not receive an explicit engine-synergy deployment prior. Add deterministic choice tests first; change policy only with separate approval. |
| +1/+1-counter payoff valuation | Board evaluation values current counters and projected power/toughness. | It does not explicitly value future counters from expected lifegain density. |
| Repeated/separate lifegain events | Rules simulation can expose each trigger and resulting state within its rollout. | No explicit multiplicity/combo policy or regression pins Warden/Researcher/Mascot/Blight-Priest fan-out. |
| Storm sequencing | Generic legal-action simulation can cast spells and Storm copies are engine-supported. | No Storm-count-aware hold/order policy or Weather-specific agent test was found. |
| Removal targeting | Removal/sweeper intent tags, target simulation, hold policy, and removal patience already exist. | Add Pest-list target regressions, especially Cast Down restrictions and Bone Shards payment choice. |
| Protection timing | Hexproof/indestructible/prevention intent tags and response-window hold logic already exist. | Add a Tamiyo's Safekeeping regression; do not assume its gain 2 is valued as engine synergy. |
| Recursion | Graveyard-to-battlefield movement is tagged as recursion; target simulation exists; an Unearth target-choice test exists. | Ordinary Unearth target bounds and timing/value choices need broader deterministic coverage. |
| Landcycling / land search | Cycling/typecycling actions are enumerated and evaluated; generic land sequencing exists. | No dedicated Forestcycling-versus-cast policy test. Sagu Wildling is an Omen and needs its own search/recast sequencing test. |
| Alternate-cost removal | Alternative costs and automatic pay-life/sacrifice/discard payments are enumerated. | No general life-preservation policy proves when Snuff Out should use mana versus four life; Bone Shards branch selection also needs proof. |
| Tapland mana sequencing | Generic usable-mana land ordering and board-presence land sequencing tests exist. | ETB lifegain/token synergy across the three Pest lands is not explicitly valued or pinned. |

## 6. Shared Argentum changes requiring approval

One shared SDK/mtg-sets vocabulary addition appears required to implement Carrier Thrall faithfully:
an authoritative 1/1 colorless Eldrazi Scion predefined token with “Sacrifice this creature: Add
`{C}`,” plus its creation facade and SDK/scenario tests. The repository currently exposes an Eldrazi
Spawn helper, not the required Scion behavior. This audit stops before that change, as required.

No other production engine/SDK change is currently indicated. The other seven missing cards appear
composable from existing primitives, but that conclusion must be confirmed by one-card scenario work.
Any failure that demonstrates a general engine/SDK gap must return for approval rather than being
worked around in a Pest-only definition.

No Gym or agent-policy change is approved or included. The policy gaps above should first be tested
against current generic behavior; any proposed general policy change is a separate approval boundary.

## 7. Recommended validation sequence

1. Obtain approval for the shared Eldrazi Scion SDK/mtg-sets addition; implement and validate it in
   isolation if approved.
2. Human-review and replace or explicitly certify the generated definitions for Chainer's Edict,
   Marauding Blight-Priest, and Unearth, each with a direct scenario.
3. Add the eight missing card definitions one card at a time, with one authoritative scenario file per
   card and no deck construction.
4. Add focused interaction scenarios for separate lifegain events, Storm copies, trigger fan-out,
   counters, drains, Follow the Lumarets, and Pest/tapland entry sequencing.
5. Add deterministic current-agent decision tests for deployment, removal/payment choices,
   protection, recursion, Forestcycling/Omen sequencing, Storm ordering, and alternate costs. Report
   failures before changing production agent behavior.
6. Run card/scenario verification, then the standard shared validation gates. Do not generate seeds or
   run games as part of these steps.
7. Only after an explicit later approval, materialize the challenger as a deck candidate; then stage
   goldfish and matchup self-play as separate, opt-in experimental phases. The frozen control remains
   Pest Control v1.0 regardless of challenger results.
