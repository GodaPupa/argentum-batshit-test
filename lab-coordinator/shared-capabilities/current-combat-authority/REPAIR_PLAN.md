# Current combat assignment repair scope

Base: `2e7e78653ed1c7e56fc609b993a79b68ecca33c6` in isolated `engine/ferocity-combat-assignment`.

## Required behavior

Current comprehensive rules 510.1a/c/d permit arbitrary division among eligible creatures while requiring the full available combat damage whenever eligible recipients exist. Trample still requires lethal assignment to each blocking creature before any damage goes to the defender, counting damage already marked and concurrent assignments (702.19b); a nonzero deathtouch assignment is lethal (702.2c). Banding changes the chooser. The current rule has no damage-assignment-order constraint.

## Exact defect and paths

`DecisionValidators.validateCombatResolution` rejects more than one positively damaged creature below lethal and allows totals below source power. The still-serializable `AssignDamageDecision` validator has the analogous ordered-target rejection, accepts underassignment, and does not reject negative entries. The real board resumer stores chosen source-to-target amounts; `CombatDamageManager.proposeDamageAssignments` honors those exact amounts without another lethal-order gate. `DamageCalculator` already makes full-power default divisions; these remain suggestions. The live engine has no constructor of `AssignDamageDecision` beyond its declaration; existing persisted questions still pass through its validator/resumer.

## Implementation boundary

- Correct both validators, including complete budgets, nonnegative/range checks, unknown/duplicate edge rejection, and changed nonowned edge rejection. Unchanged full-board echoes remain accepted for existing clients/fixtures; only the current chooser can alter amounts.
- Preserve current trample/deathtouch semantics and banding chooser routing; correct misleading order comments. Preserve serialized order fields as explicitly nonbinding legacy metadata in this bounded patch.
- Remove false order wording from the board display and false per-target lethal lower bounds in the legacy assignment modal. Full legality stays in the engine. Avoid adding new client game rules.
- Preserve exact before bytes/hashes and existing combat regression files. No source edits outside this isolated tree. No builds or gameplay by this agent. Qualification remains pending build-owner gates and source review.

## Twelve fixed regression cases maximum

1. Real attacker assigns 2+2 damage across two three-toughness blockers; both damage events/marks survive.
2. Real blocker assigns 2+2 damage across two three-toughness attackers.
3. A source may put all damage on one eligible creature, including overlethal damage.
4. Board underassignment, overassignment, and negative values reject without mutation.
5. Legacy assignment accepts free nonlethal split, regardless of stored target order.
6. Legacy underassignment and negative values reject.
7. Trample drain rejects while a blocking creature lacks lethal assignment.
8. Trample accepts lethal from marked/concurrent damage plus a defender remainder.
9. Deathtouch with trample permits one damage per blocker and the rest to the defender.
10. Banding preserves chooser ownership and allows the same free division.
11. Changed nonowned edges, unknown edges, and duplicate edge IDs reject without mutation.
12. No eligible recipient permits zero assigned damage; the empty shape cannot accept invented recipients.

Related assertions may share one fixed case; no randomized or matchup run. Existing regressions remain separately retained and must be run by the build owner. The final receipt will state all affected files and any discovered remaining limitation.

## Approved amendment before any outcome: aggregate deathtouch and trample

Static review found that the board's old trample check compares all assigned damage against the trampler's source-specific lethal hint. This wrongly permits a deathtouch trampler assigning zero to a blocker to treat another source's nonlethal one damage as lethal. Conversely, it rejects a non-deathtouch trampler's drain when another attacker assigns positive deathtouch damage to that blocker.

Root explicitly authorized correcting this adjacent validator defect within case 9 before any build or test. The original twelve-case bank, initial plan, and immediately preceding source bytes remain archived. Case 9 retains its original own-source deathtouch scenario and adds two fixed boards: the zero-deathtouch-assignment rejection and the positive-other-source deathtouch acceptance. There remain twelve test declarations; this is a disclosed addition of two fixed subscenarios.

The validator uses the existing authoritative attacker/blocker nodes and edge amounts. It sums concurrent damage per blocker, considers damage already marked, and treats the blocker as assigned lethal damage if any deathtouch attacker actually assigns a positive amount to it. A zero assignment supplies no deathtouch contribution. Missing required nodes produce an error rather than inferred characteristics. No new decision schema is introduced. The UI displays shared-target aggregate damage as an amount, without claiming it crosses an individual source's lethal threshold; complete legality remains on the server.

The legacy per-source question retains its supplied trample thresholds. It has no live producer and does not carry the whole board needed for cross-source aggregation; the real board is the supported complete simultaneous-assignment path. This amendment does not add a new legacy producer or infer absent concurrent assignments.
