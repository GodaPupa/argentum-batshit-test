package com.wingedsheep.mtg.sets.definitions.fut.cards

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.CreateDelayedTriggerEffect
import com.wingedsheep.sdk.scripting.effects.DelayedTriggerTiming
import com.wingedsheep.sdk.scripting.effects.Gate
import com.wingedsheep.sdk.scripting.effects.GatedEffect
import com.wingedsheep.sdk.scripting.effects.LoseGameEffect
import com.wingedsheep.sdk.scripting.effects.PayManaCostEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Pact of Negation — Future Sight #42
 * {0} Instant
 *
 * Counter target spell.
 * At the beginning of your next upkeep, pay {3}{U}{U}. If you don't, you lose the game.
 */
val PactOfNegation = card("Pact of Negation") {
    manaCost = "{0}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Counter target spell.\nAt the beginning of your next upkeep, pay {3}{U}{U}. If you don't, you lose the game."

    spell {
        target = Targets.Spell
        effect = CompositeEffect(listOf(
            Effects.CounterSpell(),
            CreateDelayedTriggerEffect(
                step = Step.UPKEEP,
                timing = DelayedTriggerTiming.NEXT_TURN,
                fireOnPlayer = EffectTarget.PlayerRef(Player.You),
                effect = GatedEffect(
                    gate = Gate.MayPay(PayManaCostEffect(ManaCost.parse("{3}{U}{U}"))),
                    then = CompositeEffect(emptyList()),
                    otherwise = LoseGameEffect(
                        target = EffectTarget.PlayerRef(Player.You),
                        message = "Pact of Negation upkeep payment was not paid"
                    )
                )
            )
        ))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "42"
        artist = "Jason Chan"
    }
}
