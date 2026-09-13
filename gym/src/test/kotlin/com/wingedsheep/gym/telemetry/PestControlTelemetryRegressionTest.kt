package com.wingedsheep.gym.telemetry

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.doubles.shouldBePositive
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
            val audit = snapshot.evaluatedSequences.single {
                it.relevantAction == "Carrier Thrall" && it.materiallySuperior
            }
            audit.classifications shouldContain PreSpellSetupClassification.LAND_UNLOCKED
            audit.classifications shouldContain PreSpellSetupClassification.GENUINE_MISSED_SUPERIOR_SEQUENCE
            audit.landEntersTapped shouldBe false
            audit.steps.map(SetupActionAudit::action) shouldBe listOf(
                "play Swamp", "cast Carrier Thrall", "cast Weather the Storm",
            )
            audit.steps.drop(1).all { it.manaCost != null && it.resourcesAfter != null }.shouldBeTrue()
            audit.steps.flatMap(SetupActionAudit::paymentSources).isNotEmpty().shouldBeTrue()
            val json = Json.encodeToString(snapshot)
            json shouldContain "evaluatedSequences"
            json shouldContain "GENUINE_MISSED_SUPERIOR_SEQUENCE"
            json shouldContain "paymentSources"
            json shouldContain "floatingMana"
        }

        test("Game 18 payoff-enabling deployment survives land-unlocked sequence telemetry") {
            val game = scenario().withPlayers()
                .withTurnNumber(6)
                .withLifeTotal(1, 22)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Blood Researcher")
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Chainer's Edict")
                .withCardInHand(1, "Chainer's Edict")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Follow the Lumarets")
                .withCardOnBattlefield(1, "Blood Researcher")
                .build()
            val weather = game.findCardsInHand(1, "Weather the Storm").single()

            val snapshot = PreSpellSetupTelemetry(cardRegistry)
                .observe(game.state, game.player1Id, weather)

            val audit = snapshot.evaluatedSequences.single {
                it.relevantAction == "Blood Researcher" && it.materiallySuperior
            }
            audit.classifications shouldContain PreSpellSetupClassification.LAND_UNLOCKED
            audit.classifications shouldContain PreSpellSetupClassification.GENUINE_MISSED_SUPERIOR_SEQUENCE
            audit.completeSetupContinuation shouldBe listOf(
                "play Swamp", "cast Blood Researcher", "cast Weather the Storm",
            )
            audit.comparisonLineEvaluated shouldBe listOf(
                "cast Weather the Storm", "play Swamp", "cast Blood Researcher",
            )
            audit.activePayoffsBeforeDeployment shouldContain "Blood Researcher"
            audit.activePayoffsAfterDeployment.count { it == "Blood Researcher" } shouldBe 2
            audit.additionalImmediatePayoffValue!!.shouldBePositive()
            audit.completeResourcesEquivalent shouldBe true
            audit.setupFirstResourcesAfter shouldNotBe null
            audit.focalFirstResourcesAfter shouldNotBe null
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
            snapshot.evaluatedSequences.single { it.materiallySuperior }.classifications shouldContain
                PreSpellSetupClassification.CURRENTLY_EXECUTABLE
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
            val audit = snapshot.evaluatedSequences.single {
                PreSpellSetupClassification.STILL_UNEXECUTABLE_AFTER_LAND in it.classifications
            }
            audit.landEntersTapped shouldBe true
            audit.materiallySuperior shouldBe false
            audit.steps.last().resourcesAfter shouldBe null
        }

        test("targeted additional-cost setup preserves a concrete target and payment through Weather") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Forest")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Blood Artist")
                .build()
            val weather = game.findCardsInHand(1, "Weather the Storm").single()

            val snapshot = PreSpellSetupTelemetry(cardRegistry)
                .observe(game.state, game.player1Id, weather)

            val evaluations = snapshot.evaluatedSequences.filter { it.relevantAction == "Bone Shards" }
            evaluations.shouldNotBeEmpty()
            val setup = evaluations.maxBy { it.completedLineScore ?: Double.NEGATIVE_INFINITY }
                .steps.single { it.action == "cast Bone Shards" }
            withClue(snapshot) {
                setup.targetDetails.shouldNotBeEmpty()
                setup.targetDetails.map { it.id } shouldBe setup.targets
                setup.additionalCostMode shouldBe setup.additionalCosts.single().kind
                setup.additionalCosts.single().entities.shouldNotBeEmpty()
                setup.resourcesAfter shouldNotBe null
            }
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
                    snapshot.executableButNotMateriallySuperior shouldContain
                        listOf("play Swamp", "cast Weather the Storm")
                    val audits = snapshot.evaluatedSequences.filter { it.relevantAction == "Weather the Storm" }
                    audits.shouldNotBeEmpty()
                    audits.all {
                        PreSpellSetupClassification.EXECUTABLE_BUT_NOT_MATERIALLY_SUPERIOR in it.classifications &&
                            !it.materiallySuperior
                    }.shouldBeTrue()
                }
            }
        }
    }
}
