package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.*
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaPaymentWindow
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.RevealedToComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Fixed projection fixtures, not games or policy training. Ordinary menus come from the real
 * enumerator. Explicit synthetic parameter/question cases test the boundary's data domains only;
 * their inert answer continuation is never resumed, and no game outcome is inferred.
 */
class ActorObservationBoundaryTest : ScenarioTestBase() {
    private val p1 = EntityId.of("player-1")
    private val p2 = EntityId.of("player-2")
    private val epoch = ActorEpoch("ferocity-boundary-fixture-v1", "fixed-observation", 7)
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator(cardRegistry, ManaSolver(cardRegistry),
        CostCalculator(cardRegistry), PredicateEvaluator(), ConditionEvaluator(), TurnManager(cardRegistry))
    private val vigilantFixture = card("Ferocity Boundary Vigilant Fixture") {
        manaCost = "{1}{W}"
        typeLine = "Creature — Human"
        power = 2
        toughness = 2
        keywords(Keyword.VIGILANCE)
    }

    init {
        cardRegistry.register(vigilantFixture)
        test("nullable inline references preserve absence and still reject inaccessible present IDs") {
            requireAccessible(DecisionContext(), emptySet())
            requireAccessible(DecisionContext(sourceId = p1), setOf(p1))
            shouldThrow<ObservationBoundaryException> {
                requireAccessible(DecisionContext(sourceId = p2), setOf(p1))
            }.failure shouldBe BoundaryFailure.INACCESSIBLE_REFERENCE
        }
        test("complete actor input is invariant to unknown opponent hand identities") {
            val base = fixture()
            val hidden = base.getHand(p2).first()
            val replacement = base.getEntity(base.getLibrary(p2).first())!!.require<CardComponent>()
            val changed = base.updateEntity(hidden) { it.with(replacement) }
            input(base).canonicalJson() shouldBe input(changed).canonicalJson()
        }

        test("complete actor input is invariant to both unseen library orders and identities") {
            val base = fixture()
            var changed = base
            listOf(p1, p2).forEach { player ->
                val ids = base.getLibrary(player)
                val cards = ids.map { base.getEntity(it)!!.require<CardComponent>() }
                ids.forEachIndexed { index, id -> changed = changed.updateEntity(id) {
                    it.with(cards[(index + 1) % cards.size])
                } }
                changed = changed.copy(zones = changed.zones + (ZoneKey(player, Zone.LIBRARY) to ids.reversed()))
            }
            input(base).canonicalJson() shouldBe input(changed).canonicalJson()
        }

        test("hidden exile identity and unrelated entity ID do not enter actor input") {
            val base = fixture()
            val oldId = base.getExile(p2).single()
            val newId = EntityId.of("hidden-exile-substitution")
            val changed = base.withEntity(newId, base.getEntity(oldId)!!)
                .copy(zones = base.zones + (ZoneKey(p2, Zone.EXILE) to listOf(newId)))
                .removeEntity(oldId)
            val text = input(base).canonicalJson()
            text shouldBe input(changed).canonicalJson()
            text.contains(oldId.value) shouldBe false
            input(base).observation.zones.single { it.ownerId == p2 && it.zoneType == Zone.EXILE }.size shouldBe 1
        }

        test("authoritative RNG never changes actor payload or supplied policy stream") {
            val base = fixture()
            val original = input(base)
            listOf(0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE).forEach { seed ->
                input(base.copy(rng = GameRng(seed))).canonicalJson() shouldBe original.canonicalJson()
            }
            original.policyRngState shouldBe 991L
        }

        test("known opponent card remains visible while its unknown neighbor changes") {
            val base = fixture()
            val known = base.getHand(p2).last()
            val hidden = base.getHand(p2).first()
            val revealed = base.updateEntity(known) { it.with(RevealedToComponent.to(p1)) }
            val replacement = base.getEntity(base.getLibrary(p2).last())!!.require<CardComponent>()
            val changed = revealed.updateEntity(hidden) { it.with(replacement) }
            input(revealed).canonicalJson() shouldBe input(changed).canonicalJson()
            hand(input(revealed), p2).map { it.entityId } shouldContainExactly listOf(known)
        }

        test("known library subset is sorted independently of hidden positions") {
            val base = fixture()
            val ids = base.getLibrary(p1)
            val known = ids.take(2)
            var revealed = base
            known.forEach { id -> revealed = revealed.updateEntity(id) { it.with(RevealedToComponent.to(p1)) } }
            val reordered = revealed.copy(zones = revealed.zones + (ZoneKey(p1, Zone.LIBRARY) to ids.reversed()))
            input(revealed).canonicalJson() shouldBe input(reordered).canonicalJson()
        }

        test("drawing a previously hidden card exposes its identity to its owner") {
            val base = fixture()
            val top = base.getLibrary(p1).first()
            val drawn = base.moveToZone(top, ZoneKey(p1, Zone.LIBRARY), ZoneKey(p1, Zone.HAND))
            val after = input(drawn)
            (top in hand(after, p1).map { it.entityId }) shouldBe true
            after.canonicalJson() shouldNotBe input(base).canonicalJson()
        }

        test("public discard exposes the former hidden opponent card") {
            val base = fixture()
            val card = base.getHand(p2).first()
            val discarded = base.moveToZone(card, ZoneKey(p2, Zone.HAND), ZoneKey(p2, Zone.GRAVEYARD))
            val view = input(discarded).observation.zones.single { it.ownerId == p2 && it.zoneType == Zone.GRAVEYARD }
            view.cards.map { it.entityId } shouldContainExactly listOf(card)
        }

        test("wrong actor is rejected before current private question is projected") {
            val base = fixture()
            val state = ask(base) { id -> SelectCardsDecision(id, p1, "Choose from your hand",
                DecisionContext(), base.getHand(p1), 1, 1) }
            shouldThrow<ObservationBoundaryException> {
                adapter.build(state, p2, emptyList(), epoch, 991)
            }.failure shouldBe BoundaryFailure.WRONG_ACTOR
        }

        test("current own library search reveals options without library order or future draws") {
            val base = fixture()
            val options = base.getLibrary(p1).take(2)
            fun search(state: GameState, choices: List<EntityId>) = ask(state) { id ->
                SearchLibraryDecision(id, p1, "Find a card", DecisionContext(), choices, 0, 1,
                    choices.associateWith { info(state, it) }, "basic land")
            }
            val before = input(search(base, options))
            val changed = base.copy(zones = base.zones + (ZoneKey(p1, Zone.LIBRARY) to base.getLibrary(p1).reversed()))
            val after = input(search(changed, options.reversed()))
            before.canonicalJson() shouldBe after.canonicalJson()
            before.observation.decisionCards.map { it.entityId }.toSet() shouldBe options.toSet()
            before.observation.zones.single { it.ownerId == p1 && it.zoneType == Zone.LIBRARY }.cards shouldBe emptyList()
            (before.decision as SearchLibraryDecision).options shouldContainExactly options.sortedBy { it.value }
            base.getEntity(options.first())!!.get<RevealedToComponent>() shouldBe null
        }

        test("reorder decision preserves its authorized top group but omits unseen remainder") {
            val base = fixture()
            val top = base.getLibrary(p1).take(2)
            val state = ask(base) { id -> ReorderLibraryDecision(id, p1, "Reorder two", DecisionContext(),
                top, top.associateWith { info(base, it) }) }
            val result = input(state)
            (result.decision as ReorderLibraryDecision).cards shouldContainExactly top
            result.observation.decisionCards.map { it.entityId }.toSet() shouldBe top.toSet()
            result.canonicalJson().contains(base.getLibrary(p1).last().value) shouldBe false
        }

        test("raw face-down selection metadata is masked while the public option survives") {
            val base = fixture()
            val permanent = base.getBattlefield().first { base.getEntity(it)?.get<CardComponent>()?.name == "Hill Giant" }
            val facedown = base.updateEntity(permanent) { it.with(FaceDownComponent) }
            val state = ask(facedown) { id -> SelectCardsDecision(id, p1, "Choose a permanent", DecisionContext(),
                listOf(permanent), 1, 1, cardInfo = mapOf(permanent to info(base, permanent))) }
            val result = input(state)
            val decision = result.decision as SelectCardsDecision
            decision.options shouldContainExactly listOf(permanent)
            decision.cardInfo!![permanent]!!.name shouldBe "Face-down creature"
            decision.cardInfo!![permanent]!!.imageUri shouldBe null
            result.observation.zones.flatMap { it.cards }.single { it.entityId == permanent }.let {
                it.cardDefinitionId shouldBe null
                it.power shouldBe 2
                it.toughness shouldBe 2
            }
        }

        test("unauthorized opponent-library cardInfo and extra metadata keys fail visibly") {
            val base = fixture()
            val hidden = base.getLibrary(p2).first()
            val invalid = ask(base) { id -> SelectCardsDecision(id, p1, "Invalid look", DecisionContext(),
                listOf(hidden), 1, 1, cardInfo = mapOf(hidden to info(base, hidden))) }
            shouldThrow<ObservationBoundaryException> { input(invalid) }.failure shouldBe BoundaryFailure.UNAUTHORIZED_LOOK
            val extra = ask(base) { id -> SelectCardsDecision(id, p1, "Invalid info", DecisionContext(),
                emptyList(), 0, 0, cardInfo = mapOf(hidden to info(base, hidden))) }
            shouldThrow<ObservationBoundaryException> { input(extra) }.failure shouldBe BoundaryFailure.UNAUTHORIZED_LOOK
        }

        test("complete nested target sacrifice discard and mode domains survive detached projection") {
            val base = fixture()
            val card = base.getHand(p1).single()
            val target = base.getBattlefield().first()
            val mutableTargets = mutableListOf(target)
            val targetInfo = TargetInfo(0, "creature", 1, 2, mutableTargets, mustDifferFromEarlier = true)
            val costs = AdditionalCostData("Sacrifice and discard", "fixture",
                validSacrificeTargets = listOf(target), sacrificeCount = 1,
                validDiscardTargets = listOf(card), discardCount = 1)
            val legal = LegalAction(CastSpell(p1, card), "FixtureCast", "Explicit parameter fixture",
                validTargets = mutableTargets, requiresTargets = true, targetCount = 2, minTargets = 1,
                targetRequirements = listOf(targetInfo), hasXCost = true, minX = 1, maxAffordableX = 5,
                additionalCostInfo = costs,
                modalEnumeration = ModalLegalEnumeration(2, 1, false, modes = listOf(
                    ModalEnumerationMode(0, "First", true, additionalCostInfo = costs, targetRequirements = listOf(targetInfo))
                ), unavailableIndices = listOf(1)))
            val result = adapter.build(base, p1, listOf(legal), epoch, 991)
            val copied = result.legalActions.single()
            copied.targetRequirements!!.single().mustDifferFromEarlier shouldBe true
            copied.additionalCostInfo!!.validDiscardTargets shouldContainExactly listOf(card)
            copied.modalEnumeration!!.unavailableIndices shouldContainExactly listOf(1)
            copied.minX shouldBe 1
            copied.maxAffordableX shouldBe 5
            mutableTargets.clear()
            copied.validTargets shouldContainExactly listOf(target)
            result.verifyBinding(epoch, p1)
        }

        test("inaccessible nested cost reference is rejected instead of removed") {
            val base = fixture()
            val costs = AdditionalCostData("Invalid hidden discard", "fixture",
                validDiscardTargets = listOf(base.getHand(p2).first()), discardCount = 1)
            val invalid = LegalAction(PassPriority(p1), "Fixture", "Invalid", additionalCostInfo = costs)
            shouldThrow<ObservationBoundaryException> {
                adapter.build(base, p1, listOf(invalid), epoch, 991)
            }.failure shouldBe BoundaryFailure.INACCESSIBLE_REFERENCE
        }

        test("very large mandatory numeric domain is copied without enumeration or auto-answer") {
            val state = ask(fixture()) { id -> ChooseNumberDecision(id, p1, "Choose number", DecisionContext(), 0, Int.MAX_VALUE) }
            val result = input(state)
            (result.decision as ChooseNumberDecision).maxValue shouldBe Int.MAX_VALUE
            result.legalActions shouldBe emptyList()
        }

        test("target constraints and nonselectable look cards are retained") {
            val base = fixture()
            val visible = base.getBattlefield().first()
            val targetQuestion = ask(base) { id -> ChooseTargetsDecision(id, p1, "Choose targets", DecisionContext(),
                listOf(TargetRequirementInfo(0, "two targets", 1, 2, sameOwner = true,
                    totalManaValueAtMost = 4, differentNames = true, differentControllers = false)), mapOf(0 to listOf(visible))) }
            (input(targetQuestion).decision as ChooseTargetsDecision).targetRequirements.single().let {
                it.sameOwner shouldBe true
                it.totalManaValueAtMost shouldBe 4
                it.differentNames shouldBe true
            }
            val library = base.getLibrary(p1)
            val lookQuestion = ask(base) { id -> SelectCardsDecision(id, p1, "Choose a permanent", DecisionContext(),
                listOf(library[0]), 0, 1, cardInfo = library.take(2).associateWith { info(base, it) },
                nonSelectableOptions = listOf(library[1]), onePerCardName = true, maxTotalManaValue = 6) }
            (input(lookQuestion).decision as SelectCardsDecision).let {
                it.nonSelectableOptions shouldContainExactly listOf(library[1])
                it.onePerCardName shouldBe true
                it.maxTotalManaValue shouldBe 6
            }
        }

        test("pending mana payment includes actual mana ability menu and rejects ordinary spells") {
            val base = fixture()
            val state = ask(base) { id -> SelectManaSourcesDecision(id, p1, "Pay mana", DecisionContext(),
                emptyList(), "{B}", emptyList(), canDecline = true) }
            val menu = enumerator.enumerateManaAbilities(state, p1)
            menu.isNotEmpty() shouldBe true
            val result = adapter.build(state, p1, menu, epoch, 991)
            result.legalActions.size shouldBe menu.size
            shouldThrow<ObservationBoundaryException> {
                adapter.build(state, p1, listOf(LegalAction(PassPriority(p1), "PassPriority", "Pass")), epoch, 991)
            }.failure shouldBe BoundaryFailure.INCOMPLETE_INPUT
        }

        test("mulligan public counts do not expose another hand's pending pregame cards") {
            val base = fixture()
            val changed = base.updateEntity(p2) { it.with(MulliganStateComponent(mulligansTaken = 1,
                pendingLeylineCardIds = listOf(base.getHand(p2).first()))) }
            val result = input(changed)
            result.observation.turnResources.single { it.playerId == p2 }.mulligansTaken shouldBe 1
            result.canonicalJson().contains(base.getHand(p2).first().value) shouldBe false
        }

        test("public spell X and chosen modes are retained") {
            val base = fixture()
            val spell = base.getHand(p2).first()
            val state = base.removeFromZone(ZoneKey(p2, Zone.HAND), spell)
                .updateEntity(spell) { it.with(SpellOnStackComponent(p2, xValue = 3, chosenModes = listOf(2, 0))) }
                .copy(stack = listOf(spell))
            input(state).observation.stack.single().let {
                it.xValue shouldBe 3
                it.chosenModes shouldContainExactly listOf(2, 0)
            }
        }

        test("epoch actor menu and policy-state tampering invalidate response binding") {
            val result = input(fixture())
            result.verifyBinding(epoch, p1)
            listOf(result.copy(epoch = epoch.copy(step = 8)), result.copy(actorId = p2),
                result.copy(legalActions = emptyList()), result.copy(policyRngState = 992)).forEach { changed ->
                shouldThrow<ObservationBoundaryException> { changed.verifyBinding(epoch, p1) }.failure shouldBe BoundaryFailure.STALE_INPUT
            }
        }

        test("empty ordinary menu and terminal state fail rather than inventing a pass or winner") {
            val base = fixture()
            shouldThrow<ObservationBoundaryException> { adapter.build(base, p1, emptyList(), epoch, 991) }
                .failure shouldBe BoundaryFailure.INCOMPLETE_INPUT
            shouldThrow<ObservationBoundaryException> { input(base.copy(gameOver = true, winnerId = p2)) }
                .failure shouldBe BoundaryFailure.TERMINAL_STATE
        }

        test("raw authority objects and continuations are rejected by payload graph audit") {
            val base = fixture()
            shouldThrow<ObservationBoundaryException> { requireAccessible(base, setOf(p1, p2)) }
                .failure shouldBe BoundaryFailure.UNSUPPORTED_SCHEMA
            shouldThrow<ObservationBoundaryException> { requireAccessible(LegendRuleContinuation(p1, emptyList()), setOf(p1)) }
                .failure shouldBe BoundaryFailure.UNSUPPORTED_SCHEMA
        }

        test("all eighteen current decision variants cross as complete typed questions") {
            val base = fixture()
            val hand = base.getHand(p1)
            val permanent = base.getBattlefield().first()
            val top = base.getLibrary(p1).take(1)
            val context = DecisionContext()
            val factories: List<(String) -> PendingDecision> = listOf(
                { id -> ChooseTargetsDecision(id, p1, "Targets", context, listOf(TargetRequirementInfo(0, "target")), mapOf(0 to listOf(permanent))) },
                { id -> SelectCardsDecision(id, p1, "Cards", context, hand, 1, 1) },
                { id -> YesNoDecision(id, p1, "Optional", context) },
                { id -> BatchYesNoDecision(id, p1, "Repeated optional", context, count = 2) },
                { id -> ChooseModeDecision(id, p1, "Mode", context, listOf(ModeOption(0, "First"), ModeOption(1, "Second")), 1, 2) },
                { id -> ChooseColorDecision(id, p1, "Color", context, setOf(Color.RED, Color.BLACK)) },
                { id -> ChooseNumberDecision(id, p1, "Number", context, 1, 99) },
                { id -> DistributeDecision(id, p1, "Distribute", context, 4, listOf(permanent), 1, mapOf(permanent to 4), true) },
                { id -> OrderObjectsDecision(id, p1, "Order", context, hand) },
                { id -> SplitPilesDecision(id, p1, "Piles", context, hand, 2, listOf("Keep", "Return")) },
                { id -> ChooseOptionDecision(id, p1, "Option", context, listOf("First", "Second"), optionCardIds = mapOf(0 to hand),
                    optionMetadata = listOf(OptionMetadata("a", "Meaningful first option"), OptionMetadata("b")), canCancel = true) },
                { id -> ChooseReplacementDecision(id, p1, "Replacement", context, listOf("red"), listOf("black", "green"), allowedToByFrom = listOf(listOf(0, 1)), defaultFromIndex = 0) },
                { id -> AssignDamageDecision(id, p1, "Damage", context, permanent, 3, emptyList(), p2, emptyMap(), mapOf(p2 to 3), true, false) },
                { id -> SearchLibraryDecision(id, p1, "Search", context, top, 0, 1, top.associateWith { info(base, it) }, "land") },
                { id -> ReorderLibraryDecision(id, p1, "Reorder", context, top, top.associateWith { info(base, it) }) },
                { id -> SelectManaSourcesDecision(id, p1, "Mana", context, emptyList(), "{1}", emptyList(), true) },
                { id -> BudgetModalDecision(id, p1, "Budget", context, 3, listOf(BudgetModeOption(1, "First"), BudgetModeOption(2, "Second"))) },
                { id -> CombatResolutionDecision(id, p1, "Combat", context, false, emptyList(), emptyList(),
                    listOf(ResolutionDefender(p2, ResolutionTargetKind.PLAYER, "Player2", 20)), emptyList()) },
            )
            factories.map { it("fixture").let { question -> question::class } }.toSet() shouldBe PendingDecision::class.sealedSubclasses.toSet()
            factories.forEach { factory ->
                val state = ask(base, factory)
                val result = input(state)
                result.decision!!::class shouldBe state.pendingDecision!!::class
                result.decision!!.id shouldBe state.pendingDecision!!.id
                result.verifyBinding(epoch, p1)
            }
        }

        test("real pre-block input identifies vigilant attacker and defender with an empty blocker template") {
            val game = scenario().withPlayers().withRngSeed(2026092610L)
                .withCardOnBattlefield(1, vigilantFixture.name)
                .withCardOnBattlefield(1, "Hill Giant", tapped = true)
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Mountain")
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS).build()
            val attacker = game.findPermanent(vigilantFixture.name)!!
            val blocker = game.findPermanent("Grizzly Bears")!!
            game.execute(DeclareAttackers(p1, mapOf(attacker to p2))).error shouldBe null
            repeat(8) {
                if (game.state.step != Step.DECLARE_BLOCKERS) {
                    game.state.pendingDecision shouldBe null
                    game.execute(PassPriority(game.state.priorityPlayerId!!)).error shouldBe null
                }
            }
            game.state.step shouldBe Step.DECLARE_BLOCKERS
            val before = input(game.state)
            before.actorId shouldBe p2
            (before.legalActions.single().action as DeclareBlockers).blockers shouldBe emptyMap()
            before.observation.combat.creatures.single().let {
                it.entityId shouldBe attacker
                it.attackingDefenderId shouldBe p2
                it.wasBlocked shouldBe false
            }
            before.observation.zones.flatMap { it.cards }.single { it.entityId == attacker }.tapped shouldBe false
            before.observation.combat.playersWhoDeclaredAttackers shouldContainExactly listOf(p1)
            game.execute(DeclareBlockers(p2, mapOf(blocker to listOf(attacker)))).error shouldBe null
            val after = input(game.state)
            after.observation.combat.creatures.single { it.entityId == attacker }.let {
                it.wasBlocked shouldBe true
                it.blockerIds shouldContainExactly listOf(blocker)
            }
            after.observation.combat.creatures.single { it.entityId == blocker }.blockingAttackerIds shouldContainExactly listOf(attacker)
            after.observation.combat.playersWhoDeclaredBlockers shouldContainExactly listOf(p2)
        }

        test("authorized nonbattlefield power survives power-constrained selection metadata") {
            val game = scenario().withPlayers().withRngSeed(2026092611L)
                .withCardInHand(1, "Hill Giant").withCardInLibrary(1, "Grizzly Bears")
                .withCardInGraveyard(1, "Craw Wurm").withCardInLibrary(2, "Island").build()
            val base = game.state
            val choices = base.getHand(p1) + base.getLibrary(p1) + base.getGraveyard(p1)
            val state = ask(base) { id -> SelectCardsDecision(id, p1, "Choose by power", DecisionContext(), choices,
                0, 3, cardInfo = choices.associateWith { info(base, it) }, onePerPower = true, maxTotalPower = 11) }
            val result = input(state)
            val selected = result.decision as SelectCardsDecision
            selected.onePerPower shouldBe true
            selected.maxTotalPower shouldBe 11
            selected.cardInfo!!.values.associate { it.name to it.power } shouldBe
                mapOf("Hill Giant" to 3, "Grizzly Bears" to 2, "Craw Wurm" to 6)
            hand(result, p1).single().power shouldBe 3
            result.observation.decisionCards.single().power shouldBe 2
            result.observation.zones.single { it.ownerId == p1 && it.zoneType == Zone.GRAVEYARD }.cards.single().power shouldBe 6
        }

        test("unlisted data types and enum values fail closed instead of using package fallbacks") {
            shouldThrow<ObservationBoundaryException> { requireAccessible(ActorUnreviewedPayload(p1), setOf(p1)) }
                .failure shouldBe BoundaryFailure.UNSUPPORTED_SCHEMA
            shouldThrow<ObservationBoundaryException> { requireAccessible(ActorUnreviewedEnum.SECRET, emptySet()) }
                .failure shouldBe BoundaryFailure.UNSUPPORTED_SCHEMA
        }
    }

    private fun fixture(): GameState {
        val game = scenario().withPlayers().withRngSeed(2026092609L)
            .withCardInHand(1, "Forest")
            .withCardInHand(2, "Mountain").withCardInHand(2, "Hill Giant")
            .withCardInLibrary(1, "Swamp").withCardInLibrary(1, "Plains").withCardInLibrary(1, "Island")
            .withCardInLibrary(2, "Mountain").withCardInLibrary(2, "Forest").withCardInLibrary(2, "Plains")
            .withCardOnBattlefield(1, "Swamp").withCardOnBattlefield(2, "Hill Giant")
            .withCardInExile(2, "Craw Wurm").build()
        return game.state.updateEntity(game.state.getExile(p2).single()) { it.with(FaceDownComponent) }
    }

    private fun input(state: GameState): ActorInput {
        val actor = state.pendingDecision?.playerId ?: state.priorityPlayerId!!
        val actions = when {
            ManaPaymentWindow.openFor(state, actor) != null -> enumerator.enumerateManaAbilities(state, actor)
            state.pendingDecision != null -> emptyList()
            else -> enumerator.enumerate(state, actor)
        }
        return adapter.build(state, actor, actions, epoch, 991)
    }

    private fun ask(state: GameState, question: (String) -> PendingDecision): GameState =
        state.suspendForDecision(question, LegendRuleContinuation(p1, emptyList())).state

    private fun info(state: GameState, id: EntityId): SearchCardInfo = state.getEntity(id)!!.require<CardComponent>().let {
        SearchCardInfo(it.name, it.manaCost.toString(), it.typeLine.toString(), it.imageUri, power = it.baseStats?.basePower)
    }

    private fun hand(input: ActorInput, player: EntityId) = input.observation.zones
        .single { it.ownerId == player && it.zoneType == Zone.HAND }.cards
}

private data class ActorUnreviewedPayload(val entityId: EntityId)
private enum class ActorUnreviewedEnum { SECRET }
