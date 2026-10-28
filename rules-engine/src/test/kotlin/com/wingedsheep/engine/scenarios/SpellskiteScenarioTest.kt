package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.handlers.*
import com.wingedsheep.engine.mechanics.layers.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.identity.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.assertions.withClue
import kotlinx.serialization.json.Json

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.targets.*
import io.kotest.matchers.shouldBe

/** Test-local Spellskite qualification; no discoverable production definition. */
class SpellskiteScenarioTest : ScenarioTestBase() {
    init {
        val skiteDefinition = card("Spellskite") {
            manaCost = "{2}"; typeLine = "Artifact Creature — Phyrexian Horror"; power = 0; toughness = 4
            activatedAbility {
                cost = Costs.Mana("{U/P}")
                val selectedStack = target("spell or ability", Targets.SpellOrAbility)
                effect = Effects.ChangeTargetTo(stackObject = selectedStack, recipient = EffectTarget.Self)
            }
        }

        for (kind in listOf("activation", "trigger")) for (targetCount in listOf(0, 1))
            for (payLife in listOf(false, true)) {
                test("Spellskite actual $kind targetCount=$targetCount payLife=$payLife") {
                    val definition = card("Spellskite") {
                        manaCost = "{2}"
                        typeLine = "Artifact Creature — Phyrexian Horror"
                        power = 0; toughness = 4
                        activatedAbility {
                            cost = Costs.Mana("{U/P}")
                            val selectedStack = target("spell or ability", Targets.SpellOrAbility)
                            effect = Effects.ChangeTargetTo(stackObject = selectedStack, recipient = EffectTarget.Self)
                        }
                    }
                    cardRegistry.register(definition)
                    val game = scenario().withPlayers("One", "Two").withPriorityPlayer(2)
                        .withCardOnBattlefield(1, "Grizzly Bears")
                        .withCardOnBattlefield(1, "Hill Giant")
                        .withCardOnBattlefield(2, "Spellskite")
                        .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Island").build()
                    val source = game.findPermanent("Grizzly Bears")!!
                    val old = game.findPermanent("Hill Giant")!!
                    val recipient = game.findPermanent("Spellskite")!!
                    val initial = game.state.updateEntity(game.player2Id) { it.with(ManaPoolComponent(blue = 1)) }
                    val selection = TargetSlotPlan(if (targetCount == 0) emptyList() else listOf(
                        TargetInstanceSelection(listOf(TargetSlot(ChosenTarget.Permanent(old), initial.objectRef(old))))))
                    val plan = CurrentTargetPlan(selection)
                    val requirements = if (targetCount == 0) emptyList() else listOf(Targets.Creature)
                    val refs = ObjectReferenceEnvironment(captured = true, origin = initial.objectRef(source), source = initial.objectRef(source))
                    val resolver = StackResolver(cardRegistry)
                    val effect = if (targetCount == 0) Effects.GainLife(0) else Effects.DealDamage(1, EffectTarget.ContextTarget(0))
                    val queuedRaw = if (kind == "activation") resolver.putActivatedAbility(initial,
                        ActivatedAbilityOnStackComponent(source, "Grizzly Bears", game.player1Id, effect,
                            objectReferences = refs, originalTargetSlots = selection), plan.targets, requirements, currentTargetPlan = plan.takeIf { targetCount != 0 })
                    else resolver.putTriggeredAbility(initial,
                        TriggeredAbilityOnStackComponent(source, "Grizzly Bears", game.player1Id, effect, "Card acceptance fixture",
                            objectReferences = refs, originalTargetSlots = selection), plan.targets, requirements, currentTargetPlan = plan.takeIf { targetCount != 0 })
                    // The producer intentionally omits TargetsComponent for no targets. Declare the
                    // empty authoritative fixture explicitly before exercising Spellskite.
                    val queued = if (targetCount == 0 && queuedRaw.error == null) queuedRaw.copy(
                        state = queuedRaw.state.updateEntity(queuedRaw.state.stack.last()) { c ->
                            c.with(TargetsComponent(emptyList())).withCurrentTargetPlan(null, plan)!!
                        }) else queuedRaw
                    queued.error shouldBe null
                    val originalStack = queued.state.stack.last()
                    val choices = if (payLife) listOf(Color.BLUE) else emptyList()
                    val action = ActivateAbility(game.player2Id, recipient, definition.activatedAbilities.single().id,
                        targets = listOf(ChosenTarget.Spell(originalStack)),
                        paymentStrategy = PaymentStrategy.Explicit(emptyList(), choices))
                    val activated = ActionProcessor(cardRegistry).process(queued.state, action).result
                    activated.error shouldBe null
                    activated.state.stack.size shouldBe 2
                    activated.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!.blue shouldBe (if (payLife) 1 else 0)
                    val paid = activated.events.filterIsInstance<LifeChangedEvent>().filter { it.reason == LifeChangeReason.PAYMENT }
                    paid.size shouldBe (if (payLife) 1 else 0)
                    if (payLife) { paid.single().oldLife shouldBe 20; paid.single().newLife shouldBe 18 }
                    val resolved = resolver.resolveTop(activated.state)
                    resolved.error shouldBe null
                    resolved.state.pendingDecision shouldBe null
                    resolved.state.stack shouldBe listOf(originalStack)
                    val resultPlan = resolved.state.getEntity(originalStack)!!.currentTargetPlan()!!
                    if (targetCount == 0) resultPlan shouldBe plan
                    else {
                        resultPlan.targets shouldBe listOf(ChosenTarget.Permanent(recipient))
                        resultPlan.slots.single().objectRef shouldBe initial.objectRef(recipient)
                    }
                    resolved.events.filterIsInstance<BecomesTargetEvent>().size shouldBe targetCount
                    resolved.events.filterIsInstance<LifeChangedEvent>().filter { it.reason == LifeChangeReason.PAYMENT }.size shouldBe 0
                }
            }
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        fun round(s: GameState): GameState = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), s))
        data class F(val state: GameState, val stack: EntityId, val source: EntityId, val old: EntityId,
            val recipient: EntityId, val one: EntityId, val two: EntityId, val kind: String, val plan: CurrentTargetPlan)
        fun fixture(kind: String, shape: String = "optional", recipientName: String = "Spellskite", players: Boolean = false, ward: Boolean = false, life: Int = 20,
            declaredGroups: List<List<Int>>? = null, declaredRequirements: List<TargetRequirement>? = null): F {
            val reqs: List<TargetRequirement> = declaredRequirements ?: listOf(
                AnyTarget(id = "omitted", optional = true, minCount = 0),
                if (players) TargetPlayer(id = "first") else TargetObject(id = "first", filter = TargetFilter(GameObjectFilter.Creature)),
                if (players) TargetPlayer(id = "last") else TargetObject(id = "last", filter = TargetFilter(GameObjectFilter.Creature)))
            val destroy = Effects.Destroy(EffectTarget.BoundVariable("last"))
            val body: Effect = when (shape) {
                "modal" -> ModalEffect(listOf(Mode(destroy, reqs)), chooseCount = 1)
                "splice" -> Effects.GainLife(1)
                "division" -> DividedDamageEffect(5)
                "damage" -> Effects.DealDamage(1, EffectTarget.ContextTarget(0))
                "queue", "context" -> Effects.GainLife(0)
                else -> destroy
            }
            val spell = card("Public Matrix Spell") { manaCost = "{0}"; typeLine = "Instant"; spell { effect = body } }
            val splice = card("Public Matrix Splice") {
                manaCost = "{0}"; typeLine = "Instant — Arcane"
                spell { target("omitted", reqs[0]); target("first", reqs[1]); target("last", reqs[2]); effect = destroy }
            }
            cardRegistry.register(spell); cardRegistry.register(splice)
            cardRegistry.register(skiteDefinition)
            val emitter = card("Spellskite Ward Fixture") {
                manaCost = "{2}"; typeLine = "Enchantment"
                staticAbility { ability = GrantWard(cost = WardCost.Life(2), filter = GroupFilter(GameObjectFilter.Creature.youControl())) }
            }
            cardRegistry.register(emitter)
            val game = scenario().withPlayers("One", "Two").withPriorityPlayer(2).withLifeTotal(2, life)
                .let { if (ward) it.withCardOnBattlefield(2, emitter.name) else it }
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant").withCardOnBattlefield(2, recipientName)
                .withCardInHand(1, spell.name).build()
            val source = game.findPermanent("Grizzly Bears")!!; val old = game.findPermanent("Hill Giant")!!
            val recipient = if (players) game.player2Id else game.findPermanent(recipientName)!!
            val target = if (players) ChosenTarget.Player(game.player1Id) else ChosenTarget.Permanent(old)
            val slot = TargetSlot(target, if (players) null else game.state.objectRef(old))
            val ids = listOf(old, recipient, source)
            val selections = if (declaredGroups != null) TargetSlotPlan(declaredGroups.map { group ->
                TargetInstanceSelection(group.map { index ->
                    TargetSlot(ChosenTarget.Permanent(ids[index]), game.state.objectRef(ids[index]))
                })
            }) else TargetSlotPlan(listOf(TargetInstanceSelection(emptyList()),
                TargetInstanceSelection(listOf(slot.copy(dividedAmount = if (shape == "division") 2 else null))),
                TargetInstanceSelection(listOf(slot.copy(dividedAmount = if (shape == "division") 3 else null)))))
            val plan = CurrentTargetPlan(selections, if (shape == "modal") listOf(3) else emptyList(),
                if (shape == "splice") listOf(3) else emptyList())
            val refs = ObjectReferenceEnvironment(captured = true, origin = game.state.objectRef(source), source = game.state.objectRef(source))
            val resolver = StackResolver(cardRegistry)
            val placed = when (kind) {
                "activation" -> resolver.putActivatedAbility(game.state,
                    ActivatedAbilityOnStackComponent(source, "Matrix Source", game.player1Id, body,
                        objectReferences = refs, originalTargetSlots = selections), plan.targets, reqs, currentTargetPlan = plan.takeIf { it.targets.isNotEmpty() })
                "trigger" -> resolver.putTriggeredAbility(game.state,
                    TriggeredAbilityOnStackComponent(source, "Matrix Source", game.player1Id, body, "Matrix trigger",
                        objectReferences = refs, originalTargetSlots = selections,
                        chosenModes = if (shape == "modal") listOf(0) else emptyList(),
                        modeTargetsOrdered = if (shape == "modal") listOf(plan.targets) else emptyList(),
                        modeTargetRequirements = if (shape == "modal") mapOf(0 to reqs) else emptyMap()),
                    plan.targets, reqs, currentTargetPlan = plan.takeIf { it.targets.isNotEmpty() })
                else -> resolver.castSpell(game.state, game.findCardsInHand(1, spell.name).single(), game.player1Id,
                    targets = plan.targets, targetRequirements = reqs,
                    chosenModes = if (shape == "modal") listOf(0) else emptyList(),
                    modeTargetsOrdered = if (shape == "modal") listOf(plan.targets) else emptyList(),
                    modeTargetRequirements = if (shape == "modal") mapOf(0 to reqs) else emptyMap(),
                    splicedCardNames = if (shape == "splice") listOf(splice.name) else emptyList())
            }
            placed.error shouldBe null
            val stack = placed.state.stack.last()
            // Test input is the original requirement-indexed declaration, never rebuilt from flat targets.
            val state = if (plan.targets.isEmpty()) placed.state.updateEntity(stack) { container ->
                val declared = container.with(TargetsComponent(plan.targets, reqs))
                val spell = declared.get<SpellOnStackComponent>()
                (if (spell != null) declared.with(spell.copy(originalTargetSlots = selections)) else declared)
                    .withCurrentTargetPlan(null, plan)!!
            } else if (kind == "instant") placed.state.updateEntity(stack) { container ->
                val original = container.get<SpellOnStackComponent>()!!
                container.with(original.copy(originalTargetSlots = selections)).withCurrentTargetPlan(null, plan)!!
            } else placed.state
            plan.isCoherent(state.getEntity(stack)!!) shouldBe true
            return F(state, stack, source, old, recipient, game.player1Id, game.player2Id, kind, plan)
        }
        fun original(s: GameState, f: F): TargetSlotPlan? {
            val c = s.getEntity(f.stack)!!
            return c.get<SpellOnStackComponent>()?.originalTargetSlots
                ?: c.get<ActivatedAbilityOnStackComponent>()?.originalTargetSlots
                ?: c.get<TriggeredAbilityOnStackComponent>()?.originalTargetSlots
        }
        fun offer(f: F, s: GameState = f.state): GameState {
            val activated = ActionProcessor(cardRegistry).process(s, ActivateAbility(f.two, f.recipient,
                skiteDefinition.activatedAbilities.single().id, targets = listOf(ChosenTarget.Spell(f.stack)),
                paymentStrategy = PaymentStrategy.Explicit(emptyList(), listOf(Color.BLUE)))).result
            activated.error shouldBe null
            activated.state.lifeTotal(f.two) shouldBe s.lifeTotal(f.two) - 2
            val resolved = StackResolver(cardRegistry).resolveTop(activated.state)
            resolved.error shouldBe null
            return round(resolved.state)
        }
        fun submit(f: F, s: GameState): ExecutionResult {
            val q = s.pendingDecision as ChooseOptionDecision
            q.playerId shouldBe f.two
            return ActionProcessor(cardRegistry).process(round(s), SubmitDecision(f.two, OptionChosenResponse(q.id, 1))).result
                .also { it.error shouldBe null }
        }
        fun verifyChanged(f: F, result: ExecutionResult, players: Boolean = false) {
            val c = result.state.getEntity(f.stack)!!; val plan = c.currentTargetPlan()!!
            val target = if (players) ChosenTarget.Player(f.recipient) else ChosenTarget.Permanent(f.recipient)
            withClue("SK_SELECTED_OCCURRENCE") { plan.targets shouldBe listOf(f.plan.targets[0], target) }
            plan.selections.instances[0].slots shouldBe emptyList()
            plan.selections.instances[1] shouldBe f.plan.selections.instances[1]
            plan.selections.slot(TargetSlotAddress(2, 0)).objectRef shouldBe if (players) null else f.state.objectRef(f.recipient)
            plan.slots.map { it.dividedAmount } shouldBe f.plan.slots.map { it.dividedAmount }
            original(result.state, f) shouldBe f.plan.selections
            plan.isCoherent(c) shouldBe true
            result.events.filterIsInstance<TargetsChosenEvent>().single().chooserId shouldBe f.two
            result.events.filterIsInstance<BecomesTargetEvent>().single().sourceEntityId shouldBe f.stack
        }
        for (kind in listOf("instant", "activation", "trigger")) {
            test("SK matrix optional public named resolution $kind") {
                val f = fixture(kind); val offered = offer(f)
                withClue("SK_ORIGINAL_GROUP_LABELS") { (offered.pendingDecision as ChooseOptionDecision).options shouldBe
                    listOf("Target group 2, choice 1", "Target group 3, choice 1") }
                val r = submit(f, offered); verifyChanged(f, r)
                val resolved = StackResolver(cardRegistry).resolveTop(round(r.state)); resolved.error shouldBe null
                (f.old in resolved.state.getBattlefield()) shouldBe true
                (f.recipient in resolved.state.getBattlefield()) shouldBe false
            }
            test("SK matrix division public occurrence amounts $kind") {
                val f = fixture(kind, "division"); val r = submit(f, offer(f)); verifyChanged(f, r)
                val c = r.state.getEntity(f.stack)!!
                val division = c.get<SpellOnStackComponent>()?.damageDistribution
                    ?: c.get<ActivatedAbilityOnStackComponent>()?.damageDistribution
                    ?: c.get<TriggeredAbilityOnStackComponent>()?.damageDistribution
                withClue("SK_DIVISION_MIRROR") { division shouldBe mapOf(f.old to 2, f.recipient to 3) }
                val resolved = StackResolver(cardRegistry).resolveTop(round(r.state)); resolved.error shouldBe null
                resolved.events.filterIsInstance<DamageDealtEvent>().associate { it.targetId to it.amount } shouldBe mapOf(f.old to 2, f.recipient to 3)
            }
        }
        for (kind in listOf("instant", "trigger")) test("SK matrix modal public original boundaries $kind") {
            val f = fixture(kind, "modal"); val r = submit(f, offer(f)); verifyChanged(f, r)
            val c = r.state.getEntity(f.stack)!!; val plan = c.currentTargetPlan()!!
            plan.modeInstanceCounts shouldBe listOf(3)
            val modes = c.get<SpellOnStackComponent>()?.chosenModes ?: c.get<TriggeredAbilityOnStackComponent>()!!.chosenModes
            val ordered = c.get<SpellOnStackComponent>()?.modeTargetsOrdered ?: c.get<TriggeredAbilityOnStackComponent>()!!.modeTargetsOrdered
            val requirements = c.get<SpellOnStackComponent>()?.modeTargetRequirements ?: c.get<TriggeredAbilityOnStackComponent>()!!.modeTargetRequirements
            modes shouldBe listOf(0); ordered shouldBe listOf(plan.targets)
            requirements.getValue(0) shouldBe f.state.getEntity(f.stack)!!.get<TargetsComponent>()!!.targetRequirements
            val resolved = StackResolver(cardRegistry).resolveTop(round(r.state)); resolved.error shouldBe null
            (f.old in resolved.state.getBattlefield()) shouldBe true
            (f.recipient in resolved.state.getBattlefield()) shouldBe false
        }
        test("SK matrix splice public original boundaries and tail") {
            val f = fixture("instant", "splice"); val r = submit(f, offer(f)); verifyChanged(f, r)
            val c = r.state.getEntity(f.stack)!!; val plan = c.currentTargetPlan()!!; val spell = c.get<SpellOnStackComponent>()!!
            plan.mainInstanceCount shouldBe 0; plan.spliceInstanceCounts shouldBe listOf(3)
            spell.splicedCardNames shouldBe listOf("Public Matrix Splice")
            spell.splicedTargetsOrdered shouldBe listOf(plan.targets)
            val before = r.state.lifeTotal(f.one)
            val resolved = StackResolver(cardRegistry).resolveTop(round(r.state)); resolved.error shouldBe null
            resolved.state.lifeTotal(f.one) shouldBe before + 1
            (f.old in resolved.state.getBattlefield()) shouldBe true
            (f.recipient in resolved.state.getBattlefield()) shouldBe false
            resolved.state.pendingDecision shouldBe null; resolved.state.continuationStack shouldBe emptyList()
        }
        // Every fixture declares its original requirement-indexed groups explicitly.
        fun shaped(groups: List<List<Int>>, reqs: List<TargetRequirement>, kind: String = "trigger", shape: String = "context"): F =
            fixture(kind, shape, declaredGroups = groups, declaredRequirements = reqs)
        fun activate(f: F, s: GameState = f.state, colors: List<Color> = listOf(Color.BLUE), repeats: Int = 1): ExecutionResult =
            ActionProcessor(cardRegistry).process(s, ActivateAbility(f.two, f.recipient,
                skiteDefinition.activatedAbilities.single().id, targets = listOf(ChosenTarget.Spell(f.stack)),
                repeatCount = repeats, paymentStrategy = PaymentStrategy.Explicit(emptyList(), colors))).result
        fun resolveSkite(f: F, s: GameState = f.state): ExecutionResult {
            val a = activate(f, s); a.error shouldBe null
            a.state.lifeTotal(f.two) shouldBe s.lifeTotal(f.two) - 2
            return StackResolver(cardRegistry).resolveTop(a.state).also { it.error shouldBe null }
        }
        fun word(f: F, id: EntityId, keyword: String): GameState = f.state.addFloatingEffect(Layer.ABILITY,
            SerializableModification.GrantKeyword(keyword), setOf(id), Duration.EndOfTurn,
            EffectContext(sourceId = f.source, controllerId = f.one))
        fun unchangedStack(f: F, r: ExecutionResult) {
            withClue("SK_NO_RESELECTION") { r.state.getEntity(f.stack) shouldBe f.state.getEntity(f.stack) }
            r.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
            r.state.pendingDecision shouldBe null
        }
        for (returned in listOf(false, true)) test("SK source departure returned=$returned") {
            val f = shaped(listOf(listOf(0)), listOf(Targets.Creature))
            val a = activate(f); a.error shouldBe null
            val from = a.state.logicalZone(f.recipient)!!; val away = ZoneKey(f.two, Zone.EXILE)
            var s = a.state.removeFromZone(from, f.recipient).addToZone(away, f.recipient)
            if (returned) s = s.removeFromZone(away, f.recipient).addToZone(from, f.recipient)
            val r = StackResolver(cardRegistry).resolveTop(round(s)); r.error shouldBe null
            unchangedStack(f, r)
        }
        test("SK same instance replace exactly one and reject duplicate") {
            val f = shaped(listOf(listOf(0, 2)), listOf(TargetCreature(count = 2)))
            val offered = resolveSkite(f); val q = offered.state.pendingDecision as ChooseOptionDecision
            q.options.size shouldBe 2
            val r = ActionProcessor(cardRegistry).process(offered.state, SubmitDecision(f.two, OptionChosenResponse(q.id, 0))).result
            r.error shouldBe null
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets shouldBe listOf(ChosenTarget.Permanent(f.recipient), ChosenTarget.Permanent(f.source))
            val again = f.copy(state = r.state, plan = r.state.getEntity(f.stack)!!.currentTargetPlan()!!)
            unchangedStack(again, resolveSkite(again))
        }
        test("SK cross instance duplicate retains old occurrence without retrigger") {
            val f = shaped(listOf(listOf(0), listOf(1)), listOf(Targets.Creature, Targets.Creature))
            val r = resolveSkite(f)
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets shouldBe List(2) { ChosenTarget.Permanent(f.recipient) }
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.selections.instances[1] shouldBe f.plan.selections.instances[1]
            r.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
        }
        test("SK another-target relation cannot invalidate unchanged later slot") {
            val f = shaped(listOf(listOf(0), listOf(1)), listOf(Targets.Creature, TargetOther(Targets.Creature)))
            unchangedStack(f, resolveSkite(f))
        }
        test("SK same controller whole group restriction") {
            val f = shaped(listOf(listOf(0, 2)), listOf(TargetCreature(count = 2, sameController = true)))
            unchangedStack(f, resolveSkite(f))
        }
        test("SK different controllers whole group restriction") {
            val f = shaped(listOf(listOf(0, 1)), listOf(TargetCreature(count = 2, differentControllers = true)))
            unchangedStack(f, resolveSkite(f))
        }
        for (keyword in listOf("HEXPROOF", "SHROUD", "PROTECTION_FROM_GREEN")) test("SK original source legality $keyword") {
            val base = shaped(listOf(listOf(0)), listOf(Targets.Creature))
            val f = base.copy(state = word(base, base.recipient, keyword))
            unchangedStack(f, resolveSkite(f))
        }
        test("SK dynamic count retains original declared count") {
            val f = shaped(listOf(listOf(0)), listOf(TargetObject(filter = TargetFilter.Creature,
                dynamicMaxCount = DynamicAmount.Fixed(0))))
            val r = resolveSkite(f)
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets shouldBe listOf(ChosenTarget.Permanent(f.recipient))
            original(r.state, f) shouldBe f.plan.selections
        }
        test("SK unchanged illegal target keeps its occurrence stamp") {
            val base = shaped(listOf(listOf(0), listOf(2)), listOf(Targets.Creature, Targets.Creature))
            val f = base.copy(state = word(base, base.source, "SHROUD"))
            val offered = offer(f); val q = offered.pendingDecision as ChooseOptionDecision
            val r = ActionProcessor(cardRegistry).process(offered, SubmitDecision(f.two, OptionChosenResponse(q.id, 0))).result
            r.error shouldBe null
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.selections.instances[1] shouldBe f.plan.selections.instances[1]
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.selections.instances[0].slots.single().target shouldBe ChosenTarget.Permanent(f.recipient)
        }
        test("SK same physical id different visits stay occurrence-specific") {
            val base = shaped(listOf(listOf(0), listOf(1)), listOf(Targets.Creature, Targets.Creature))
            val from = base.state.logicalZone(base.recipient)!!; val away = ZoneKey(base.two, Zone.EXILE)
            val s = base.state.removeFromZone(from, base.recipient).addToZone(away, base.recipient)
                .removeFromZone(away, base.recipient).addToZone(from, base.recipient)
            val f = base.copy(state = s)
            val r = resolveSkite(f)
            val slots = r.state.getEntity(f.stack)!!.currentTargetPlan()!!.slots
            slots[0].objectRef shouldBe s.objectRef(f.recipient)
            slots[1] shouldBe f.plan.slots[1]
            (slots[0].objectRef == slots[1].objectRef) shouldBe false
        }
        test("SK serialized choice rejects wrong identity and replays exactly") {
            val f = fixture("instant"); val offered = offer(f)
            val q = offered.pendingDecision as ChooseOptionDecision
            val processor = ActionProcessor(cardRegistry)
            val rejected = processor.process(round(offered), SubmitDecision(f.two, OptionChosenResponse("wrong", 0))).result
            (rejected.error != null) shouldBe true; rejected.state shouldBe round(offered); rejected.events shouldBe emptyList()
            val a = SubmitDecision(f.two, OptionChosenResponse(q.id, 1))
            val first = processor.process(offered, a).result; val replay = processor.process(round(offered), a).result
            first.error shouldBe null; replay.state shouldBe first.state; replay.events shouldBe first.events
            val stale = processor.process(first.state, a).result
            (stale.error != null) shouldBe true; stale.state shouldBe first.state; stale.events shouldBe emptyList()
        }
        for (colors in listOf(listOf(Color.RED), listOf(Color.BLUE, Color.BLUE))) test("SK invalid payment multiset $colors") {
            val f = shaped(listOf(listOf(0)), listOf(Targets.Creature))
            val r = activate(f, colors = colors)
            (r.error != null) shouldBe true; r.state shouldBe f.state; r.events shouldBe emptyList()
        }
        test("SK repeated activations debit each time") {
            val f = shaped(listOf(listOf(0)), listOf(Targets.Creature))
            val r = activate(f, repeats = 3); r.error shouldBe null
            r.state.lifeTotal(f.two) shouldBe 14; r.state.stack.size shouldBe 4
            r.events.filterIsInstance<LifeChangedEvent>().filter { it.reason == LifeChangeReason.PAYMENT }.size shouldBe 3
        }
        test("SK invalid stack target rolls payment back") {
            val f = fixture("instant")
            val r = ActionProcessor(cardRegistry).process(f.state, ActivateAbility(f.two, f.recipient,
                skiteDefinition.activatedAbilities.single().id, targets = listOf(ChosenTarget.Permanent(f.old)),
                paymentStrategy = PaymentStrategy.Explicit(emptyList(), listOf(Color.BLUE)))).result
            (r.error != null) shouldBe true; r.state shouldBe f.state; r.events shouldBe emptyList()
        }
        test("SK single spell redirects and deals damage to same visit") {
            val f = shaped(listOf(listOf(0)), listOf(Targets.Creature), "instant", "damage")
            val changed = resolveSkite(f)
            changed.state.getEntity(f.stack)!!.currentTargetPlan()!!.slots.single().objectRef shouldBe f.state.objectRef(f.recipient)
            val resolved = StackResolver(cardRegistry).resolveTop(changed.state); resolved.error shouldBe null
            val damage = resolved.events.filterIsInstance<DamageDealtEvent>()
            damage.size shouldBe 1; damage.single().targetId shouldBe f.recipient; damage.single().amount shouldBe 1
        }
        test("SK insufficient life leaves activation and payment unchanged") {
            val f = fixture("instant", life = 1)
            val r = activate(f)
            (r.error != null) shouldBe true; r.state shouldBe f.state; r.events shouldBe emptyList()
        }
        test("SK no reselection preserves complete stack object") {
            val f = shaped(listOf(listOf(1)), listOf(Targets.Creature))
            unchangedStack(f, resolveSkite(f))
        }
        for (pay in listOf(false, true)) test("SK actual targeting triggers ward pay=$pay") {
            val f = fixture("instant", "context", ward = true)
            val offered = offer(f); val q = offered.pendingDecision as ChooseOptionDecision
            val r = ActionProcessor(cardRegistry).process(offered, SubmitDecision(f.two, OptionChosenResponse(q.id, 1))).result
            r.error shouldBe null
            r.events.filterIsInstance<BecomesTargetEvent>().single().sourceEntityId shouldBe f.stack
            val ward = r.state.getEntity(r.state.stack.last())!!.get<TriggeredAbilityOnStackComponent>()!!
            ward.targetingSourceEntityId shouldBe f.stack
            val resolved = StackResolver(cardRegistry).resolveTop(r.state); resolved.error shouldBe null
            val question = resolved.state.pendingDecision as YesNoDecision; question.playerId shouldBe f.one
            val answered = ActionProcessor(cardRegistry).process(resolved.state, SubmitDecision(f.one, YesNoResponse(question.id, pay))).result
            answered.error shouldBe null
            answered.state.stack.contains(f.stack) shouldBe pay
            answered.state.lifeTotal(f.one) shouldBe (if (pay) 18 else 20)
            answered.state.getBattlefield().contains(f.recipient) shouldBe true
        }
        test("SK explicit life with no blue mana") {
            val base = shaped(listOf(listOf(0)), listOf(Targets.Creature))
            val f = base.copy(state = base.state.updateEntity(base.two) { it.with(ManaPoolComponent()) })
            val r = resolveSkite(f)
            r.state.lifeTotal(f.two) shouldBe 18
            r.state.getEntity(f.two)!!.get<ManaPoolComponent>()!!.blue shouldBe 0
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets shouldBe listOf(ChosenTarget.Permanent(f.recipient))
        }
        test("SK zero target spell still permits activation") {
            val f = shaped(emptyList(), emptyList(), "instant")
            unchangedStack(f, resolveSkite(f))
        }
        for (index in listOf(-1, 2)) test("SK illegal occurrence index preserves choice $index") {
            val f = fixture("instant"); val offered = offer(f); val q = offered.pendingDecision as ChooseOptionDecision
            val r = ActionProcessor(cardRegistry).process(offered, SubmitDecision(f.two, OptionChosenResponse(q.id, index))).result
            (r.error != null) shouldBe true; r.state shouldBe offered; r.events shouldBe emptyList()
        }
        for (kind in listOf("instant", "activation")) {
            val keywords = if (kind == "instant") listOf("HEXPROOF", "SHROUD") else listOf("HEXPROOF", "SHROUD", "PROTECTION_FROM_GREEN")
            for (keyword in keywords) test("SK explicit original source legality $kind $keyword") {
                val base = shaped(listOf(listOf(0)), listOf(Targets.Creature), kind)
                val f = base.copy(state = word(base, base.recipient, keyword))
                unchangedStack(f, resolveSkite(f))
            }
        }
    }
}
