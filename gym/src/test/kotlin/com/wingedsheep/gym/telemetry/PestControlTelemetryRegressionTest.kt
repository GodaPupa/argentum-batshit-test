package com.wingedsheep.gym.telemetry

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class PestControlTelemetryRegressionTest : ScenarioTestBase() {
    init {
        test("legal land play prevents a pre-land-drop false bottleneck") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Essence Warden")
                .build()

            ActionableManaBottleneckTracker(cardRegistry)
                .observe(game.state, game.player1Id, turn = 1) shouldBe emptyList()
        }

        test("a post-land-drop castable spell is not a bottleneck") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Essence Warden")
                .build()

            ActionableManaBottleneckTracker(cardRegistry)
                .observe(game.state, game.player1Id, turn = 1) shouldBe emptyList()
        }

        test("reports and deduplicates a genuine total-mana shortage") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Fierce Witchstalker")
                .build()
            val tracker = ActionableManaBottleneckTracker(cardRegistry)

            val first = tracker.observe(game.state, game.player1Id, turn = 2).single()
            first.constraint shouldBe ManaConstraint.TOTAL_MANA
            first.requiredMana shouldBe 4
            first.availableMana shouldBe 1
            tracker.observe(game.state, game.player1Id, turn = 2) shouldBe emptyList()
        }

        test("reports a genuine color shortage separately") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Essence Warden")
                .build()

            ActionableManaBottleneckTracker(cardRegistry)
                .observe(game.state, game.player1Id, turn = 2).single().constraint shouldBe ManaConstraint.COLOR
        }

        test("reports an enters-tapped land as a tapland tempo constraint") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Jungle Hollow")
                .withCardInHand(1, "Weather the Storm")
                .build()

            ActionableManaBottleneckTracker(cardRegistry)
                .observe(game.state, game.player1Id, turn = 2)
                .single().constraint shouldBe ManaConstraint.TAPLAND
        }

        test("Scion mana resolves an otherwise genuine total-mana shortage") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Eldrazi Scion", isToken = true)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(2, "Hill Giant")
                .build()

            ActionableManaBottleneckTracker(cardRegistry)
                .observe(game.state, game.player1Id, turn = 2) shouldBe emptyList()
        }

        test("opponent-dependent interaction without a relevant opposing permanent is not a bottleneck") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Chainer's Edict")
                .withCardInHand(1, "Cast Down")
                .withCardInHand(1, "Bone Shards")
                .withCardOnBattlefield(2, "Plains")
                .build()

            ActionableManaBottleneckTracker(cardRegistry)
                .observe(game.state, game.player1Id, turn = 2) shouldBe emptyList()
        }

        test("modal targeted interaction with a valid opposing target reports a color shortage") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Bone Shards")
                .withCardOnBattlefield(2, "Hill Giant")
                .build()

            val bottleneck = ActionableManaBottleneckTracker(cardRegistry)
                .observe(game.state, game.player1Id, turn = 2).single()
            bottleneck.cardName shouldBe "Bone Shards"
            bottleneck.constraint shouldBe ManaConstraint.COLOR
        }

        test("sacrifice-mana trace attributes production consumption and the funded spell") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Eldrazi Scion", isToken = true)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(2, "Hill Giant")
                .build()
            val trace = SacrificeManaTrace()
            val scion = game.findPermanent("Eldrazi Scion")!!
            val ability = PredefinedTokens.EldraziScion.activatedAbilities.single().id

            var before = game.state
            var result = game.execute(ActivateAbility(game.player1Id, scion, ability))
            trace.observe(before, game.state, result.events, game.player1Id, turn = 2)

            before = game.state
            result = game.castSpell(1, "Cast Down", game.findPermanent("Hill Giant")!!)
            trace.observe(before, game.state, result.events, game.player1Id, turn = 2)

            val use = trace.snapshot().single()
            use.sourceName shouldBe "Eldrazi Scion"
            use.sacrificed shouldBe true
            use.manaProduced shouldBe 1
            use.manaConsumed shouldBe 1
            use.fundedActions shouldBe listOf("Cast Cast Down@T2")
            use.unusedMana shouldBe 0
        }

        test("sacrifice-mana trace reports mana lost without consumption as unused") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Eldrazi Scion", isToken = true)
                .build()
            val trace = SacrificeManaTrace()
            val scion = game.findPermanent("Eldrazi Scion")!!
            val ability = PredefinedTokens.EldraziScion.activatedAbilities.single().id

            val before = game.state
            val result = game.execute(ActivateAbility(game.player1Id, scion, ability))
            trace.observe(before, game.state, result.events, game.player1Id, turn = 2)

            val floating = game.state
            val emptied = floating.updateEntity(game.player1Id) { it.with(ManaPoolComponent()) }
            trace.observe(floating, emptied, emptyList(), game.player1Id, turn = 2)

            val use = trace.snapshot().single()
            use.manaProduced shouldBe 1
            use.manaConsumed shouldBe 0
            use.fundedActions shouldBe emptyList()
            use.unusedMana shouldBe 1
        }

        test("pre-spell telemetry distinguishes a land-unlocked setup from a currently executable one") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Carrier Thrall")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Pest Mascot")
                .build()
            val weather = game.findCardsInHand(1, "Weather the Storm").single()

            val snapshot = PreSpellSetupTelemetry(cardRegistry)
                .observe(game.state, game.player1Id, weather)

            snapshot.currentlyExecutableBeforeFocal shouldBe emptyList()
            snapshot.executableAfterLegalLandPlay shouldBe listOf(
                LandUnlockedSpell("Swamp", "Carrier Thrall"),
            )
            snapshot.bestValidatedSetupSequence shouldBe listOf(
                "play Swamp",
                "cast Carrier Thrall",
                "cast Weather the Storm",
            )
            snapshot.focalCastBeforeSuperiorSetup shouldBe true
        }

        test("pre-spell telemetry reports a genuinely current setup without inventing a land dependency") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Carrier Thrall")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Pest Mascot")
                .build()
            val weather = game.findCardsInHand(1, "Weather the Storm").single()

            val snapshot = PreSpellSetupTelemetry(cardRegistry)
                .observe(game.state, game.player1Id, weather)

            snapshot.currentlyExecutableBeforeFocal shouldBe listOf("Carrier Thrall")
            snapshot.executableAfterLegalLandPlay shouldBe emptyList()
            snapshot.bestValidatedSetupSequence shouldBe listOf(
                "cast Carrier Thrall",
                "cast Weather the Storm",
            )
        }

        test("pre-spell telemetry separates a present sequence that a tapped land still cannot fund") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Jungle Hollow")
                .withCardInHand(1, "Carrier Thrall")
                .withCardInHand(1, "Weather the Storm")
                .build()
            val weather = game.findCardsInHand(1, "Weather the Storm").single()

            val snapshot = PreSpellSetupTelemetry(cardRegistry)
                .observe(game.state, game.player1Id, weather)

            snapshot.stillUnexecutableAfterLegalLandPlay shouldBe listOf(
                LandUnlockedSpell("Jungle Hollow", "Carrier Thrall"),
            )
            snapshot.focalCastBeforeSuperiorSetup shouldBe false
        }

        listOf(
            "Game 1 shape" to listOf("Carrier Thrall", "Follow the Lumarets"),
            "Game 8 shape" to listOf("Blood Researcher", "Bone Shards"),
            "Game 30 shape" to listOf("Fierce Witchstalker"),
        ).forEach { (label, extraCards) ->
            test("pre-spell telemetry rejects the equivalent double-Weather ordering from $label") {
                var builder = scenario().withPlayers()
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInHand(1, "Swamp")
                    .withCardInHand(1, "Weather the Storm")
                    .withCardInHand(1, "Weather the Storm")
                    .withCardOnBattlefield(1, "Pest Mascot")
                extraCards.forEach { builder = builder.withCardInHand(1, it) }
                val game = builder.build()
                val weather = game.findCardsInHand(1, "Weather the Storm").first()

                val snapshot = PreSpellSetupTelemetry(cardRegistry)
                    .observe(game.state, game.player1Id, weather)

                withClue(snapshot) {
                    snapshot.executableAfterLegalLandPlay shouldBe emptyList()
                    snapshot.executableButNotMateriallySuperior shouldContain
                        listOf("play Swamp", "cast Weather the Storm")
                    snapshot.bestValidatedSetupSequence shouldBe null
                    snapshot.focalCastBeforeSuperiorSetup shouldBe false
                }
            }
        }
    }
}
