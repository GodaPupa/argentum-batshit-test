package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Reproduces a bug in Auxiliary Boosters (EOE #5, {4}{W} Equipment):
 *
 *   When this Equipment enters, create a 2/2 colorless Robot artifact creature token
 *   and attach this Equipment to it.
 *
 * The token IS created, but the Equipment is NOT attached to it. Root cause is
 * the ETB chain wiring `Effects.AttachEquipment(EffectTarget.ContextTarget(0))`
 * after `CreateTokenEffect`: `CreateTokenEffect` publishes the new token's id into
 * `context.pipeline.storedCollections[CREATED_TOKENS]`, not into `context.targets[0]`,
 * so `ContextTarget(0)` resolves to null on a triggered ability that has no declared
 * targets. (StarforgedSword does the same chain inside a `Mode.withTarget(...)`,
 * which populates `context.targets[0]` for it — so it works.)
 *
 * Expected fix: `EffectTarget.PipelineTarget(CREATED_TOKENS, 0)` for the attach.
 */
class AuxiliaryBoostersAttachOnEtbTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Auxiliary Boosters ETB — create Robot token AND attach Equipment to it") {

            test("Equipment auto-attaches to the freshly-created 2/2 Robot token and grants +1/+2 + flying") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Auxiliary Boosters")
                    .withLandsOnBattlefield(1, "Plains", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Auxiliary Boosters")
                withClue("Casting Auxiliary Boosters should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
                game.resolveStack()

                // (1) Token presence — should be the easiest assertion and unaffected by the bug.
                withClue("Auxiliary Boosters' ETB should create exactly one 2/2 Robot token") {
                    game.findPermanents("Robot Token").size shouldBe 1
                }

                // (2) Equipment is on Alice's battlefield.
                val boosters = game.findPermanent("Auxiliary Boosters")
                withClue("Auxiliary Boosters should be on Alice's battlefield after resolving") {
                    (boosters != null) shouldBe true
                }

                val robotToken = game.findPermanent("Robot Token")!!

                // (3) THE BUG — attachment should be established by the ETB chain.
                withClue(
                    "Auxiliary Boosters should have AttachedToComponent pointing at the Robot token " +
                        "(observed: ${game.state.getEntity(boosters!!)?.get<AttachedToComponent>()?.targetId})"
                ) {
                    game.state.getEntity(boosters)?.get<AttachedToComponent>()?.targetId shouldBe robotToken
                }
                withClue(
                    "Robot token should expose AttachmentsComponent listing Auxiliary Boosters " +
                        "(observed: ${game.state.getEntity(robotToken)?.get<AttachmentsComponent>()?.attachedIds})"
                ) {
                    game.state.getEntity(robotToken)?.get<AttachmentsComponent>()?.attachedIds shouldBe listOf(boosters)
                }

                // (4) Static abilities take effect via the attachment: 2/2 + (+1/+2) = 3/4, with flying.
                val projected = stateProjector.project(game.state)
                withClue("Equipped Robot token should be 3/4 (base 2/2 + Auxiliary Boosters' +1/+2)") {
                    projected.getPower(robotToken) shouldBe 3
                    projected.getToughness(robotToken) shouldBe 4
                }
                withClue("Equipped Robot token should have flying granted by Auxiliary Boosters") {
                    projected.hasKeyword(robotToken, Keyword.FLYING) shouldBe true
                }
            }
        }
    }
}
