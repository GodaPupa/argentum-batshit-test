# Testing Strategy — what to test where, and how to get there

_Scope: the whole repo's test story — SDK, engine, server, client, and the
self-play/parity tooling. Goal: a single, unambiguous answer to "where does this
test go?" plus a low-risk path from where we are today._

## Bottom line

**The engine is the source of truth, so card and rules behavior is proven in
`rules-engine`.** The server and client are an anti-corruption layer and a dumb
terminal respectively — they get tested for *the things only they do*
(masking, DTO transformation, protocol, rendering, interaction), not for
whether Lightning Bolt deals 3.

This principle is now upheld in practice. It used to be muddied by history: 104 scenario
tests lived in `game-server` despite having **zero** game-server dependencies — engine
tests that happened to be written against a harness that lived in the server module. All
four phases are now done: the harness was relocated into the engine, the skills point at
it, and 102 of those 104 tests have moved home (the 2 that remain are genuinely
server-level). See the phase log below for the details and the audit that confirmed only
2 belonged in `game-server`.

The plan was deliberately **not** a big-bang migration: (1) name the convention, (2) give
the engine one nice harness by **relocating** (not duplicating) the good builder into the
engine, (3) move the misplaced tests once the audit showed the risk was low.

---

## Principles

1. **Test where the behavior lives.** A card is `cardDef { }` data interpreted by
   the engine; the only way to prove it works is to run that data through the real
   `ActionProcessor`. That is an engine test by definition — there is no "card
   logic" layer above the engine to test instead.
2. **Each layer tests only what it uniquely owns.** Re-proving engine behavior at
   the server or E2E layer adds latency and flake without adding signal.
3. **Integration over isolation for cards.** A card test exercises
   data → engine → projection/triggers → result as one unit. That composition *is*
   the unit of value; don't mock it apart.
4. **Most cards need no bespoke test.** If a card only reuses mechanics already
   covered, the mechanic's tests protect it. Write a card scenario test when the
   card introduces or newly combines mechanics. (The `add-card` skill already
   gates this with "only if the card uses NEW effects/…".)
5. **One harness per setup style, not per module.** Duplicated harnesses drift.

---

## What is tested where (target)

| Layer                         | Module / location                                      | What belongs here                                                                                                                                                                                                                                                                                                               | What does **not**                                                                   |
|-------------------------------|--------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| **SDK data**                  | `mtg-sdk` unit tests                                   | A new data type serializes, round-trips, and composes as intended. Pure-data invariants.                                                                                                                                                                                                                                        | Anything requiring a running game.                                                  |
| **Engine unit / integration** | `rules-engine/.../{mechanics,handlers,predicates,...}` | An executor / projector / detector / solver in isolation: construct `GameState` directly, assert on the result. Layer ordering, mana solving, trigger detection paths.                                                                                                                                                          | End-to-end card flows (use a scenario instead).                                     |
| **Engine scenario** ⭐         | `rules-engine/.../engine/scenarios/`                   | **The primary home for card behavior and rules.** A card or mechanic exercised through the real `ActionProcessor` on a realistic board. Every CR rule/ruling you researched gets a paired assertion. Edge cases: fizzle, "may" declined, source leaves, zero/multiple instances, replacement vs trigger order, last-known info. | Frontend concerns; network.                                                         |
| **Server**                    | `game-server`                                          | **Only what the server uniquely does:** session/idempotency (`lastProcessedMessageId`), the auto-pass priority loop (`AutoPassManager`), tournament/lobby orchestration, the live WebSocket message protocol round-trip. **Note:** masking and DTO transformation are *not* here — `ClientStateTransformer`/`LegalActionEnricher` live in `rules-engine/.../engine/view`, so DTO-shape and per-viewer-masking *correctness* are engine-testable (the harness exposes `getClientState`). | Card behavior. Rules correctness. DTO/masking correctness (that's engine `view`).   |
| **E2E**                       | `e2e-scenarios/` (Playwright, 53 specs)                | Full-stack player flows: a decision actually renders and is clickable, targeting/priority/stack UX, animations driven by events. The frontend↔engine↔frontend round trip.                                                                                                                                                       | Exhaustive rules coverage (one representative flow per UI surface, not every card). |
| **Self-play / smoke**         | `gym` HTTP step loop                                   | Shake out new-set cards that don't behave as printed by driving full games; surfaces crashes/soft-locks no unit test predicted.                                                                                                                                                                                                 | Deterministic assertions (it's exploratory).                                        |
| **Differential (future)**     | `forge-parity-harness`                                 | Record-and-replay diff vs Forge as oracle for rules divergences.                                                                                                                                                                                                                                                                | (Aspirational — see its own backlog doc.)                                           |

⭐ = where the bulk of card/rules work should land.

### The one-line decision rule

> **Does the assertion depend on the server masking state, transforming a DTO,
> or the browser rendering something?** If no → `rules-engine`. If yes → that
> layer. Cards are almost always "no".

---

## Current state (2026-06)

- **`rules-engine` scenario tests: ~560** under `engine/scenarios/` (was 455; +104 from
  the Phase 3 migration below), plus ~65 more focused engine tests. These use either
  **`GameTestDriver`** (live game) or **`ScenarioTestBase`** (static board) — both now in
  `rules-engine/src/testFixtures` — over **`TestCards.all`**, which registers the *entire*
  `MtgSetCatalog` (every set's cards + basic lands) plus test-only cards. This is the
  primary card-test home.
- **`game-server` scenario tests: 2** (was 104). The other 102 had **zero** server
  dependency and migrated to `rules-engine/.../engine/scenarios/` (Phase 3, below). The
  two that remain genuinely exercise server-only behavior:
  `AshlingsCommandPriorityScenarioTest` (the `AutoPassManager` priority loop) and
  `ProtocolTestBase` (`@SpringBootTest` + live WebSocket round-trip). The
  `game-server/.../gameserver/ScenarioTestBase.kt` typealias shim stays as long as
  `AshlingsCommand` extends it.
- **Two setup styles, no shared builder:**
    - `GameTestDriver` — *live game*: real `GameInitializer`, decks, advance through
      real turns/priority/mana. Imperative. Per-test `registerCards(...)` boilerplate.
    - `ScenarioTestBase.ScenarioBuilder` — *static board*: fluent
      `scenario().withCardOnBattlefield(2,"X",tapped=true).withLifeTotal(1,5).inPhase(...).build()`,
      then a `TestGame` with name-based `castSpell("Bolt", target)`,
      `declareAttackers(mapOf("Bear" to 2))`, and decision sugar
      (`answerYesNo`, `selectTargets`, `submitDistribution`, …). Far nicer to author,
      but it lives in the server module.
- **Docs guidance already corrected** (committed): `add-feature` and `add-card`
  skills + `docs/RULES.md` now say engine tests are primary and game-server is only
  for frontend↔engine concerns. But the skill **templates still teach the
  server-only `ScenarioTestBase`**, which the engine can't import — a live
  inconsistency.

### Problems to fix

1. ~~The nicest authoring harness lives in the module we're steering tests *away* from.~~ (Fixed — Phase 1.)
2. ~~Skill templates point at a harness unavailable in the target module.~~ (Fixed — Phase 2.)
3. ~~104 engine tests are mislocated~~ (Fixed — Phase 3 migrated all but the 2 genuinely
   server-level ones.)

---

## Target state (reached)

- ✅ **One canonical scenario harness in `rules-engine/src/testFixtures`**, offering
  *both* setup styles against the real engine:
    - live-game flow (`GameTestDriver`), and
    - the fluent static-board builder + name-based action/decision API
      (`ScenarioBuilder`/`TestGame`), backed by `TestCards.all`. The static-board harness
      now also exposes `getLegalActions(playerNumber)` and `getClientState(playerNumber)`
      so legal-action and client-DTO assertions need no server `GameSession`.
- ✅ **`game-server` consumes that harness** via `testFixtures(project(":rules-engine"))`;
  its own `ScenarioTestBase` is a one-line typealias shim, kept only for the lone remaining
  server-level scenario test that still uses the static-board builder.
- ✅ **Skill templates teach the engine harness**, so every new card/feature test
  lands in `rules-engine`.
- ✅ **Misplaced tests migrated** — 102 of 104 moved home (see Phase 3); the genuinely
  server-level tests (auto-pass loop, protocol) stayed.

Acceptable end state: two *setup styles* coexist in one module — they're
complementary (live-flow vs static-board), not redundant. Fully converging them
into a single entry point is optional polish, not required.

---

## How we get there (phased, low-risk)

### Phase 0 — Guidance (DONE)

- `add-feature`/`add-card` skills + `docs/RULES.md` state engine-primary; game-server
  only for frontend↔engine. ✅ (committed `2e2a971c4`)

### Phase 1 — Relocate the nice harness into the engine (DONE)

**The keystone. Relocation, not duplication.**

1. ✅ Moved `ScenarioTestBase` (the `ScenarioBuilder` + `TestGame`) into
   `rules-engine/src/testFixtures/kotlin/com/wingedsheep/engine/support/ScenarioTestBase.kt`
   (package `com.wingedsheep.engine.support`, alongside `GameTestDriver`/`TestCards`).
2. ✅ Replaced its hand-maintained ~60-set `register(...)` list with
   `register(TestCards.all)` + `register(PredefinedTokens.allTokens)`. `TestCards.all` is the
   full `MtgSetCatalog` (a superset of the old list) plus test-only cards; the tokens are added
   back explicitly because `TestCards.all` does **not** bundle them, so this stays a strict
   superset and no card un-registers.
3. ✅ It uses only engine classes, so no dependency surgery inside it — **but** the fixtures
   source set needed Kotest on its compile classpath (the base extends `FunSpec`):
   added `testFixturesImplementation(libs.kotestRunner)` to `rules-engine/build.gradle.kts`.
4. ✅ Wired `game-server` to see it: added
   `testImplementation(testFixtures(project(":rules-engine")))` to `game-server/build.gradle.kts`.
5. ✅ Collapsed game-server's `ScenarioTestBase` to a **typealias** shim (not a subclass):
   ```kotlin
   typealias ScenarioTestBase = com.wingedsheep.engine.support.ScenarioTestBase
   ```
   A subclass shim (`abstract class ScenarioTestBase : …support.ScenarioTestBase()`) does **not**
   make the inherited nested `ScenarioTestBase.TestGame` reachable via the subclass qualifier in
   Kotlin; the typealias makes the constructor and the bare nested names resolve. One residual
   Kotlin limitation: you can't reach a nested class *through* a typealias qualifier
   (`ScenarioTestBase.TestGame`), so the 3 sites that used that qualified form in member extension
   receivers (`TapEachTargetScenarioTest`, `NewWayForwardScenarioTest`) were switched to the bare
   `TestGame` — in scope inside any subclass, matching how the other ~100 tests already write it.
6. ✅ `just test-rules` (520 files) + `just test-server` (104 scenario tests) green.

_Outcome: engine gained the fluent builder immediately; one harness, no duplication; the 104
game-server scenario tests compile and pass with only 3 one-token receiver-type edits._

### Phase 2 — Point the skills at the engine harness (DONE)

- ✅ Updated `add-card` `examples.md` scenario template (package
  `com.wingedsheep.engine.scenarios`, import `com.wingedsheep.engine.support.ScenarioTestBase`)
  and the `add-feature` "Scenario" + "Harness & scope" bullets to teach the engine harness.
  Resolves the template inconsistency Phase 0 left open. (`add-card/SKILL.md` already pointed at
  the engine path from Phase 0.)
- ✅ Documented both styles in two places: the `examples.md` template preamble and
  `docs/architecture-principles.md` §5.2 now both name live-game (`GameTestDriver`) vs
  static-board (`ScenarioTestBase`) and where each lives.
- ✅ Deleted `docs/adding-new-cards-workflow.md` (superseded by the `add-card` skill) and removed
  its two links from `docs/card-sdk-language-reference.md`. Its stale
  `game-server/src/test/.../scenarios/` template is gone with it.

### Phase 3 — Migrate misplaced tests (DONE)

What started as "migrate on touch" was completed in one pass once the audit showed the
risk was low and the remainder small. **102 of the 104 `game-server` scenario tests moved
to `rules-engine/.../engine/scenarios/`; 2 genuinely server-level tests stayed.**

How the 102 split:

1. **88 were a verbatim move** — only a package line (`com.wingedsheep.gameserver.scenarios`
   → `com.wingedsheep.engine.scenarios`) and the `ScenarioTestBase` import
   (`com.wingedsheep.gameserver.ScenarioTestBase` → `com.wingedsheep.engine.support.ScenarioTestBase`)
   changed. `git mv` + a two-line `perl` swap.
2. **16 needed a small rewrite.** They built a throwaway server `GameSession` (+ mock
   `WebSocketSession`s + `injectStateForTesting`) purely to call `getLegalActions`. But
   `GameSession.getLegalActions` just delegates to `LegalActionEnumerator`
   (`engine.legalactions`) + `LegalActionEnricher` (`engine.view`) — both engine classes —
   plus a thin priority/actor gate. So a new `TestGame.getLegalActions(playerNumber)` helper
   was added to the engine harness (mirroring that gate exactly), and each test swapped
   `session.getLegalActions(game.playerNId)` → `game.getLegalActions(N)`, dropping the
   `GameSession`/mockk scaffolding. (The three that also asserted on client-state DTO content
   already used the harness's `game.getClientState(...)`, which is engine `view`.)

**Audit conclusion — only 2 tests justify staying in `game-server`:**
`AshlingsCommandPriorityScenarioTest` (drives the `AutoPassManager` + `GameSession`
auto-pass priority loop — genuinely server logic) and `ProtocolTestBase`
(`@SpringBootTest` + live WebSocket round-trip). Everything else was engine behavior
reached through a server convenience wrapper.

The `game-server` `ScenarioTestBase` typealias shim stays while `AshlingsCommand` extends
it; it can be deleted if that test is ever reworked to not need the static-board builder.

### Phase 4 (optional) — Converge setup styles

- If the two-style split causes friction, fold the static-board builder into
  `GameTestDriver` as an alternate setup path that returns the same driver, so
  there's one action/query/assertion vocabulary with two ways to seed the board.
- Pure polish; only do it if authors actually trip over the split.

---

## Authoring quick-reference (target)

**A card / rules behavior** → `rules-engine/.../engine/scenarios/FooTest.kt`:

- Static board, exact setup: `scenario().withCardOnBattlefield(...).build()` then
  name-based `castSpell` / `declareAttackers` / decision sugar.
- Needs real turn/priority/mana flow: `GameTestDriver` + `initGame/initMirrorMatch`,
  `passPriorityUntil(...)`, `put*OnBattlefield`, submit actions.
- Assert via `StateProjector().project(state)` for static-ability/projection checks,
  or the harness's life/zone/stack queries.

**A new SDK type** → `mtg-sdk` round-trip/unit test (serializes, composes).

**Server-only behavior** (masking, DTO shape, idempotency, tournament) → `game-server`.

**A player-facing flow** (decision renders & is clickable, targeting UX) →
`e2e-scenarios/` Playwright.

**"Does this new set actually play?"** → gym HTTP self-play loop (exploratory).

---

## Decisions / open questions

- **Naming on relocation:** ~~keep `ScenarioTestBase` vs. rename to `EngineScenarioTest`~~
  _Resolved: kept the name_ — the shim and all 102 migrated tests needed no rename.
- **Delete the game-server shim eventually?** Now blocked only on
  `AshlingsCommandPriorityScenarioTest`, the sole remaining test that extends it (for the
  static-board builder). Masking/DTO did *not* turn out to justify keeping it — that logic
  is engine `view`, not server. Rework `AshlingsCommand` to build state without the shim
  (or accept it referencing the engine `support.ScenarioTestBase` directly) and the shim
  can go.
- **`TestCards.all` cost:** registering the full catalog per spec is already what every
  engine scenario test does; if registry build time ever shows up in profiles, cache a
  shared registry across the spec (it's immutable input).
- **Convergence (Phase 4):** worth it only if the two-style split generates real
  confusion. Revisit after Phases 1–3 settle.
