# Pest Control — Sample #2 Take 4 observability corrections

**SHARED ARGENTUM CHANGE: yes**

## Scope and preserved evidence

Sample #2 Take 4 was rejected in full at
`e5204d478cf96b1201a27d610498238e423e5022`. Its complete 30-seed vector, SHA-256
`9f53ae30fd233bc8a980163aff9a7df1d6b41095356072e520a2cad16f97a7ad`, remains permanently
retired and hard-disabled. This correction used its preserved artifact and deterministic synthetic
scenarios only. No retired seed or Pest gameplay vector executed.

Pest Control v1.0 is unchanged. Sample #1 remains the sole accepted Pest performance/engine sample,
and the challenger remains audit-only and unconstructed.

## Bone Shards audit root cause and correction

The rejection audit's initial hypothesis that Bone Shards acquired its choices only after priority
scoring was incomplete. Deterministic instrumentation showed that each candidate was already a
concrete `CastSpell` with its target and `AdditionalCostPayment` materialized. The actual gap was the
generic card-intent fold: it did not classify the removal effect nested inside Bone Shards' modal
wrapper, so friendly-removal audit extraction returned before reading the concrete target, payment,
and resolved events.

Audit extraction now recognizes a concrete friendly-targeted removal outcome from generic action and
event metadata even when the broad intent tag is absent. Each record explicitly says whether the
production removal-policy gate recognized the candidate. This is observability, not a policy change:
the production hold and target valuation still receive the unchanged intent classification.

The generic record now preserves:

- exact target identity, controller, and pre-removal battlefield value;
- mana sources, life cost, additional-cost mode, and exact sacrificed/discarded or other paid
  resources and their values;
- removal-card value and represented future-interaction opportunity cost;
- friendly and opposing target alternatives and the pass/hold value;
- death triggers, created tokens/resources, the complete resulting friendly battlefield and value,
  and engine/lethal/prevention/resource-transition effects;
- resolved value, net-versus-pass, required fair-trade margin and surplus, policy applicability and
  disposition, selected flag, and selection/rejection reason.

The final completeness gate requires every executed friendly removal to have a selected record.

## Explicit default serialization

Every Pest performance artifact now uses one exact JSON configuration with defaults and nulls
enabled. Mandatory audit members therefore remain present when their value is `0`, `false`, an empty
list/map/string-equivalent default, the default enum/string value, or `null`; schema completeness no
longer depends on serializer omission behavior.

## Sequencing completeness and targeted setup

The five Take 4 completeness errors were false positives caused by comparing different
representations: the compact non-superior list contained an intentional setup prefix, while the
structured proposal contained setup plus the focal Weather continuation. The schema now preserves
and distinguishes the setup prefix, complete setup continuation, actual line, complete setup-first
counterfactual, and complete focal-first comparison. The checker compares prefixes with prefixes and
complete lines with equivalent complete lines.

Pre-Weather setup evaluation no longer discards a candidate merely because it is targeted. It
enumerates only target combinations and additional-cost payments exposed by the authoritative legal
action, simulates each concrete combination, retains target/cost identities in structured steps, and
requires an executable complete setup-to-Weather continuation. It does not invent a target or cost.
The selected best line remains auditable when multiple legal combinations exist.

All five classifications remain serialized: currently executable, land-unlocked, still unexecutable
after land, executable but not materially superior, and genuine missed-superior sequence. Existing
Games 1/8/30 false-positive regressions and the genuine Game-16-style land-to-relevant-spell-to-
Weather regression remain green.

## Games 8, 18, and 23

The preserved artifact identifies the turn and Bone Shards cast but omits the exact target,
additional-cost choice, pass/hold comparison, complete alternatives, resulting board, and selection
reason. That is insufficient to reconstruct the exact historical strategic structure. The retired
seeds were not run. Games 8, 18, and 23 therefore remain permanently unclassifiable historical
actions, not policy failures.

A deterministic synthetic scenario independently demonstrates the corrected observation path on a
productive friendly-removal line: Bone Shards targets a friendly payoff, sacrifices Carrier Thrall,
records the resulting Scion/death value, and produces deterministic lethal. It proves that selected
modal/additional-cost friendly removal is observable; it does not demonstrate an adverse gameplay-
policy defect. Production gameplay policy was not modified.

## Hard pre-sample artifact contract

`PestControlArtifactContractTest` is a mandatory pre-sample CI gate. Deterministic synthetic scenarios
produce a real selected modal/additional-cost removal audit and all five sequencing classifications,
embed them in the actual `PestGoldfishBlock`, serialize with the exact production artifact writer,
and parse the final JSON. The test proves the artifact can answer:

- what the removal targeted and who controlled it;
- which additional-cost mode and exact resource it used;
- what passing was worth and which friendly/opposing alternatives existed;
- why friendly removal was selected and whether policy applied;
- whether the setup prefix, continuation, actual line, counterfactual, and comparison are complete;
- whether target/cost identities and pass/fair-trade values survived; and
- whether zero, false, empty, null, and default-valued mandatory fields are explicitly present.

No Take 5 seed may be generated or frozen unless this exact final-artifact contract is green in CI
and seed generation receives separate explicit authorization.

## Validation

Implementation commit `860d2f72b63bac460b90a04a1cc561bbb40ac9a9` is green in CI #228. Focused artifact/schema,
sequencing, and friendly-removal regressions; all 72 Pest agent decision tests; all 17 Pest sequencing
telemetry regressions; the complete AI suite; Bone Shards, Carrier Thrall, Pest, Follow, and Pest
lifegain engine scenarios; the summary test; and frozen-control/vector guards are green. All gameplay
runners were skipped. No retired seed executed.
