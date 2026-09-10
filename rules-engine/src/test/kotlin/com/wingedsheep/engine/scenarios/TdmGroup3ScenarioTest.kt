package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for the TDM "group 3" batch:
 *  - Reverberating Summons (#117): {1}{R} Enchantment — animates into a 3/3 Monk with haste at the
 *    beginning of each combat if you've cast 2+ spells this turn; activated draw-two by discarding
 *    your hand and sacrificing itself.
 *  - Wild Ride (#132): {R} Sorcery — target creature gets +3/+0 and gains haste until end of turn.
 *    (Harmonize {4}{R} reuses the keyword; the harmonize cast machinery is exercised in the
 *    rules-engine HarmonizeKeywordTest.)
 *  - War Effort (#131): {3}{R} Enchantment — anthem (+1/+0 to creatures you control) plus a
 *    "whenever you attack, make a tapped & attacking 1/1 red Warrior, sacrificed at the next end step".
 *
 * Thunder of Unity (#231) was intentionally skipped: chapters II/III grant a turn-bounded
 * "whenever a creature you control enters this turn" delayed triggered ability, but the engine's
 * event-based delayed-trigger matcher (TriggerDetector.matchesEventForWatchedEntity) ignores the
 * TriggerSpec filter for ZoneChange events, so it cannot scope to "a creature you control" — it
 * would fire for every permanent entering the battlefield. Faithful support needs an engine change.
 */
class TdmGroup3ScenarioTest : ScenarioTestBase() {

    init {
        context("Reverberating Summons") {
            test("animates into a 3/3 Monk with haste at combat after casting two spells") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Reverberating Summons")
                    .withCardInHand(1, "Glory Seeker")
                    .withCardInHand(1, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val summons = game.findPermanent("Reverberating Summons")!!
                withClue("Reverberating Summons should not be a creature before combat") {
                    game.state.projectedState.isCreature(summons) shouldBe false
                }

                // Cast two spells this turn.
                val first = game.castSpell(1, "Glory Seeker")
                withClue("First Glory Seeker should resolve: ${first.error}") { first.error shouldBe null }
                game.resolveStack()
                val second = game.castSpell(1, "Glory Seeker")
                withClue("Second Glory Seeker should resolve: ${second.error}") { second.error shouldBe null }
                game.resolveStack()

                // Advance through real flow to the beginning of combat so the EachCombat trigger fires.
                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                game.resolveStack()

                withClue("Reverberating Summons should now be a creature") {
                    game.state.projectedState.isCreature(summons) shouldBe true
                }
                withClue("It should remain an Enchantment (in addition to its other types)") {
                    game.state.projectedState.hasType(summons, "ENCHANTMENT") shouldBe true
                }
                withClue("It should be a 3/3") {
                    game.state.projectedState.getPower(summons) shouldBe 3
                    game.state.projectedState.getToughness(summons) shouldBe 3
                }
                withClue("It should be a Monk with haste") {
                    game.state.projectedState.hasSubtype(summons, "Monk") shouldBe true
                    game.state.projectedState.hasKeyword(summons, Keyword.HASTE) shouldBe true
                }
            }

            test("does not animate if fewer than two spells were cast") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Reverberating Summons")
                    .withCardInHand(1, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val summons = game.findPermanent("Reverberating Summons")!!

                val first = game.castSpell(1, "Glory Seeker")
                withClue("Glory Seeker should resolve: ${first.error}") { first.error shouldBe null }
                game.resolveStack()

                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                game.resolveStack()

                withClue("With only one spell cast, Reverberating Summons should stay a non-creature") {
                    game.state.projectedState.isCreature(summons) shouldBe false
                }
            }

            test("activated ability draws two cards, discarding hand and sacrificing itself") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Reverberating Summons")
                    .withCardInHand(1, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val summons = game.findPermanent("Reverberating Summons")!!
                val abilityId = cardRegistry.getCard("Reverberating Summons")!!.activatedAbilities.first().id

                val activation = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = summons,
                        abilityId = abilityId,
                    )
                )
                withClue("Activating the draw ability should succeed: ${activation.error}") {
                    activation.error shouldBe null
                }
                game.resolveStack()

                withClue("Reverberating Summons should be sacrificed (no longer on battlefield)") {
                    game.isOnBattlefield("Reverberating Summons") shouldBe false
                }
                withClue("The Glory Seeker in hand should be discarded as part of the cost") {
                    game.findCardsInGraveyard(1, "Glory Seeker").size shouldBe 1
                }
                withClue("Player should have drawn two cards (Island + Forest)") {
                    game.findCardsInHand(1, "Island").size shouldBe 1
                    game.findCardsInHand(1, "Forest").size shouldBe 1
                }
            }
        }

        context("Wild Ride") {
            test("target creature gets +3/+0 and gains haste until end of turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Wild Ride")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2, no haste
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                withClue("Grizzly Bears starts as a 2/2 without haste") {
                    game.state.projectedState.getPower(bears) shouldBe 2
                    game.state.projectedState.getToughness(bears) shouldBe 2
                    game.state.projectedState.hasKeyword(bears, Keyword.HASTE) shouldBe false
                }

                val cast = game.castSpell(1, "Wild Ride", bears)
                withClue("Casting Wild Ride should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Grizzly Bears should be a 5/2 with haste") {
                    game.state.projectedState.getPower(bears) shouldBe 5
                    game.state.projectedState.getToughness(bears) shouldBe 2
                    game.state.projectedState.hasKeyword(bears, Keyword.HASTE) shouldBe true
                }
            }
        }

        context("War Effort") {
            test("anthem gives creatures you control +1/+0") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "War Effort")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 base
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                withClue("Grizzly Bears should be a 3/2 under War Effort's anthem") {
                    game.state.projectedState.getPower(bears) shouldBe 3
                    game.state.projectedState.getToughness(bears) shouldBe 2
                }
            }

            test("whenever you attack, create a tapped attacking 1/1 red Warrior token") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "War Effort")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                withClue("No Warrior tokens before attacking") {
                    game.findPermanents("Warrior Token").size shouldBe 0
                }

                val attack = game.declareAttackers(mapOf("Grizzly Bears" to 2))
                withClue("Declaring attackers should succeed: ${attack.error}") { attack.error shouldBe null }
                game.resolveStack()

                val warriors = game.findPermanents("Warrior Token")
                withClue("Exactly one Warrior token should have been created") {
                    warriors.size shouldBe 1
                }
                val token = warriors.first()
                withClue("The Warrior token should be tapped and attacking") {
                    game.state.getEntity(token)?.has<TappedComponent>() shouldBe true
                    game.state.getEntity(token)?.has<AttackingComponent>() shouldBe true
                }
            }
        }
    }
}
