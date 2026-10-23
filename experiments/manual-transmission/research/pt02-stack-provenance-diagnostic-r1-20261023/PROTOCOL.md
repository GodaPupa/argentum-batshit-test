# PT02 stack provenance — diagnostic-only prospective protocol

**State: prepared diagnostic, uncompiled/untested; not a component qualification.** This source-isolated probe is a companion to the original paused-token R2 **failed, unadopted** evidence. It does not alter, delete, mute, skip, or replace any original PT02 assertions.

## Inputs
- R2 candidate ancestor/source: `06c27e1c31efd974300028c8ce5c1ceeac703280`, tree `560298ec2a80d623dd0fabd41c9c26351a250a34`.
- R2 failed run: `39946289164`, job `120389655294`, artifact `12498707228` (365,886 bytes, SHA-256 `492b55350389a9a391434d5e26c83193b34dc413b36478cca83d429a1795b71c`).
- Existing original PT02 test: `PausedTokenInstructionScenarioTest.kt` line 124, target-copy false/serialized false or true produced `expected 0 but was 1`. Original source-copy true/serialized false/true tests passed.
- Frozen Manual control, 23 goldens, main, and official/Precision budgets must remain unchanged.

## Questions and preregistered cases
Exactly **four** standalone diagnostic cases (`sourceCopy=false/true` × `serialized=false/true`), with the same two-token as-enters color fixture and external observer as the original PT02:
1. After the **first** color response, `pendingDecision` is still `ChooseColorDecision`, and **zero copied tokens have entered**.
2. No entity in `state.stack` has `TriggeredAbilityOnStackComponent`.
3. In target-copy variants only, **one** stack entity has `SpellOnStackComponent`, is named `Paused Token Copy Action`, and no ability-on-stack entities remain.
4. In source-copy variants, **zero** spell/activated-ability/triggered-ability entities remain on the priority stack.
5. Where the fixture requests serialization, GameState JSON round-trips byte-exactly before and after the first answer.

These are **source-supported predictions**, not evidence of a successful run. A mismatch, compilation error, or actual triggered stack entity is an unresolved issue and must not be reclassified as a pass.

## Review boundary

Why this test is needed: `GameTestDriver.stackSize` counts all stack entries. `StackResolver.resolveNonPermanentSpell` retains a spell object while effect continuation is pending; `resolveActivatedAbility` removes an activated-ability object when effect execution pauses. The original PT02 zero-stack assertion does not distinguish a still-resolving spell from an ETB trigger. The original test remains unchanged and failed.

This standalone diagnostic must not become an excuse to remove the original assertion or make arbitrary production changes. It merely establishes **runtime stack-object identity**. If it confirms the source-supported explanation, a separately reviewed, prospective test-oracle correction is needed before full paused-token adoption. Preserve the original failed R2 source/run/XML and every original assertion verbatim.

## Execution rules

- Bind the complete diagnostic source commit/tree before creating a validation-only child.
- Use one guarded Actions run of only `PausedTokenStackProvenanceDiagnosticTest`, through `scripts/gradle-locked`, global `flock`, and shared Manual concurrency.
- **No official games, seeds, Precision allocations, deck modification, protected golden updates, baseline rerun or wide qualification.**
- Publish original test XML/log and GitHub run/attempt/source/artifact identifiers; label every outcome as **diagnostic-only**.
