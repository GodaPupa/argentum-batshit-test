# Bounded original-source characteristics

User authority: NEXT_ADMISSION.json at 770f9a06614ff5addcd404df0d109bb9203ff825,
explicitly granted in CURRENT checkpoint 250b2a9d5820e897e4b8f856419b94e5100c0800.
Accepted production predecessor: e067e4b803d3cb2358d0b363929b940ebcfa2cbf.
The R5 acceptance and original source-history diagnostic are reused without repeat qualification or receiving.

## Contract and exact boundary

AbilitySourceCharacteristics supplies colors, subtypes and card types for resolution legality.
An original ObjectRef is preferred; a legacy battlefield-entry stamp is used when the ref is absent.
The original reference is never advanced along permitted source moves. A matching live battlefield
visit reads the cached projection, including explicitly empty colors/types. A departed or unprovable
snapshot-backed visit reads only the already captured snapshot. It never fills missing historical
fields from a new visit of the same entity. A nonbattlefield original reads its existing base card.

A captured-but-missing origin never recaptures the entity. Legacy abilities with neither identity nor snapshot retain the live/base fallback. Removed legacy
sources have empty unknown characteristics, as before. Known-lost sources without a snapshot also
have unknown characteristics: an unrelated new visit cannot supply history. Partial snapshots keep
their present fields and leave missing colors/types unknown. No snapshot is fabricated.

StackResolver changes only the trigger/activation source extraction and the private legality
plumbing. The optional explicit card-type set reaches both protection and SourceTypeTargeting.
Spell callers keep their existing card-type fallback. Ability controller attribution is untouched.
The accepted current-target plan, occurrence refs, groups, divisions, modal/splice bindings and legacy
target layout code are unchanged. No other protected binding changes.

## Layer trace

- SDK and Assay: no data language change, new card, registration or generator mapping.
- Projection: consume existing cached colors/subtypes/types; floating-effect fixtures prove changes
  made after stacking, distinct from printed data.
- Trigger/activation producers: existing captured snapshots and original identity are reused. A real
  token departure goes through the target-selection pause, serialization, resume and resolution.
  A real self-sacrifice cost supplies the activation snapshot. No producer changes.
- Resolution: protection and ability-source-type restrictions share the view; normal fizzle/resolved
  events remain the existing public result. All fixtures use the original ability controller.
- Continuations/cleanup: captured data survives real cleanup and full state serialization; no new
  continuation, state field, decision or duration.
- Server/legal actions/frontend: no new interaction or DTO. Existing target choices and fizzle events
  remain authoritative. Full ordinary and targeted transport regressions are required.
- Performance: one ephemeral view per resolving ability, one cached projection, no global scans beyond
  the existing battlefield membership lookup. No mutable shared state.

## Frozen evidence contract

Five original diagnostic tests retain identical bytes and assertion frames 81/85. The successor adds
30 projected/live/departed/returned matrix cases, 20 legacy/controller/spell cases, 8 real token
departure/pause cases and 2 actual sacrifice-cost cases: 65 newly included cases total.
The retained baseline expands from 18,531 passes to 18,596, with the same exact 43 skips. Targeted126,
focused914, broad14977 and engine4202 identities are frozen in PROTOCOL.json. The complete expanded
identity counters are derived by adding these fixed identities to already accepted report identities;
this is not a repeat audit of the accepted original artifact.

Two separately compiled helper mutations restore live-only lookup and same-ID wrong-visit reuse.
Each runs the same 35 cases: live-only must fail exactly18 frozen assertion frames and wrong-visit
exactly8; both must have zero errors/skips and only the intended test task failure. Compilation is
not behavioral evidence. Source binding and normal/both mutant compilation precede targeted controls;
client, recording, actor, focused, broad, ordinary and Assay follow successful controls. Restoration
and rebind are mandatory after each mutation, including failed workflows.

Receiving checks the original artifact, complete manifest and raw reports against immutable source,
tree, workflow, validation, run/job/attempt pins. A green workflow alone is insufficient. Review is
same-owner review, not independent review. No games/seeds, budget increase, main merge, Spellskite
registration or multiplayer admission is authorized by this engineering successor.
