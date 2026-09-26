# Atomic collection action — Stage E feature contract

Status: DESIGN_FROZEN_BEFORE_IMPLEMENTATION
Scope: reusable engine vocabulary; seedless deterministic qualification
Motivating card: Sphinx's Approach

## Why existing composition is insufficient

The SDK already exposes CardSource.Self, FromZone gathers, exact/up-to selection, MoveCollection,
Gate.DoAction, and explicit SuccessCriterion. A naive CompositeEffect is still unsafe for this shape:
it can move the resolving source before a later graveyard selection fails, or move a selected subset before
the whole action is known to be payable. Sphinx's Approach requires an all-or-nothing action.

## Required reusable behavior

Introduce one general action primitive/composition whose semantics are:

1. Gather one or more declared collection requirements without moving anything.
2. Each requirement has a source, optional filter, and exact required count.
3. Resolve any required player selections while all candidate cards remain in their current zones.
4. Revalidate every selected entity and the source immediately before commit.
5. If any requirement cannot be fulfilled or revalidation fails, commit zero zone moves and report action
   failure to the enclosing Gate.DoAction.
6. On success, move every selected collection and any declared source collection as one logical action,
   then report success.
7. No player receives priority between selection and commit.
8. The primitive must not contain Sphinx-, card-name-, or library-search-specific logic.

The first consumer will use:
- source requirement: CardSource.Self, exactly 1, current resolving source;
- graveyard requirement: controller graveyard, filter name == "Sphinx's Approach", exactly 4;
- destination for both: exile;
- payoff outside the primitive: filtered Sphinx library search -> battlefield -> shuffle.

## Fail-closed deterministic qualification

Engine-level:
- exact requirement unavailable -> no moves;
- decline at enclosing may gate -> action never begins;
- exact selection across duplicate names -> exactly N selected/moved;
- stale selected entity before commit -> zero moves;
- source no longer in required zone -> zero moves;
- success reports true only after every move commits;
- card conservation across all zones;
- no partial events claiming committed movement on failure;
- serialization and card-lint coverage for every new SDK field;
- generic non-Sphinx fixture proves the vocabulary is reusable.

Approach scenario:
- three graveyard Approaches -> unavailable/no exile;
- four -> legal;
- resolving spell is not one of the four graveyard cards;
- draw two occurs before feasibility/choice;
- draw can remove final Sphinx target;
- decline -> normal resolution destination and graveyard unchanged;
- success -> source + exactly four graveyard copies exiled;
- no target -> successful action, empty search, shuffle;
- counter -> no draw/action;
- pre-resolution graveyard disruption can remove feasibility;
- searched Sphinx enters rather than being cast;
- no priority window inside the resolving sequence;
- summoning sickness and EOT timing remain ordinary engine behavior.

## Acceptance boundary

This feature is not accepted because it compiles. Acceptance requires deterministic engine tests plus
Sphinx's Approach scenario tests green under the repository verify gate. Until then:
official Stage-E seeds = 0; games = 0; outcomes = 0.
