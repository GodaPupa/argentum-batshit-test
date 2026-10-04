package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.TargetFinder
import com.wingedsheep.engine.handlers.TargetingSourceType
import com.wingedsheep.engine.handlers.effects.permanent.protection.GrantHexproofFromColorsExecutor
import com.wingedsheep.engine.legalactions.utils.TargetEnumerationUtils
import com.wingedsheep.engine.mechanics.targeting.PlayerColorHexproof
import com.wingedsheep.engine.mechanics.targeting.TargetValidator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.GrantHexproofFromColorsEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetPlayer
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FixedColorHexproofScenarioTest : ScenarioTestBase() {
    private val blueTarget = card("Scoped Hexproof Blue Probe") {
        manaCost = "{0}"
        colorIndicator = "U"
        typeLine = "Instant"
        spell { target = Targets.Player; effect = Effects.DealDamage(3, com.wingedsheep.sdk.scripting.targets.EffectTarget.ContextTarget(0)) }
    }
    private val grant = card("Scoped Hexproof Grant") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell { effect = Effects.GrantHexproofFromColors(setOf(Color.BLUE, Color.BLACK), EffectTarget.Controller) }
    }
    private val redTarget = card("Scoped Hexproof Red Probe") {
        manaCost = "{0}"
        colorIndicator = "R"
        typeLine = "Instant"
        spell { target = Targets.Player; effect = Effects.DealDamage(3, com.wingedsheep.sdk.scripting.targets.EffectTarget.ContextTarget(0)) }
    }
    init {
        listOf(blueTarget, redTarget, grant).forEach(cardRegistry::register)
        for (color in Color.entries) test("player hexproof restricts only opponent sources matching $color") {
            val game = scenario().withPlayers("A", "B").build()
            val result = GrantHexproofFromColorsExecutor().execute(game.state,
                GrantHexproofFromColorsEffect(setOf(Color.BLUE, Color.BLACK), EffectTarget.Controller),
                EffectContext(null, game.player1Id))
            val state = result.state
            val blocked = color == Color.BLUE || color == Color.BLACK
            PlayerColorHexproof.applies(state, game.player1Id, game.player2Id, setOf(color)) shouldBe blocked
            PlayerColorHexproof.applies(state, game.player1Id, game.player1Id, setOf(color)) shouldBe false
            val error = TargetValidator().validateTargets(state, listOf(ChosenTarget.Player(game.player1Id)),
                listOf(TargetPlayer()), game.player2Id, sourceColors = setOf(color), targetingSourceType = TargetingSourceType.SPELL)
            (error != null) shouldBe blocked
        }
        test("serialized player grant preserves color scope without full hexproof") {
            val game = scenario().withPlayers("A", "B").build()
            val state = GrantHexproofFromColorsExecutor().execute(game.state,
                GrantHexproofFromColorsEffect(setOf(Color.BLUE), EffectTarget.Controller, Duration.Permanent),
                EffectContext(null, game.player1Id)).state
            val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
            val restored = json.decodeFromString<GameState>(json.encodeToString(state))
            PlayerColorHexproof.applies(restored, game.player1Id, game.player2Id, setOf(Color.BLUE)) shouldBe true
            PlayerColorHexproof.applies(restored, game.player1Id, game.player2Id, emptySet()) shouldBe false
            PlayerColorHexproof.applies(restored, game.player1Id, game.player2Id, setOf(Color.GREEN, Color.BLUE)) shouldBe true
        }
        test("both targeting enumeration paths exclude a player only from blue source") {
            val game = scenario().withPlayers("A", "B").withCardInHand(2, blueTarget.name)
                .withCardInHand(2, redTarget.name).build()
            val state = GrantHexproofFromColorsExecutor().execute(game.state,
                GrantHexproofFromColorsEffect(setOf(Color.BLUE), EffectTarget.Controller), EffectContext(null, game.player1Id)).state
            val blue = game.findCardsInHand(2, blueTarget.name).single()
            val red = game.findCardsInHand(2, redTarget.name).single()
            val finder = TargetFinder()
            val enumeration = TargetEnumerationUtils(PredicateEvaluator())
            for (source in listOf(blue, red)) {
                val expected = source == red
                (game.player1Id in finder.findLegalTargets(state, TargetPlayer(), game.player2Id, source)) shouldBe expected
                (game.player1Id in enumeration.findValidTargets(state, game.player2Id, TargetPlayer(), source)) shouldBe expected
            }
        }
        test("blue spell targeting player fizzles if matching hexproof appears before resolution") {
            val game = scenario().withPlayers("A", "B").withCardInHand(2, blueTarget.name)
                .withActivePlayer(2).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpellTargetingPlayer(2, blueTarget.name, 1)
            game.state = GrantHexproofFromColorsExecutor().execute(game.state,
                GrantHexproofFromColorsEffect(setOf(Color.BLUE), EffectTarget.Controller), EffectContext(null, game.player1Id)).state
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 20
        }
        test("red spell still damages scoped hexproof player") {
            val game = scenario().withPlayers("A", "B").withCardInHand(2, redTarget.name)
                .withActivePlayer(2).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.state = GrantHexproofFromColorsExecutor().execute(game.state,
                GrantHexproofFromColorsEffect(setOf(Color.BLUE, Color.BLACK), EffectTarget.Controller), EffectContext(null, game.player1Id)).state
            game.castSpellTargetingPlayer(2, redTarget.name, 1)
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 17
        }
        test("real spell dispatch grants scoped player hexproof") {
            val game = scenario().withPlayers("A", "B").withCardInHand(1, grant.name)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, grant.name); game.resolveStack()
            PlayerColorHexproof.applies(game.state, game.player1Id, game.player2Id, setOf(Color.BLACK)) shouldBe true
        }
        test("cleanup expires temporary color grant without erasing permanent grant") {
            val game = scenario().withPlayers("A", "B").build()
            val executor = GrantHexproofFromColorsExecutor()
            val context = EffectContext(null, game.player1Id)
            var state = executor.execute(game.state, GrantHexproofFromColorsEffect(setOf(Color.BLUE), EffectTarget.Controller, Duration.Permanent), context).state
            state = executor.execute(state, GrantHexproofFromColorsEffect(setOf(Color.BLACK), EffectTarget.Controller), context).state
            val cleanup = com.wingedsheep.engine.core.CleanupPhaseManager(cardRegistry, com.wingedsheep.engine.handlers.DecisionHandler())
            state = cleanup.cleanupEndOfTurn(state)
            PlayerColorHexproof.applies(state, game.player1Id, game.player2Id, setOf(Color.BLUE)) shouldBe true
            PlayerColorHexproof.applies(state, game.player1Id, game.player2Id, setOf(Color.BLACK)) shouldBe false
        }
        test("unsupported player duration fails without mutating state") {
            val game = scenario().withPlayers("A", "B").build()
            val result = GrantHexproofFromColorsExecutor().execute(game.state,
                GrantHexproofFromColorsEffect(setOf(Color.BLUE), EffectTarget.Controller, Duration.UntilYourNextTurn), EffectContext(null, game.player1Id))
            (result.error != null) shouldBe true
            result.state shouldBe game.state
        }
        test("color word replacement rewrites grant scope without changing target or duration") {
            val effect = GrantHexproofFromColorsEffect(setOf(Color.BLUE, Color.BLACK), EffectTarget.Controller, Duration.Permanent)
            val replacer = object : com.wingedsheep.sdk.scripting.text.TextReplacer {
                override fun replaceColor(color: Color) = if (color == Color.BLUE) Color.RED else color
                override fun replaceCreatureType(subtype: String) = subtype
                override fun replaceSubtype(subtype: com.wingedsheep.sdk.core.Subtype) = subtype
            }
            effect.applyTextReplacement(replacer) shouldBe effect.copy(colors = setOf(Color.RED, Color.BLACK))
        }
        test("teammate blue source can target scoped hexproof player through both enumeration and validation") {
            val game = scenario().withPlayers("A", "Ally").withCardInHand(2, blueTarget.name).build()
            var state = game.state.updateEntity(game.player1Id) { it.with(com.wingedsheep.engine.state.components.identity.TeamComponent(0)) }
                .updateEntity(game.player2Id) { it.with(com.wingedsheep.engine.state.components.identity.TeamComponent(0)) }
            state = GrantHexproofFromColorsExecutor().execute(state,
                GrantHexproofFromColorsEffect(setOf(Color.BLUE), EffectTarget.Controller), EffectContext(null, game.player1Id)).state
            val source = game.findCardsInHand(2, blueTarget.name).single()
            PlayerColorHexproof.applies(state, game.player1Id, game.player2Id, setOf(Color.BLUE)) shouldBe false
            (game.player1Id in TargetFinder().findLegalTargets(state, TargetPlayer(), game.player2Id, source)) shouldBe true
            (game.player1Id in TargetEnumerationUtils(PredicateEvaluator()).findValidTargets(state, game.player2Id, TargetPlayer(), source)) shouldBe true
            TargetValidator().validateTargets(state, listOf(ChosenTarget.Player(game.player1Id)), listOf(TargetPlayer()), game.player2Id,
                sourceColors = setOf(Color.BLUE), targetingSourceType = TargetingSourceType.SPELL) shouldBe null
        }
        test("teammate blue source can target scoped hexproof permanent through both enumeration and validation") {
            val game = scenario().withPlayers("A", "Ally").withCardInHand(2, blueTarget.name)
                .withCardOnBattlefield(1, "Grizzly Bears").build()
            val bear = game.findPermanent("Grizzly Bears")!!
            var state = game.state.updateEntity(game.player1Id) { it.with(com.wingedsheep.engine.state.components.identity.TeamComponent(0)) }
                .updateEntity(game.player2Id) { it.with(com.wingedsheep.engine.state.components.identity.TeamComponent(0)) }
            state = GrantHexproofFromColorsExecutor().execute(state,
                GrantHexproofFromColorsEffect(setOf(Color.BLUE), EffectTarget.ContextTarget(0)),
                EffectContext(null, game.player1Id, targets = listOf(ChosenTarget.Permanent(bear)))).state
            val source = game.findCardsInHand(2, blueTarget.name).single()
            (bear in TargetFinder().findLegalTargets(state, Targets.Creature, game.player2Id, source)) shouldBe true
            (bear in TargetEnumerationUtils(PredicateEvaluator()).findValidTargets(state, game.player2Id, Targets.Creature, source)) shouldBe true
            TargetValidator().validateTargets(state, listOf(ChosenTarget.Permanent(bear)), listOf(Targets.Creature), game.player2Id,
                sourceColors = setOf(Color.BLUE), targetingSourceType = TargetingSourceType.SPELL) shouldBe null
        }
        test("scoped permanent hexproof follows projected controller through every targeting path") {
            val game = scenario().withPlayers("A", "B").withCardInHand(1, blueTarget.name)
                .withCardInHand(2, blueTarget.name).withCardOnBattlefield(1, "Grizzly Bears").build()
            val bear = game.findPermanent("Grizzly Bears")!!
            var state = GrantHexproofFromColorsExecutor().execute(game.state,
                GrantHexproofFromColorsEffect(setOf(Color.BLUE), EffectTarget.ContextTarget(0)),
                EffectContext(null, game.player1Id, targets = listOf(ChosenTarget.Permanent(bear)))).state
            state = state.addFloatingEffect(layer = com.wingedsheep.engine.mechanics.layers.Layer.CONTROL,
                modification = com.wingedsheep.engine.mechanics.layers.SerializableModification.ChangeController(game.player2Id),
                affectedEntities = setOf(bear), duration = Duration.EndOfTurn, context = EffectContext(null, game.player2Id))
            state.projectedState.getController(bear) shouldBe game.player2Id
            for ((number, player) in listOf(1 to game.player1Id, 2 to game.player2Id)) {
                val source = game.findCardsInHand(number, blueTarget.name).single()
                for (requirement in listOf(Targets.Creature, com.wingedsheep.sdk.scripting.targets.TargetSpellOrPermanent())) {
                    val expected = player == game.player2Id
                    (bear in TargetFinder().findLegalTargets(state, requirement, player, source)) shouldBe expected
                    (bear in TargetEnumerationUtils(PredicateEvaluator()).findValidTargets(state, player, requirement, source)) shouldBe expected
                    val error = TargetValidator().validateTargets(state, listOf(ChosenTarget.Permanent(bear)), listOf(requirement), player,
                        sourceColors = setOf(Color.BLUE), targetingSourceType = TargetingSourceType.SPELL)
                    (error == null) shouldBe expected
                }
            }
        }
        test("effect data round trips with fixed colors target and duration") {
            val effect = GrantHexproofFromColorsEffect(setOf(Color.BLUE, Color.BLACK), EffectTarget.Controller, Duration.Permanent)
            Json.decodeFromString<GrantHexproofFromColorsEffect>(Json.encodeToString(effect)) shouldBe effect
        }
        test("permanent grant uses projected scoped keyword rather than full hexproof") {
            val game = scenario().withPlayers("A", "B").withCardOnBattlefield(1, "Grizzly Bears").build()
            val bear = game.findPermanent("Grizzly Bears")!!
            val state = GrantHexproofFromColorsExecutor().execute(game.state,
                GrantHexproofFromColorsEffect(setOf(Color.BLUE), EffectTarget.ContextTarget(0)),
                EffectContext(null, game.player1Id, targets = listOf(ChosenTarget.Permanent(bear)))).state
            state.projectedState.hasKeyword(bear, "HEXPROOF_FROM_BLUE") shouldBe true
            state.projectedState.hasKeyword(bear, "HEXPROOF") shouldBe false
        }
    }
}
