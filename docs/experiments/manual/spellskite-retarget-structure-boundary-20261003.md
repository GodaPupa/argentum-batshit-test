# Manual fixed-destination retarget: target structure boundary

Source baseline: b01e312106011af2aa904e7c70438df7620d5e0c. This isolated diagnostic
adds no card, production behavior, registry admission, or gameplay authority.

The qualified BecomesTargetEvent helper remains intact. The new issue is that a
flat-slot replacement is not yet a replacement of all authoritative target bindings.
`RetargetStructureBoundaryTest` contains one passing flat-slot/event control and
four required-behavior probes:

- Modal spell targets must also update `SpellOnStackComponent.modeTargetsOrdered`;
  otherwise `StackResolver` supplies the old targets to modal resolution.
- Spliced targets must also update `splicedTargetsOrdered`; `StackResolver` resolves
  each spliced effect with this separately retained slice.
- A divided allocation must stay with its target slot when that target changes;
  the entity-keyed `damageDistribution` currently retains the old entity key.
- An omitted optional requirement must not consume the next requirement's target.
  `TargetValidator.validateTargets` slices by each requirement's maximum count,
  not its declared selected count. `CastSpell` and `TargetsComponent` contain no
  per-requirement cardinality or slot-to-requirement identity. Identical flat target
  lists can therefore encode distinct valid optional-group declarations.

Source trace: `StackResolver` builds the flat union in its cast path while retaining
modal/splice slices, and uses those slices again during resolution. `TargetingEvents`
updates only `TargetsComponent`. `ContestedRetargetLogic.expandRequirements` repeats
maximum counts, and its global exclusion also cannot distinguish distinctness within
one target requirement from allowed repetition across independent target words.

Recovering historic target groups from current legality is invalid: target properties
and legality may change between announcement and redirection. A correct generalized
one-slot operation needs durable announcement-time slot/group identity through actions,
continuations, stack objects and copies, then an atomic update of flat targets, modal/
splice bindings and per-slot allocations. It must use the original source/controller's
existing target validation and the already-qualified event path. Do not replace this
with a Spellskite name check or reject all multiple-target spells as an approximation.

These probes deliberately assert required behavior; their failures are preserved as
engine-boundary evidence, not inverted into passing tests. No exact Spellskite candidate
or registry audit is justified until the shared structure is corrected.

Official counters delta: 0 allocations / 0 claims / 0 games / 0 outcomes.
