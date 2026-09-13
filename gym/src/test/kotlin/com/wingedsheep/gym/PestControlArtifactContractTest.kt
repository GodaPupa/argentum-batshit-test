package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.insight.FriendlyRemovalAudit
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.telemetry.LandUnlockedSpell
import com.wingedsheep.gym.telemetry.PreSpellSetupClassification
import com.wingedsheep.gym.telemetry.PreSpellSetupEvaluation
import com.wingedsheep.gym.telemetry.SetupActionAudit
import com.wingedsheep.gym.telemetry.SetupAdditionalCostAudit
import com.wingedsheep.gym.telemetry.SetupEntityAudit
import com.wingedsheep.gym.telemetry.SetupManaSource
import com.wingedsheep.gym.telemetry.SetupResourceState
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Hard pre-sample contract for the exact JSON writer used by every Pest performance artifact.
 * These are deterministic synthetic positions and records, never laboratory seed-vector games.
 * A future seed freeze is prohibited unless this exact final-artifact contract is green in CI.
 *
 * SHARED ARGENTUM CHANGE: yes
 */
class PestControlArtifactContractTest : ScenarioTestBase() {
    init {
        test("final Sample 2 JSON answers every mandatory sequencing and friendly-removal question") {
            val selectedRemoval = selectedBoneShardsAudit()
            val evaluations = syntheticSequencingEvaluations()
            val game = syntheticGame(selectedRemoval, evaluations)
            pestAuditCompletenessErrors(game) shouldBe emptyList()

            val block = PestGoldfishBlock(
                deckVersion = "synthetic artifact-contract fixture",
                agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
                horizon = 0,
                seeds = emptyList(),
                games = listOf(game),
                summary = summarizePest(listOf(game)),
            )
            val root = PEST_ARTIFACT_JSON.encodeToJsonElement(PestGoldfishBlock.serializer(), block).jsonObject
            val encoded = PEST_ARTIFACT_JSON.encodeToString(block)
            val decoded = PEST_ARTIFACT_JSON.parseToJsonElement(encoded).jsonObject
            decoded shouldBe root

            val removal = decoded.games().single().jsonObject["friendlyRemovalAudits"]!!.jsonArray
                .map { it.jsonObject }.single { it["selected"].toString() == "true" }
            removal.keys.shouldContainAll(
                "removalAction", "targetId", "targetName", "targetControllerId",
                "targetBattlefieldValueBefore", "manaSources", "lifePaid", "additionalCostMode",
                "additionalCosts", "removalResourceValueConsumed", "futureInteractionOpportunityCost",
                "deathTriggersCreated", "resourcesCreated", "resultingBoardState", "resultingBoardValue",
                "immediateEngineEffects", "deterministicLethal", "preventionBenefit",
                "resourceTransitionBenefit", "passHoldValue", "opposingTargetAlternatives",
                "friendlyTargetAlternatives", "resolvedLineValue", "netVersusHold",
                "requiredFairTradeMargin", "fairTradeSurplus", "policyApplied", "policyDisposition",
                "selected", "selectionReason",
            )
            removal["targetName"].toString() shouldBe "\"Blood Artist\""
            removal["additionalCostMode"].toString() shouldBe "\"sacrifice\""
            removal["lifePaid"].toString() shouldBe "0"
            removal["opposingTargetAlternatives"] shouldBe JsonArray(emptyList())
            removal["preventionBenefit"].toString() shouldBe "null"
            removal["policyApplied"].toString() shouldBe "true"

            val weather = decoded.games().single().jsonObject["weatherCasts"]!!.jsonArray.single().jsonObject
            val serializedEvaluations = weather["evaluatedSetupSequences"]!!.jsonArray.map { it.jsonObject }
            serializedEvaluations.flatMap { it["classifications"]!!.jsonArray.map { value -> value.toString().trim('"') } }
                .toSet() shouldBe PreSpellSetupClassification.entries.map { it.name }.toSet()
            serializedEvaluations.forEach { evaluation ->
                evaluation.keys.shouldContainAll(
                    "setupPrefix", "completeSetupContinuation", "actualLineTaken",
                    "counterfactualLineEvaluated", "comparisonLineEvaluated", "comparisonSteps",
                    "materiallySuperior",
                    "activePayoffsBeforeDeployment", "activePayoffsAfterDeployment",
                    "additionalImmediatePayoffValue", "setupFirstResourcesAfter",
                    "focalFirstResourcesAfter", "completeResourcesEquivalent",
                )
            }
            val payoffSequence = serializedEvaluations.single {
                it["relevantAction"].toString() == "\"Blood Researcher\""
            }
            payoffSequence["proposedLandPlay"].toString() shouldBe "\"Swamp\""
            payoffSequence["counterfactualLineEvaluated"]!!.jsonArray.map { it.toString().trim('"') } shouldBe
                listOf("play Swamp", "cast Blood Researcher", "cast Weather the Storm")
            payoffSequence["comparisonLineEvaluated"]!!.jsonArray.map { it.toString().trim('"') } shouldBe
                listOf("cast Weather the Storm", "play Swamp", "cast Blood Researcher")
            payoffSequence["additionalImmediatePayoffValue"].toString() shouldBe "1.0"
            payoffSequence["completeResourcesEquivalent"].toString() shouldBe "true"
            val boneStep = serializedEvaluations.flatMap { it["steps"]!!.jsonArray.map { step -> step.jsonObject } }
                .single { it["action"].toString() == "\"cast Bone Shards\"" }
            boneStep["targetDetails"]!!.jsonArray.single().jsonObject["name"].toString() shouldBe "\"Blood Artist\""
            boneStep["additionalCosts"]!!.jsonArray.single().jsonObject["entities"]!!.jsonArray
                .single().jsonObject["name"].toString() shouldBe "\"Carrier Thrall\""
        }
    }

    private fun selectedBoneShardsAudit(): FriendlyRemovalAudit {
        val game = scenario().withPlayers().withRngSeed(0xA71FAC7L)
            .withTurnNumber(15)
            .withLandsOnBattlefield(1, "Swamp", 1)
            .withCardInHand(1, "Bone Shards")
            .withCardInHand(1, "Forest")
            .withCardOnBattlefield(1, "Carrier Thrall")
            .withCardOnBattlefield(1, "Blood Artist")
            .withLifeTotal(2, 1)
            .build()
        val insights = mutableListOf<com.wingedsheep.ai.insight.AiDecisionInsight>()
        AIPlayer.create(
            cardRegistry,
            game.player1Id,
            AiProfile.PRODUCTION_CANDIDATE_EXPIRING,
            insightSink = { _, insight -> insights += insight },
        ).chooseAction(game.state)
        return withClue(insights.last()) {
            insights.last().options.mapNotNull { it.friendlyRemovalAudit }.single { it.selected }
        }
    }

    private fun syntheticSequencingEvaluations(): List<PreSpellSetupEvaluation> {
        val current = evaluation(
            listOf(PreSpellSetupClassification.CURRENTLY_EXECUTABLE, PreSpellSetupClassification.EXECUTABLE_BUT_NOT_MATERIALLY_SUPERIOR),
            listOf("cast Setup"), superior = false,
        )
        val landUnlocked = evaluation(
            listOf(PreSpellSetupClassification.LAND_UNLOCKED, PreSpellSetupClassification.GENUINE_MISSED_SUPERIOR_SEQUENCE),
            listOf("play Swamp", "cast Blood Researcher"), superior = true,
        )
        val still = evaluation(
            listOf(PreSpellSetupClassification.STILL_UNEXECUTABLE_AFTER_LAND),
            listOf("play Jungle Hollow", "cast Setup"), superior = false, complete = false,
        )
        val targeted = evaluation(
            listOf(PreSpellSetupClassification.CURRENTLY_EXECUTABLE, PreSpellSetupClassification.EXECUTABLE_BUT_NOT_MATERIALLY_SUPERIOR),
            listOf("cast Bone Shards"), superior = false, targeted = true,
        )
        return listOf(current, landUnlocked, still, targeted)
    }

    private fun evaluation(
        classifications: List<PreSpellSetupClassification>,
        prefix: List<String>,
        superior: Boolean,
        complete: Boolean = true,
        targeted: Boolean = false,
    ): PreSpellSetupEvaluation {
        val land = prefix.firstOrNull()?.takeIf { it.startsWith("play ") }?.removePrefix("play ")
        val resource = resources()
        val steps = prefix.map { action ->
            when {
                action.startsWith("play ") -> SetupActionAudit(action, cardId = EntityId("land"), resourcesBefore = resource, resourcesAfter = resource)
                targeted -> SetupActionAudit(
                    action = action,
                    cardId = EntityId("removal"),
                    manaCost = "{B}",
                    coloredRequirements = "{B}",
                    targets = listOf(EntityId("target")),
                    targetDetails = listOf(SetupEntityAudit(EntityId("target"), "Blood Artist", EntityId("player-1"))),
                    additionalCostMode = "sacrifice",
                    additionalCosts = listOf(SetupAdditionalCostAudit("sacrifice", listOf(SetupEntityAudit(EntityId("cost"), "Carrier Thrall", EntityId("player-1"))))),
                    paymentSources = resource.manaSources,
                    resourcesBefore = resource,
                    resourcesAfter = resource,
                )
                else -> SetupActionAudit(
                    action = action,
                    cardId = EntityId("setup"),
                    manaCost = "{1}",
                    coloredRequirements = "{1}",
                    paymentSources = resource.manaSources,
                    resourcesBefore = resource,
                    resourcesAfter = resource,
                )
            }
        } + if (complete) listOf(
            SetupActionAudit(
                action = "cast Weather the Storm",
                cardId = EntityId("weather"),
                manaCost = "{1}{G}",
                coloredRequirements = "{1}{G}",
                paymentSources = resource.manaSources,
                resourcesBefore = resource,
                resourcesAfter = resource,
            )
        ) else emptyList()
        val continuation = if (complete) prefix + "cast Weather the Storm" else emptyList()
        return PreSpellSetupEvaluation(
            turn = 7,
            relevantAction = if (targeted) "Bone Shards" else
                prefix.last().removePrefix("cast ").removePrefix("play "),
            proposedLandPlay = land,
            landEntersTapped = land?.let { it == "Jungle Hollow" },
            classifications = classifications,
            setupPrefix = prefix,
            completeSetupContinuation = continuation,
            proposedActionOrder = if (complete) continuation else prefix,
            steps = steps,
            actualLineTaken = listOf("cast Weather the Storm"),
            counterfactualLineEvaluated = continuation,
            comparisonLineEvaluated = if (complete) listOf("cast Weather the Storm") + prefix else emptyList(),
            comparisonSteps = if (complete) {
                (listOf("cast Weather the Storm") + prefix).map { action ->
                    SetupActionAudit(
                        action = action,
                        cardId = EntityId("comparison-${action.hashCode()}"),
                        manaCost = "{1}".takeIf { action.startsWith("cast ") },
                        coloredRequirements = "{1}".takeIf { action.startsWith("cast ") },
                        paymentSources = resource.manaSources.takeIf { action.startsWith("cast ") }.orEmpty(),
                        resourcesBefore = resource,
                        resourcesAfter = resource,
                    )
                }
            } else emptyList(),
            completedLineScore = 10.0.takeIf { complete },
            reorderedLineScore = (if (superior) 9.0 else 10.0).takeIf { complete },
            activePayoffsBeforeDeployment = listOf("Blood Researcher"),
            activePayoffsAfterDeployment = listOf("Blood Researcher", "Blood Researcher"),
            additionalImmediatePayoffValue = (if (superior) 1.0 else 0.0).takeIf { complete },
            setupFirstResourcesAfter = resource.takeIf { complete },
            focalFirstResourcesAfter = resource.takeIf { complete },
            completeResourcesEquivalent = true.takeIf { complete },
            materiallySuperior = superior,
            reason = if (complete) "synthetic complete comparison" else "synthetic unexecutable continuation",
        )
    }

    private fun resources() = SetupResourceState(
        manaSources = listOf(SetupManaSource(EntityId("mana"), "Forest", tapped = false)),
        untappedManaSourceCount = 1,
        floatingMana = linkedMapOf(
            "white" to 0, "blue" to 0, "black" to 0,
            "red" to 0, "green" to 0, "colorless" to 0,
        ),
        handSize = 0,
        spellsCastThisTurn = 0,
        temporaryConditions = emptyList(),
    )

    private fun syntheticGame(
        selected: FriendlyRemovalAudit,
        evaluations: List<PreSpellSetupEvaluation>,
    ) = PestGoldfishGame(
        game = 1,
        seed = 0,
        seedHex = "synthetic",
        mulligans = 0,
        opening = PestOpeningAccess(emptyList(), emptyList(), false, false, false, false, false),
        t1Development = emptyList(),
        firstMeaningfulPermanentTurn = null,
        actualWinningTurn = null,
        terminalMechanism = null,
        essenceWardenCastTurns = emptyList(),
        bloodResearcherCastTurns = emptyList(),
        pestMascotCastTurns = emptyList(),
        carrierThrallCastTurns = emptyList(),
        carrierThrallDeaths = 0,
        scionsCreated = 0,
        scionsSacrificedForMana = 0,
        scionFundedSpells = emptyList(),
        scionManaUses = emptyList(),
        fierceWitchstalkerCastTurns = emptyList(),
        generousEntCycleTurns = emptyList(),
        generousEntCastTurns = emptyList(),
        followCasts = emptyList(),
        weatherCasts = listOf(
            PestWeatherCast(
                turn = 7,
                stormCount = 0,
                expectedCopies = 0,
                observedCopies = 0,
                lifeBeforeCast = 20,
                availableManaBeforeCast = 0,
                handBeforeCast = emptyList(),
                actionsBeforeCastThisTurn = emptyList(),
                researcherPresent = false,
                mascotPresent = false,
                followAvailable = false,
                survivalRequired = false,
                pendingStackSources = emptyList(),
                currentlyExecutablePreWeatherSpells = listOf("Setup", "Bone Shards"),
                spellsExecutableAfterLegalLandPlay = listOf(LandUnlockedSpell("Swamp", "Blood Researcher")),
                bestValidatedPreWeatherSetupSequence = listOf("play Swamp", "cast Blood Researcher", "cast Weather the Storm"),
                weatherCastBeforeSuperiorSetup = true,
                usefulSpellCastLaterThisTurn = null,
                stillUnexecutableAfterLegalLandPlay = listOf(LandUnlockedSpell("Jungle Hollow", "Setup")),
                executableButNotMateriallySuperior = listOf(listOf("cast Setup"), listOf("cast Bone Shards")),
                evaluatedSetupSequences = evaluations,
            )
        ),
        friendlyRemovalAudits = listOf(selected, selected.copy(selected = false, selectionReason = "not selected: synthetic alternative")),
        pureLifeGainActivations = emptyList(),
        turnActions = emptyList(),
        creatureEntries = emptyList(),
        payoffWithoutWardenTurns = emptyList(),
        wardenWithoutPayoffTurns = emptyList(),
        lifeEvents = emptyList(),
        totalLifeGained = 0,
        researcherCounterTriggers = 0,
        researcherCountersAdded = 0,
        mascotCounterTriggers = 0,
        mascotCountersAdded = 0,
        maximumResearcherPower = null,
        maximumResearcherToughness = null,
        maximumMascotPower = null,
        maximumMascotToughness = null,
        largestCreatureBattlefield = 0,
        largestPermanentBattlefield = 0,
        solitaireStrandedInteraction = emptyList(),
        genuineManaBottlenecks = emptyList(),
        jungleHollowTempoEvents = emptyList(),
        coexistence = PestCoexistence(false, false, false, false, false, false),
        functionalState = "SYNTHETIC",
        actions = 0,
        stopReason = "SYNTHETIC",
        auditErrors = emptyList(),
    )

    private fun JsonObject.games(): JsonArray = this["games"]!!.jsonArray
}
