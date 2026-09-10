package com.wingedsheep.engine.legalactions

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.legalactions.support.setupP1
import com.wingedsheep.mtg.sets.definitions.fin.cards.IshgardTheHolySee
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * A Town land // spell Adventure (FIN — "Land — Town // Sorcery — Adventure") is a land-primary
 * Adventure card. From hand the player may either *play the land* or *cast the Adventure spell*.
 *
 * Regression guard for the engine fix: `CastSpellEnumerator` previously skipped every land in hand
 * before reaching its secondary-face enumeration, so a land-primary Adventure's spell face was never
 * offered. The fix enumerates the spell face for land-primary Adventure/Omen/modal-DFC cards while
 * still leaving the land itself to `PlayLandEnumerator`.
 *
 * Ishgard, the Holy See // Faith & Grief — land face `Land — Town`, Adventure face `{3}{W}{W}`.
 */
class TownLandSpellAdventureEnumerationTest : FunSpec({

    test("a land//spell Adventure in hand offers BOTH playing the land and casting the Adventure spell") {
        val driver = setupP1(
            hand = listOf("Ishgard, the Holy See"),
            // Five Plains affords the {3}{W}{W} Adventure face.
            battlefield = listOf("Plains", "Plains", "Plains", "Plains", "Plains"),
            extraSetCards = listOf(IshgardTheHolySee),
        )

        val view = driver.enumerateFor(driver.player1)

        // The land primary is playable from hand (PlayLandEnumerator).
        view.playLandActionsFor("Ishgard, the Holy See") shouldHaveSize 1

        // The Adventure spell face is castable from hand (the fix). It is the only cast option —
        // a land has no normal "cast the primary face" action.
        val casts = view.castActionsFor("Ishgard, the Holy See")
        casts shouldHaveSize 1
        (casts.single().action as CastSpell).faceIndex shouldBe 0
        casts.single().affordable shouldBe true
    }

    test("when the Adventure spell is unaffordable, the land can still be played and the spell shows grayed-out") {
        val driver = setupP1(
            hand = listOf("Ishgard, the Holy See"),
            battlefield = listOf("Plains"), // 1 mana — cannot pay {3}{W}{W}
            extraSetCards = listOf(IshgardTheHolySee),
        )

        val view = driver.enumerateFor(driver.player1)

        // Playing the land is always an alternative, so the spell face is still surfaced (grayed out)
        // rather than the menu silently offering only the land.
        view.playLandActionsFor("Ishgard, the Holy See") shouldHaveSize 1
        val casts = view.castActionsFor("Ishgard, the Holy See")
        casts shouldHaveSize 1
        casts.single().affordable shouldBe false
    }
})
