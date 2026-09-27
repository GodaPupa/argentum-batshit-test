package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.LastKnownPermanentComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Fixed public-projection fixtures. Real casts/activations exercise the actual departure capture.
 * Explicitly labeled malformed/synthetic snapshots are never executed as a game or scored.
 * The generic boost is not Ferocity and creates no return trigger or Aura implementation.
 */
class ActorStackSourceObservationTest : ScenarioTestBase() {
    private val p1 = EntityId.of("player-1")
    private val p2 = EntityId.of("player-2")
    private val epoch = ActorEpoch("ferocity-source-observation-v3", "fixed-stack-source", 3)
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator(cardRegistry, ManaSolver(cardRegistry),
        CostCalculator(cardRegistry), PredicateEvaluator(), ConditionEvaluator(), TurnManager(cardRegistry))
    private val boost = card("Ferocity Source Observation Boost Fixture") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            val creature = target("target creature", Targets.Creature)
            effect = Effects.ModifyStats(1, 0, creature)
                .then(Effects.GrantKeyword(Keyword.DEATHTOUCH, creature))
                .then(Effects.GrantKeyword(Keyword.LIFELINK, creature))
        }
    }
    private val sacrificeSource = card("Ferocity Source Observation Sacrifice Fixture") {
        manaCost = "{B}"
        typeLine = "Artifact Creature — Construct"
        power = 2
        toughness = 3
        keywords(Keyword.DEATHTOUCH, Keyword.LIFELINK)
        activatedAbility { cost = Costs.SacrificeSelf; effect = Effects.GainLife(1) }
    }
    private val dyingSource = card("Ferocity Source Observation Death Fixture") {
        manaCost = "{B}"
        typeLine = "Creature — Construct"
        power = 2
        toughness = 3
        keywords(Keyword.DEATHTOUCH)
        activatedAbility { cost = Costs.SacrificeSelf; effect = Effects.GainLife(1) }
        triggeredAbility { trigger = Triggers.Dies; effect = Effects.GainLife(1) }
    }
    private val handSource = card("Ferocity Source Observation Hand Fixture") {
        manaCost = "{B}"
        typeLine = "Creature — Construct"
        power = 1
        toughness = 1
        activatedAbility {
            activateFromZone = Zone.HAND
            cost = Costs.DiscardSelf
            effect = Effects.GainLife(1)
        }
    }

    init {
        cardRegistry.register(listOf(boost, sacrificeSource, dyingSource, handSource))

        test("real pending Shaman activation reads current bonuses gained after activation") {
            val game = shamanGame(1)
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            game.activateShaman(shaman)
            val before = input(game.state).observation.stack.single().source!!
            before.mode shouldBe ActorSourceMode.LIVE_BATTLEFIELD
            before.originalObjectIsCurrent shouldBe true
            before.characteristics!!.power shouldBe 1
            before.characteristics.deathtouch shouldBe false
            before.characteristics.lifelink shouldBe false

            game.castSpell(1, boost.name, shaman).error shouldBe null
            game.resolveOne()
            val after = input(game.state).observation.stack.single().source!!
            after.origin shouldBe before.origin
            after.originalObjectIsCurrent shouldBe true
            after.mode shouldBe ActorSourceMode.LIVE_BATTLEFIELD
            after.characteristics!!.power shouldBe 2
            after.characteristics.toughness shouldBe 1
            after.characteristics.deathtouch shouldBe true
            after.characteristics.lifelink shouldBe true
        }

        test("two real queued Shaman activations keep old properties after a same-ID blink return") {
            val (state, shaman, origin) = blinkedShaman()
            state.stack.size shouldBe 2
            val current = state.objectRef(shaman)!!
            current shouldNotBe origin
            val observed = input(state)
            val live = observed.observation.zones.flatMap { it.cards }.single { it.entityId == shaman }
            live.power shouldBe 1
            live.toughness shouldBe 1
            ("DEATHTOUCH" in live.keywords) shouldBe false
            ("LIFELINK" in live.keywords) shouldBe false
            observed.observation.stack.forEach { item ->
                item.sourceId shouldBe shaman
                val source = item.source!!
                source.mode shouldBe ActorSourceMode.DEPARTED_BATTLEFIELD
                source.originalObjectIsCurrent shouldBe false
                source.origin shouldBe ActorObjectIdentity(origin.entityId, origin.generation)
                source.currentVisibleObject shouldBe ActorObjectIdentity(current.entityId, current.generation)
                source.characteristics!!.let {
                    it.name shouldBe "Krark-Clan Shaman"
                    it.controllerId shouldBe p1
                    it.ownerId shouldBe p1
                    it.power shouldBe 2
                    it.toughness shouldBe 1
                    it.colors shouldBe setOf("RED")
                    it.types shouldBe setOf("CREATURE")
                    it.deathtouch shouldBe true
                    it.lifelink shouldBe true
                }
            }
            observed.verifyBinding(epoch, observed.actorId)
        }

        test("actual sacrificed token source remains public after token entity cleanup") {
            val game = setup().withCardOnBattlefield(1, sacrificeSource.name, isToken = true).build()
            val sourceId = game.findPermanent(sacrificeSource.name)!!
            val original = game.state.objectRef(sourceId)!!
            game.execute(ActivateAbility(p1, sourceId, sacrificeSource.activatedAbilities.single().id)).error shouldBe null
            game.state.getEntity(sourceId) shouldBe null
            val source = input(game.state).observation.stack.single().source!!
            source.origin shouldBe ActorObjectIdentity(sourceId, original.generation)
            source.mode shouldBe ActorSourceMode.DEPARTED_BATTLEFIELD
            source.currentVisibleObject shouldBe null
            source.originalObjectIsCurrent shouldBe false
            source.characteristics!!.name shouldBe sacrificeSource.name
            source.characteristics.power shouldBe 2
            source.characteristics.toughness shouldBe 3
            source.characteristics.colors shouldBe setOf("BLACK")
            source.characteristics.types shouldBe setOf("ARTIFACT", "CREATURE")
            source.characteristics.deathtouch shouldBe true
            source.characteristics.lifelink shouldBe true
        }

        test("actual own death trigger has the departed source identity and public characteristics") {
            val game = setup().withCardOnBattlefield(1, dyingSource.name).build()
            val sourceId = game.findPermanent(dyingSource.name)!!
            val original = game.state.objectRef(sourceId)!!
            game.execute(ActivateAbility(p1, sourceId, dyingSource.activatedAbilities.single().id)).error shouldBe null
            game.state.pendingDecision shouldBe null
            val triggerId = game.state.stack.single { game.state.getEntity(it)!!.has<TriggeredAbilityOnStackComponent>() }
            val source = input(game.state).observation.stack.single { it.view.entityId == triggerId }.source!!
            source.origin shouldBe ActorObjectIdentity(sourceId, original.generation)
            source.originalObjectIsCurrent shouldBe false
            source.mode shouldBe ActorSourceMode.DEPARTED_BATTLEFIELD
            source.characteristics!!.name shouldBe dyingSource.name
            source.characteristics.deathtouch shouldBe true
            source.characteristics.lifelink shouldBe false
        }

        test("full pending-source input ignores unknown hand library identities order and authoritative RNG") {
            val (base) = blinkedShaman()
            var changed = base.copy(rng = GameRng(Long.MIN_VALUE))
            val hiddenHand = base.getHand(p2).first()
            val replacement = base.getEntity(base.getLibrary(p2).first())!!.require<CardComponent>()
            changed = changed.updateEntity(hiddenHand) { it.with(replacement) }
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

        test("synthetic absent uncaptured or wrong-entity source origins fail explicitly") {
            val game = shamanGame(1)
            val sourceId = game.findPermanent("Krark-Clan Shaman")!!
            game.activateShaman(sourceId)
            val entity = game.state.getEntity(game.state.stack.single())!!
            val activation = entity.require<ActivatedAbilityOnStackComponent>()
            val invalid = listOf(ObjectReferenceEnvironment(),
                activation.objectReferences.copy(captured = false),
                activation.objectReferences.copy(origin = ObjectRef(p2, 0)))
            invalid.forEach { references ->
                shouldThrow<ObservationBoundaryException> {
                    projectStackSource(game.state, entity.with(activation.copy(objectReferences = references)), emptyMap())
                }.failure shouldBe BoundaryFailure.INCOMPLETE_INPUT
            }
        }

        test("synthetic missing or wrong-incarnation LKI never falls back to the returned creature") {
            val (base, sourceId) = blinkedShaman()
            val stackId = base.stack.first()
            val activation = base.getEntity(stackId)!!.require<ActivatedAbilityOnStackComponent>()
            val old = activation.lastKnownSourceSnapshot!!
            activation.sourceBattlefieldTimestamp shouldBe null
            old.objectRef shouldBe activation.objectReferences.origin
            val withoutFallback = base.updateEntity(sourceId) { it.without<LastKnownPermanentComponent>() }
            val wrong = old.copy(objectRef = withoutFallback.objectRef(sourceId))
            listOf(null, wrong).forEach { snapshot ->
                val malformed = withoutFallback.updateEntity(stackId) {
                    // The initial ScenarioBuilder permanent has no entry timestamp. Retain
                    // explicit synthetic battlefield-origin proof while deleting/corrupting LKI;
                    // erasing every proof instead reaches the intentional unavailable-null path.
                    it.with(activation.copy(sourceBattlefieldTimestamp = 1L, lastKnownSourceSnapshot = snapshot))
                }
                shouldThrow<ObservationBoundaryException> { input(malformed) }.failure shouldBe BoundaryFailure.INCOMPLETE_INPUT
            }
        }

        test("synthetic partial last-known public fields fail rather than invent values") {
            val (base, sourceId) = blinkedShaman()
            val stackId = base.stack.first()
            val activation = base.getEntity(stackId)!!.require<ActivatedAbilityOnStackComponent>()
            val old = activation.lastKnownSourceSnapshot!!
            val withoutFallback = base.updateEntity(sourceId) { it.without<LastKnownPermanentComponent>() }
            listOf(old.copy(colors = null), old.copy(typeLine = null), old.copy(ownerId = null),
                old.copy(controllerId = null), old.copy(name = null)).forEach { snapshot ->
                val malformed = withoutFallback.updateEntity(stackId) {
                    it.with(activation.copy(lastKnownSourceSnapshot = snapshot))
                }
                shouldThrow<ObservationBoundaryException> { input(malformed) }.failure shouldBe BoundaryFailure.INCOMPLETE_INPUT
            }
        }

        test("synthetic face-down departure summary omits raw identity definition and private attachments") {
            val game = setup().withCardOnBattlefield(1, sacrificeSource.name).build()
            val id = game.findPermanent(sacrificeSource.name)!!
            val original = game.state.objectRef(id)!!
            val snapshot = EntitySnapshot.fromProjection(id, game.state).copy(wasFaceDown = true,
                name = "Private identity A", cardDefinitionId = "Private-A", copyOfOriginalName = "Private original A",
                attachmentIds = listOf(EntityId.of("private-attachment-A")))
            val moved = game.state.moveToZone(id, ZoneKey(p1, Zone.BATTLEFIELD), ZoneKey(p1, Zone.HAND))
            val first = syntheticAbility(id, original, snapshot)
            val second = syntheticAbility(id, original, snapshot.copy(name = "Private identity B", cardDefinitionId = "Private-B",
                copyOfOriginalName = "Private original B", attachmentIds = listOf(EntityId.of("private-attachment-B"))))
            val visible = input(moved).observation.zones.flatMap { it.cards }.associateBy { it.entityId }
            (id in visible) shouldBe true
            val a = projectStackSource(moved, first, visible)!!
            val b = projectStackSource(moved, second, visible)!!
            a shouldBe b
            a.characteristics!!.name shouldBe "Face-down creature"
            a.characteristics.faceDown shouldBe true
            a.currentVisibleObject shouldBe null
            requireAccessible(a, setOf(p1, id))
        }

        test("synthetic nonbattlefield ability exposes no private origin generation or fabricated characteristics") {
            val game = setup().withCardInHand(1, "Grizzly Bears").build()
            val id = game.state.getHand(p1).single()
            val original = game.state.objectRef(id)!!
            val entity = ComponentContainer.of(ActivatedAbilityOnStackComponent(id, "Public ability", p1, Effects.GainLife(1),
                objectReferences = ObjectReferenceEnvironment(captured = true, origin = original, source = original)))
            projectStackSource(game.state, entity, emptyMap()) shouldBe null
        }

        test("actual hand activation input is invariant to hidden hand and library incarnation permutations") {
            val base = setup().withCardInHand(1, handSource.name).build().state
            val sourceId = base.getHand(p1).single()
            val hidden = base.getHand(p1) + base.getHand(p2) + base.getLibrary(p1) + base.getLibrary(p2)
            val generations = hidden.map { base.objectIdentities.getValue(it).generation }
            // A permutation retains uniqueness and the allocator bound while changing hidden
            // acquisition order. No public identity, card, zone membership or RNG value changes.
            val identities = base.objectIdentities.toMutableMap()
            hidden.forEachIndexed { index, id ->
                identities[id] = identities.getValue(id).copy(generation = generations[(index + 1) % generations.size])
            }
            val changed = base.copy(objectIdentities = identities.toMap())
            changed.objectRef(sourceId) shouldNotBe base.objectRef(sourceId)
            val action = ActivateAbility(p1, sourceId, handSource.activatedAbilities.single().id)
            val first = actionProcessor.process(base, action).result
            val second = actionProcessor.process(changed, action).result
            first.error shouldBe null
            second.error shouldBe null
            first.state.stack.size shouldBe 1
            second.state.stack.size shouldBe 1
            val a = input(first.state)
            val b = input(second.state)
            a.observation.stack.single().sourceId shouldBe sourceId
            a.observation.stack.single().source shouldBe null
            b.observation.stack.single().source shouldBe null
            a.canonicalJson() shouldBe b.canonicalJson()
        }

        test("synthetic stale snapshot cannot override properties of the still-current original") {
            val game = shamanGame(1)
            val sourceId = game.findPermanent("Krark-Clan Shaman")!!
            game.activateShaman(sourceId)
            val stackId = game.state.stack.single()
            val activation = game.state.getEntity(stackId)!!.require<ActivatedAbilityOnStackComponent>()
            val stale = EntitySnapshot.fromProjection(sourceId, game.state).copy(power = 99,
                keywords = setOf("DEATHTOUCH", "LIFELINK"))
            val changed = game.state.updateEntity(stackId) { it.with(activation.copy(lastKnownSourceSnapshot = stale)) }
            val source = input(changed).observation.stack.single().source!!
            source.mode shouldBe ActorSourceMode.LIVE_BATTLEFIELD
            source.originalObjectIsCurrent shouldBe true
            source.characteristics!!.power shouldBe 1
            source.characteristics.deathtouch shouldBe false
            source.characteristics.lifelink shouldBe false
        }
    }

    private fun setup() = scenario().withPlayers().withRngSeed(2026092641L)
        .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Swamp")
        .withCardInLibrary(2, "Mountain").withCardInLibrary(2, "Island")
        .withCardInHand(2, "Craw Wurm")

    private fun shamanGame(artifactCount: Int): TestGame {
        val builder = setup().withCardOnBattlefield(1, "Krark-Clan Shaman")
            .withCardInHand(1, boost.name).withCardInHand(1, "Momentary Blink")
            .withLandsOnBattlefield(1, "Plains", 2)
        repeat(artifactCount) { builder.withCardOnBattlefield(1, "Bonesplitter") }
        return builder.build()
    }

    private fun TestGame.activateShaman(id: EntityId) {
        val fodder = findPermanent("Bonesplitter")!!
        val ability = cardRegistry.requireCard("Krark-Clan Shaman").activatedAbilities.single().id
        execute(ActivateAbility(p1, id, ability,
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder)))).error shouldBe null
    }

    private fun TestGame.resolveOne() {
        val initialSize = state.stack.size
        passPriority().error shouldBe null
        passPriority().error shouldBe null
        state.pendingDecision shouldBe null
        state.stack.size shouldBe initialSize - 1
        state.gameOver shouldBe false
    }

    private fun blinkedShaman(): Triple<GameState, EntityId, ObjectRef> {
        val game = shamanGame(2)
        val shaman = game.findPermanent("Krark-Clan Shaman")!!
        val origin = game.state.objectRef(shaman)!!
        game.castSpell(1, boost.name, shaman).error shouldBe null
        game.resolveOne()
        repeat(2) { game.activateShaman(shaman) }
        game.castSpell(1, "Momentary Blink", shaman).error shouldBe null
        game.resolveOne()
        game.state.getBattlefield().filter { it == shaman } shouldContainExactly listOf(shaman)
        return Triple(game.state, shaman, origin)
    }

    private fun input(state: GameState): ActorInput {
        state.pendingDecision shouldBe null
        val actor = state.priorityPlayerId!!
        return adapter.build(state, actor, enumerator.enumerate(state, actor), epoch, 401)
    }

    private fun syntheticAbility(id: EntityId, origin: ObjectRef, snapshot: EntitySnapshot) = ComponentContainer.of(
        ActivatedAbilityOnStackComponent(id, "Public ability", p1, Effects.GainLife(1), sourceBattlefieldTimestamp = 1,
            lastKnownSourceSnapshot = snapshot,
            objectReferences = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin)),
    )
}
