package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.PestMonsterTronPolicy
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec

/**
 * Source-only/excluded R4 controls. Mirrors R2's artificial sparse and dense
 * 40-Forest boards, with the same existing frozen Monster Tron AI profile.
 * These are NOT G10 states; no player action is submitted and no terminal
 * gameplay or formal experiment result can be generated.
 * The R2 original XML reported CastSpell for both cases and 17/29 legal actions.
 * This test checks action category, not byte-identical action identity.
 */
class G10R5SparseDenseParityTest : FunSpec({
    val enabled = System.getenv("ARGENTUM_G10_R5_PROFILE") == "EXCLUDED_LATE_BOARD_ONLY"
    test("excluded R2 sparse control maintains CastSpell category").config(enabled = enabled) {
        r5SparseDenseControl(6, 2, 17)
    }
    test("excluded R2 dense control maintains CastSpell category").config(enabled = enabled) {
        r5SparseDenseControl(18, 6, 29)
    }
})

private fun r5SparseDenseControl(forestCount: Int, creatureCount: Int, expectedLegal: Int) {
    check(System.getenv("ARGENTUM_G10_R5_PROFILE") == "EXCLUDED_LATE_BOARD_ONLY")
    check(System.getenv("PLAY_FIRST_ONE_SHOT_ACK").isNullOrBlank())
    check(System.getenv("PLAY_FIRST_ISOLATED_ACK").isNullOrBlank())
    check(System.getenv("PLAY_FIRST_ISOLATED_CASE_FILE").isNullOrBlank())
    check(System.getenv("ARGENTUM_SUCCESSOR_REQUEST_JSON").isNullOrBlank())
    System.getenv("GITHUB_REF")?.let {
        check(it == "refs/heads/play-first/g10-r5-bounded-source-choice-20261014")
    }
    System.getenv("GITHUB_RUN_ATTEMPT")?.let { check(it == "1") }

    val driver = GameTestDriver()
    driver.registerCards(TestCards.all)
    driver.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
    driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
    val player = driver.player1
    val opponent = driver.player2
    repeat(forestCount) { driver.putLandOnBattlefield(player, "Forest") }
    repeat(creatureCount) { driver.putCreatureOnBattlefield(player, "Centaur Courser") }
    repeat(creatureCount / 2) { driver.putCreatureOnBattlefield(opponent, "Centaur Courser") }
    driver.putCardInHand(player, "Giant Growth")
    driver.putCardInHand(player, "Centaur Courser")
    driver.putCardInHand(player, "Force of Nature")
    val state = driver.state
    check(!state.gameOver && state.pendingDecision == null && state.priorityPlayerId == player)
    val agent = AIPlayer.create(driver.cardRegistry, player, PestMonsterTronPolicy.profile)
    val legal = GameSimulator(driver.cardRegistry).getLegalActions(state, player)
    check(legal.size == expectedLegal) { "R2 control shape drift: ${legal.size} != $expectedLegal" }
    val chosen = agent.chooseFrom(state, legal).action
    check(chosen::class.java.simpleName == "CastSpell") {
        "R2 original action category changed: ${chosen::class.java.simpleName}"
    }
    check(driver.state === state) { "Synthetic state was mutated" }
    println("G10_R5_EXCLUDED_R2_CONTROL forest=$forestCount creatures=$creatureCount legal=${legal.size} action=CastSpell")
}
