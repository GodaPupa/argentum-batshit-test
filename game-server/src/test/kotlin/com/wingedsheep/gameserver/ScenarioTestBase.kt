package com.wingedsheep.gameserver

/**
 * Shim: the canonical scenario harness now lives in the engine's `testFixtures`
 * ([com.wingedsheep.engine.support.ScenarioTestBase]). This typealias keeps the 104
 * existing game-server scenario tests — including those that reference the nested
 * `ScenarioTestBase.TestGame` / `ScenarioTestBase.ScenarioBuilder` types — compiling
 * untouched while behavior is proven in `rules-engine`.
 *
 * New card/rules scenario tests belong in `rules-engine/.../engine/scenarios/`.
 * Genuine server-level scenario tests (masking, DTO shape, tournament) may keep
 * extending this here; once none remain, this shim can be deleted.
 */
typealias ScenarioTestBase = com.wingedsheep.engine.support.ScenarioTestBase
