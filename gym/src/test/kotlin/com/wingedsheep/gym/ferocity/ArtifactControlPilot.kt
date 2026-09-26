package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment

/**
 * Initial, untrained A3-F4/A3-N0 pilot. All numeric priorities below are tactical preferences
 * for choosing an action from public information. Only the engine determines game outcomes.
 * This class neither receives nor reconstructs hidden hands, library order, or game RNG.
 */
class ArtifactControlPilot {
    private data class Choice(val priority: Double, val action: GameAction, val reason: String)
    private val support = setOf("Ferocity of the Hunt", "Toxin Analysis", "Not Dead After All")
    private val ownedPool = setOf("Refurbished Familiar", "Baleful Strix", "Krark-Clan Shaman", "Myr Enforcer",
        "Ichor Wellspring", "Blood Fountain", "Nihil Spellbomb", "Thoughtcast", "Reckoner's Bargain",
        "Galvanic Blast", "Ferocity of the Hunt", "Toxin Analysis", "Not Dead After All", "Cast Down",
        "Makeshift Munitions", "Drossforge Bridge", "Mistvault Bridge", "Silverbluff Bridge",
        "Vault of Whispers", "Seat of the Synod", "Great Furnace", "Blood", "Clue", "Wicked Role", "Fish")

    fun choose(input: ActorInput): ActorProposal {
        input.verifyBinding(input.epoch, input.actorId)
        val cards = ActorPublicCards(input)
        require(cards.hand.all { it.name in ownedPool }) { "Artifact pilot encountered an unqualified hand card" }
        input.decision?.let { return ActorChoiceSupport.proposal(input, decision(input, cards, it)) }
        val menu = input.legalActions.filter { it.affordable }
        menu.firstOrNull { it.action is KeepHand }?.let {
            val landCount = cards.hand.count { it.land }
            val taken = cards.resources.mulligansTaken ?: 0
            val productive = cards.hand.any { it.name in setOf("Refurbished Familiar", "Baleful Strix",
                "Ichor Wellspring", "Thoughtcast", "Reckoner's Bargain", "Myr Enforcer") }
            val keep = when {
                taken >= 4 -> landCount in 1..5
                taken >= 2 -> landCount in 1..4 && productive
                else -> landCount in 2..4 && productive
            }
            val selected = if (keep) it else menu.firstOrNull { option -> option.action is TakeMulligan } ?: it
            return ActorChoiceSupport.proposal(input, selected.action)
        }
        menu.firstOrNull { it.action is BottomCards }?.let {
            val number = requireNotNull(cards.resources.cardsToBottom)
            val bottom = selectDiscard(cards, cards.hand.map { card -> card.entityId }, number)
            return ActorChoiceSupport.proposal(input, BottomCards(input.actorId, bottom))
        }
        menu.firstOrNull { it.action is DeclareAttackers }?.let {
            return ActorChoiceSupport.proposal(input, ActorCombatPlanner.attackers(input, it, false, ::creatureValue))
        }
        menu.firstOrNull { it.action is DeclareBlockers }?.let {
            return ActorChoiceSupport.proposal(input, ActorCombatPlanner.blockers(input, it, ::creatureValue))
        }

        val choices = mutableListOf<Choice>()
        for (option in menu) when (val action = option.action) {
            is PlayLand -> choices += Choice(85.0 + landPreference(cards, cards.card(action.cardId)), action, "land development")
            is CastSpell -> cast(input, cards, option)?.let(choices::add)
            is ActivateAbility -> activate(input, cards, option)?.let(choices::add)
            is PassPriority -> choices += Choice(0.0, action, "retain interaction or complete the priority window")
            else -> throw UnsupportedPolicyInput("Artifact pilot action ${action::class.simpleName} requires qualification")
        }
        val selected = choices.withIndex().maxWithOrNull(compareBy<IndexedValue<Choice>> { it.value.priority }
            .thenBy { -it.index })?.value ?: throw UnsupportedPolicyInput("Artifact pilot has no supported legal action")
        return ActorChoiceSupport.proposal(input, selected.action)
    }

    private fun cast(input: ActorInput, cards: ActorPublicCards, option: ActorLegalAction): Choice? {
        val card = cards.card((option.action as CastSpell).cardId)
        require(card.name in ownedPool) { "Unqualified artifact spell ${card.name}" }
        val targetDomain = targets(option)
        fun play(priority: Double, target: EntityId? = null, costs: AdditionalCostPayment? = null, reason: String) =
            Choice(priority, ActorChoiceSupport.materialize(input, option,
                if (target == null) emptyMap() else singleTarget(option, target), costs), reason)
        val emptyStack = input.observation.stack.isEmpty()
        return when (card.name) {
            "Galvanic Blast", "Cast Down" -> {
                val damage = if (card.name == "Galvanic Blast") if (cards.ownBoard.count { it.artifact } >= 3) 4 else 2 else Int.MAX_VALUE
                if (cards.opponentId in targetDomain && cards.opponentLife <= damage)
                    return play(1000.0, cards.opponentId, reason = "available lethal damage")
                val victim = targetDomain.mapNotNull(cards::cardOrNull).filter {
                    it.controllerId == cards.opponentId && it.creature &&
                        "INDESTRUCTIBLE" !in it.keywords && remaining(it) <= damage
                }.maxByOrNull(::creatureValue)
                if (victim != null) play(64.0 + creatureValue(victim) * 3 +
                    if (threatensPlayer(input, cards, victim)) 80 else 0, victim.entityId, reason = "remove a relevant threat")
                else if (cards.opponentId in targetDomain && (cards.opponentLife <= damage + 4 ||
                    (input.observation.activePlayerId == cards.opponentId && input.observation.step == Step.END)))
                    play(15.0, cards.opponentId, reason = "convert available late mana into clock") else null
            }
            in support -> supportSpell(input, cards, option, card)
            "Reckoner's Bargain" -> {
                val domains = requireNotNull(option.additionalCostInfo)
                val legal = domains.validSacrificeTargets.map(cards::card)
                val burn = pendingPlayerDamage(input, cards)
                val saving = legal.filter { cards.ownLife + it.manaValue > burn }
                    .minByOrNull { fodderCost(cards, it) }
                val fodder = if (burn >= cards.ownLife && saving != null) saving else legal.minByOrNull { fodderCost(cards, it) }
                    ?: return null
                val targeted = input.observation.stack.any { item -> item.view.controllerId == cards.opponentId &&
                    fodder.entityId in item.view.targets && killsCreature(item.view.name, fodder) }
                val value = if (burn >= cards.ownLife && saving != null) 900.0 else
                    45.0 - fodderCost(cards, fodder) * 7 + if (targeted) 55.0 else 0.0
                if (value <= 0 || (!emptyStack && !targeted && burn < cards.ownLife)) null else
                    play(value, costs = AdditionalCostPayment(sacrificedPermanents = listOf(fodder.entityId)),
                        reason = "draw and actual sacrificed mana-value life gain")
            }
            "Thoughtcast" -> if (emptyStack) play(60.0 - cards.hand.size, reason = "affinity card draw") else null
            "Refurbished Familiar" -> if (emptyStack) play(76.0, reason = "affinity threat and entry value") else null
            "Baleful Strix" -> if (emptyStack) play(74.0, reason = "cantrip evasive defense") else null
            "Myr Enforcer" -> if (emptyStack) play(78.0, reason = "credible affinity clock") else null
            "Krark-Clan Shaman" -> if (emptyStack) play(if (cards.ownBoard.any { it.name == card.name }) 25.0 else 62.0,
                reason = "available creature-based board control") else null
            "Ichor Wellspring" -> if (emptyStack) play(59.0, reason = "card-neutral artifact and later sacrifice draw") else null
            "Blood Fountain" -> if (emptyStack) play(48.0, reason = "two artifacts and later actual recovery") else null
            "Nihil Spellbomb" -> if (emptyStack) play(43.0, reason = "artifact development and graveyard interaction") else null
            "Makeshift Munitions" -> if (emptyStack && cards.ownBoard.none { it.name == card.name })
                play(32.0, reason = "reach and use of surplus artifacts") else null
            else -> throw UnsupportedPolicyInput("No artifact spell strategy for ${card.name}")
        }
    }

    private fun supportSpell(input: ActorInput, cards: ActorPublicCards, option: ActorLegalAction,
                             spell: EntityFeatures): Choice? {
        val candidates = targets(option).mapNotNull(cards::cardOrNull).filter {
            it.controllerId == input.actorId && it.creature && it.manaValue > 0
        }
        val ferocity = spell.name == "Ferocity of the Hunt"
        val toxin = spell.name == "Toxin Analysis"
        fun chosen(priority: Double, target: EntityFeatures, reason: String) = Choice(priority,
            ActorChoiceSupport.materialize(input, option, singleTarget(option, target.entityId)), reason)
        if (!toxin) {
            val threatened = candidates.filter { target -> !hasFerocity(cards, target) && input.observation.stack.any {
                it.view.controllerId == cards.opponentId && target.entityId in it.view.targets && killsCreature(it.view.name, target)
            } }.maxByOrNull(::creatureValue)
            if (threatened != null && creatureValue(threatened) >= 2.0 && !opponentCanExileGraveyard(cards))
                return chosen(110.0 + creatureValue(threatened) - (if (ferocity) 2.0 else 1.0), threatened,
                    "paid protection from a public death-producing effect")
        }
        if (spell.name == "Not Dead After All") return null
        val shaman = candidates.firstOrNull { it.name == "Krark-Clan Shaman" && "DEATHTOUCH" !in it.keywords }
        if (shaman != null &&
            input.observation.stack.none { it.view.name == "Krark-Clan Shaman" }) {
            val fodder = cards.ownBoard.filter { it.artifact }.minByOrNull { fodderCost(cards, it) }
            // Toxin creates a real Clue that may pay Shaman's cost. Ferocity creates no fodder.
            if (fodder != null || toxin) {
                val spendClue = toxin && (fodder == null || fodderCost(cards, fodder) > 1.3)
                val spent = if (spendClue) null else fodder?.entityId
                val ground = cards.allBoard.filter { it.creature && "FLYING" !in it.keywords && it.entityId != spent }
                val enemy = ground.filter { it.controllerId == cards.opponentId && "INDESTRUCTIBLE" !in it.keywords }
                val friendly = ground.filter { it.controllerId == input.actorId && "INDESTRUCTIBLE" !in it.keywords }
                val addsKills = enemy.any { remaining(it) > 1 }
                val returning = if (ferocity && !opponentCanExileGraveyard(cards)) creatureValue(shaman) else 0.0
                val gain = if (toxin) ground.size * (if (cards.ownLife <= 10) 1.5 else 0.35) else 0.0
                val net = enemy.sumOf(::creatureValue) - friendly.sumOf(::creatureValue) + returning + gain -
                    (if (toxin) 1.3 else 2.8) - (if (spendClue) 1.3 else fodder?.let { fodderCost(cards, it) } ?: 0.0)
                if (addsKills && net > 1.0) return chosen(72.0 + net, shaman,
                    if (toxin) "deathtouch sweep with actual lifelink and Clue" else "deathtouch sweep with paid source return")
            }
        }
        if (toxin) {
            val combat = input.observation.combat.creatures.filter { it.blockingAttackerIds.isNotEmpty() || it.attackingDefenderId != null }
            val fighter = candidates.filter { candidate -> combat.any { it.entityId == candidate.entityId } }
                .maxByOrNull { (it.power ?: 0) + creatureValue(it) }
            if (fighter != null && input.observation.step == Step.DECLARE_BLOCKERS) {
                val related = combat.first { it.entityId == fighter.entityId }
                val victims = (related.blockerIds + related.blockingAttackerIds).map(cards::card)
                if (victims.any { remaining(it) > (fighter.power ?: 0) && "INDESTRUCTIBLE" !in it.keywords } ||
                    (cards.ownLife <= 8 && (fighter.power ?: 0) > 0))
                    return chosen(96.0, fighter, "combat deathtouch/lifelink plus real Clue")
            }
        }
        return null
    }

    private fun activate(input: ActorInput, cards: ActorPublicCards, option: ActorLegalAction): Choice? {
        if (option.isManaAbility) return null // Ordinary casts use the qualified payment menu/solver.
        val source = cards.card((option.action as ActivateAbility).sourceId)
        fun play(priority: Double, ids: Map<Int, List<EntityId>> = emptyMap(), costs: AdditionalCostPayment? = null,
                 reason: String) = Choice(priority, ActorChoiceSupport.materialize(input, option, ids, costs), reason)
        return when (source.name) {
            "Krark-Clan Shaman" -> {
                val pending = input.observation.stack.filter { it.view.name == source.name && it.view.controllerId == input.actorId }
                // One deathtouch sweep is enough for ordinary destructible ground creatures.
                // Exact public LKI is required if the original Shaman has already departed.
                if (pending.any { requireNotNull(it.source).characteristics?.deathtouch == true }) return null
                val available = requireNotNull(option.additionalCostInfo).validSacrificeTargets.map(cards::card)
                    .sortedBy { fodderCost(cards, it) }
                if (available.isEmpty()) return null
                val deathtouch = "DEATHTOUCH" in source.keywords
                // Plan at most four ordinary pulses from actual remaining artifacts. Previously
                // queued ordinary damage is the baseline; do not credit its kills a second time.
                val before = pending.size
                val beforeKills = cards.allBoard.filter { it.creature && "FLYING" !in it.keywords &&
                    "INDESTRUCTIBLE" !in it.keywords && remaining(it) <= before }.map { it.entityId }.toSet()
                val plan = (1..minOf(if (deathtouch) 1 else 4, available.size)).map { extra ->
                    val payment = available.take(extra)
                    val ground = cards.allBoard.filter { it.creature && "FLYING" !in it.keywords &&
                        it.entityId !in payment.map { paid -> paid.entityId } }
                    val newDeaths = ground.filter { "INDESTRUCTIBLE" !in it.keywords &&
                        it.entityId !in beforeKills && (deathtouch || remaining(it) <= before + extra) }
                    val enemy = newDeaths.filter { it.controllerId == cards.opponentId }
                    val friends = newDeaths.filter { it.controllerId == input.actorId }
                    val returnCredit = if (hasFerocity(cards, source) && source in friends &&
                        !opponentCanExileGraveyard(cards)) creatureValue(source) else 0.0
                    val lifelink = if ("LIFELINK" in source.keywords) ground.size *
                        (if (cards.ownLife <= 10) 1.5 else 0.35) else 0.0
                    val benefit = enemy.sumOf(::creatureValue) - friends.sumOf(::creatureValue) +
                        returnCredit + lifelink - payment.sumOf { fodderCost(cards, it) }
                    extra to if (enemy.isEmpty()) Double.NEGATIVE_INFINITY else benefit
                }.maxWithOrNull(compareBy<Pair<Int, Double>> { it.second }.thenBy { -it.first })!!
                if (plan.second <= 0.8) null else play(90.0 + plan.second,
                    costs = AdditionalCostPayment(sacrificedPermanents = listOf(available.first().entityId)),
                    reason = "ground sweep with explicit remaining artifact budget and friendly casualties")
            }
            "Makeshift Munitions" -> {
                val fodder = requireNotNull(option.additionalCostInfo).validSacrificeTargets.map(cards::card)
                    .minByOrNull { fodderCost(cards, it) } ?: return null
                val target = targets(option).mapNotNull(cards::cardOrNull).filter {
                    it.controllerId == cards.opponentId && it.creature && remaining(it) <= 1 && "INDESTRUCTIBLE" !in it.keywords
                }.maxByOrNull(::creatureValue)
                if (target != null) play(68.0 + creatureValue(target) - fodderCost(cards, fodder), singleTarget(option, target.entityId),
                    AdditionalCostPayment(sacrificedPermanents = listOf(fodder.entityId)), "remove a one-toughness threat")
                else if (cards.opponentId in targets(option) && (cards.opponentLife <= 1 ||
                    (input.observation.step == Step.END && fodderCost(cards, fodder) <= 1.5)))
                    play(if (cards.opponentLife <= 1) 1000.0 else 18.0, singleTarget(option, cards.opponentId),
                        AdditionalCostPayment(sacrificedPermanents = listOf(fodder.entityId)), "actual reach") else null
            }
            "Blood Fountain" -> {
                val legal = targets(option).mapNotNull(cards::cardOrNull).filter { it.zone == Zone.GRAVEYARD && it.creature }
                    .sortedByDescending(::creatureValue)
                val req = option.targetRequirements?.singleOrNull() ?: if (option.requiresTargets) ActorTargetInfo(
                    0, option.targetDescription ?: "Return creature cards", option.minTargets, option.targetCount,
                    option.validTargets ?: throw UnsupportedPolicyInput("Blood Fountain target domain missing")
                ) else throw UnsupportedPolicyInput("Blood Fountain recovery lacks its target requirement")
                val chosen = legal.take(req.maxTargets)
                if (chosen.size < req.minTargets || chosen.isEmpty() || input.observation.stack.isNotEmpty()) null else
                    play(44.0 + chosen.sumOf(::creatureValue), mapOf(req.index to chosen.map { it.entityId }), reason = "paid creature recovery")
            }
            "Nihil Spellbomb" -> {
                val harmfulReturn = input.observation.stack.any { it.view.controllerId == cards.opponentId &&
                    it.view.name in setOf("Sneaky Snacker", "Blood Fountain", "Unearth", "Not Dead After All", "Ferocity of the Hunt") }
                if (harmfulReturn && cards.opponentId in targets(option)) play(125.0,
                    singleTarget(option, cards.opponentId), reason = "graveyard response before a return resolves") else null
            }
            "Clue" -> if (input.observation.stack.isEmpty() && cards.hand.size <= 4) play(26.0, reason = "pay for Clue draw") else null
            "Blood" -> {
                val domain = option.additionalCostInfo?.validDiscardTargets.orEmpty()
                val discard = selectDiscard(cards, domain, 1).singleOrNull() ?: return null
                if (discardValue(cards, cards.card(discard)) > 2.5 || input.observation.stack.isNotEmpty()) null else
                    play(22.0, costs = AdditionalCostPayment(discardedCards = listOf(discard)), reason = "paid filtering of excess card")
            }
            else -> throw UnsupportedPolicyInput("Artifact activation ${source.name} requires qualification")
        }
    }

    private fun decision(input: ActorInput, cards: ActorPublicCards, q: PendingDecision): GameAction {
        val source = q.context.sourceName
        val response = when (q) {
            is CombatResolutionDecision -> ActorCombatPlanner.combatDamage(input, q, ::creatureValue)
            is AssignDamageDecision -> ActorCombatPlanner.damage(q)
            is OrderObjectsDecision -> ActorCombatPlanner.order(input, q, ::creatureValue)
            is SelectCardsDecision -> {
                require(source in setOf("Refurbished Familiar", "Blood", "Reckoner's Bargain", "Krark-Clan Shaman",
                    "Makeshift Munitions", "Cleanup", null)) { "Artifact card-selection source $source unqualified" }
                val ids = if (q.options.all { cards.card(it).zone == Zone.HAND })
                    selectDiscard(cards, q.options, q.minSelections) else q.options.map(cards::card)
                    .sortedBy { fodderCost(cards, it) }.take(q.minSelections).map { it.entityId }
                CardsSelectedResponse(q.id, ids)
            }
            is ChooseTargetsDecision -> {
                require(source == "Blood Fountain") { "Artifact trigger targets for $source need qualification" }
                TargetsResponse(q.id, q.targetRequirements.associate { req -> req.index to
                    q.legalTargets.getValue(req.index).map(cards::card).sortedByDescending(::creatureValue)
                        .take(req.maxTargets).map { it.entityId } })
            }
            is YesNoDecision -> {
                require(source == "Nihil Spellbomb") { "Artifact consent for $source needs qualification" }
                YesNoResponse(q.id, canMakeBlack(cards))
            }
            is SelectManaSourcesDecision -> {
                val covered = q.autoPaySuggestion.isNotEmpty() || floatingCovers(cards, q.requiredCost)
                require(covered || q.canDecline) { "Mandatory mana payment requires an explicit mana-production strategy" }
                ManaSourcesSelectedResponse(q.id, autoPay = covered, declined = !covered)
            }
            is ChooseColorDecision -> {
                require(source in setOf("Blood Fountain", "Treasure")) { "Artifact color choice for $source unqualified" }
                ColorChosenResponse(q.id, listOf(Color.BLACK, Color.BLUE, Color.RED, Color.GREEN, Color.WHITE)
                    .first { it in q.availableColors })
            }
            is ChooseOptionDecision -> {
                require(source == "Triggered abilities") { "Artifact option source $source unqualified" }
                // Place return first, then draw, then Familiar discard: reverse resolution makes
                // the opponent discard before the last draw and preserves each actual trigger.
                val index = q.options.indices.minWithOrNull(compareBy<Int> {
                    when {
                        q.options[it].contains("Ferocity of the Hunt") || q.options[it].contains("Not Dead After All") -> 0
                        listOf("Krark-Clan Shaman", "Myr Enforcer").any { name -> q.options[it].contains(name) } &&
                            q.options[it].contains("When this creature dies, return it to the battlefield tapped") &&
                            q.options[it].contains("Wicked Role token attached to it") -> 0
                        q.options[it].contains("Ichor Wellspring") || q.options[it].contains("Baleful Strix") ||
                            q.options[it].contains("Nihil Spellbomb") || q.options[it].contains("Blood Fountain") -> 1
                        q.options[it].contains("Refurbished Familiar") -> 2
                        else -> throw UnsupportedPolicyInput("Unqualified simultaneous trigger ${q.options[it]}")
                    }
                }.thenBy { it }) ?: throw UnsupportedPolicyInput("Empty trigger order menu")
                OptionChosenResponse(q.id, index)
            }
            else -> throw UnsupportedPolicyInput("Artifact decision ${q::class.simpleName} needs qualification")
        }
        return ActorChoiceSupport.submit(input, response)
    }

    private fun targets(option: ActorLegalAction) = option.targetRequirements?.flatMap { it.validTargets }
        ?: option.validTargets.orEmpty()
    private fun singleTarget(option: ActorLegalAction, id: EntityId): Map<Int, List<EntityId>> =
        mapOf((option.targetRequirements?.single()?.index ?: 0) to listOf(id))
    private fun creatureValue(card: EntityFeatures): Double = when (card.name) {
        "Guttersnipe" -> 6.0; "Kessig Flamebreather" -> 4.7; "Baleful Strix" -> 3.3
        "Refurbished Familiar" -> 3.5; "Krark-Clan Shaman" -> 2.7; "Sneaky Snacker" -> 2.8
        "Myr Enforcer" -> 5.0
        else -> 1.0 + (card.power ?: 0) * 0.6 + (card.toughness ?: 0) * 0.35
    }
    private fun fodderCost(cards: ActorPublicCards, card: EntityFeatures): Double = when {
        card.name == "Ichor Wellspring" -> -1.0
        card.name == "Blood" -> 0.6
        card.name == "Clue" -> 1.3
        card.name == "Nihil Spellbomb" -> if (cards.input.observation.zones.any {
            it.ownerId == cards.opponentId && it.zoneType == Zone.GRAVEYARD && it.cards.any { c -> c.name == "Sneaky Snacker" }
        }) 2.6 else 1.0
        card.name == "Blood Fountain" -> if (cards.graveyard.count { it.creature } >= 2) 3.0 else 0.9
        card.land -> if (cards.ownBoard.count { it.land } <= 3) 9.0 else 3.5
        card.creature -> creatureValue(card)
        else -> 2.0
    }
    private fun selectDiscard(cards: ActorPublicCards, domain: List<EntityId>, number: Int): List<EntityId> {
        require(domain.size >= number)
        val available = domain.map(cards::card).toMutableList()
        val selected = mutableListOf<EntityId>()
        repeat(number) {
            val pick = available.minWith(compareBy<EntityFeatures> { discardValue(cards, it, selected) }
                .thenBy { it.entityId.toString() })
            selected += pick.entityId; available.remove(pick)
        }
        return selected
    }
    private fun discardValue(cards: ActorPublicCards, card: EntityFeatures, removed: List<EntityId> = emptyList()): Double {
        val hand = cards.hand.filter { it.entityId !in removed }
        if (card.land) return if (hand.count { it.land } > 3) 0.1 else 9.0 + landPreference(cards, card)
        if (card.name in support) return if (hand.count { it.name in support } > 1 ||
            cards.ownBoard.none { it.creature }) 0.4 else 2.5
        if (card.name == "Makeshift Munitions" && cards.ownBoard.any { it.name == card.name }) return 0.2
        return if (card.creature) creatureValue(card) + 2 else 3.0
    }
    private fun landPreference(cards: ActorPublicCards, card: EntityFeatures): Double {
        val colors = landColors(card.name)
        val existing = cards.ownBoard.filter { it.land }.flatMap { landColors(it.name) }.toSet()
        val missing = colors.count { it !in existing }
        val needRed = cards.hand.any { it.name in setOf("Galvanic Blast", "Krark-Clan Shaman") } && Color.RED in colors
        val immediateRed = !card.name.endsWith("Bridge") && Color.RED in colors &&
            cards.ownBoard.none { it.land && !it.tapped && Color.RED in landColors(it.name) } &&
            cards.hand.any { it.name == "Galvanic Blast" } &&
            (cards.opponentLife <= 4 || cards.opponentBoard.any { it.creature &&
                "INDESTRUCTIBLE" !in it.keywords && remaining(it) <= 4 })
        return missing * 3.0 + (if (needRed) 2 else 0) + (if (immediateRed) 20 else 0) -
            (if (card.name.endsWith("Bridge")) 1.5 else 0.0)
    }
    private fun landColors(name: String): Set<Color> = when (name) {
        "Drossforge Bridge" -> setOf(Color.BLACK, Color.RED)
        "Mistvault Bridge" -> setOf(Color.BLACK, Color.BLUE)
        "Silverbluff Bridge" -> setOf(Color.BLUE, Color.RED)
        "Vault of Whispers" -> setOf(Color.BLACK); "Seat of the Synod" -> setOf(Color.BLUE)
        "Great Furnace" -> setOf(Color.RED); else -> emptySet()
    }
    private fun hasFerocity(cards: ActorPublicCards, creature: EntityFeatures) = cards.ownBoard.any {
        it.name == "Ferocity of the Hunt" && it.attachedTo == creature.entityId
    }
    private fun opponentCanExileGraveyard(cards: ActorPublicCards) = cards.opponentBoard.any {
        it.name in setOf("Nihil Spellbomb", "Relic of Progenitus", "Soul-Guide Lantern")
    }
    private fun threatensPlayer(input: ActorInput, cards: ActorPublicCards, creature: EntityFeatures) =
        input.observation.combat.creatures.any { it.entityId == creature.entityId && it.attackingDefenderId == input.actorId } ||
            cards.ownLife <= (creature.power ?: 0) + 5
    private fun killsCreature(spell: String, target: EntityFeatures): Boolean = "INDESTRUCTIBLE" !in target.keywords &&
        (spell == "Cast Down" || spellDamage(spell) >= remaining(target))
    private fun pendingPlayerDamage(input: ActorInput, cards: ActorPublicCards) = input.observation.stack.sumOf {
        if (it.view.controllerId != cards.opponentId) 0 else when {
            it.view.name == "Guttersnipe" -> 2
            it.view.name == "Kessig Flamebreather" -> 1
            input.actorId in it.view.targets -> spellDamage(it.view.name)
            else -> 0
        }
    }
    private fun spellDamage(name: String) = when (name) {
        "Lightning Bolt", "Fiery Temper" -> 3; "Fireblast" -> 4; "Lava Dart", "Makeshift Munitions" -> 1
        else -> 0
    }
    private fun canMakeBlack(cards: ActorPublicCards) = cards.ownPlayer.manaPool.black > 0 ||
        cards.ownBoard.any { it.land && !it.tapped && Color.BLACK in landColors(it.name) }
    private fun floatingCovers(cards: ActorPublicCards, cost: String): Boolean {
        val pool = cards.ownPlayer.manaPool
        val pips = Regex("\\{([^}]+)}").findAll(cost).map { it.groupValues[1] }.toList()
        val need = pips.groupingBy { it }.eachCount()
        if (need.keys.any { it.toIntOrNull() == null && it !in setOf("W", "U", "B", "R", "G", "C") })
            throw UnsupportedPolicyInput("Mana-payment cost $cost requires qualification")
        val remaining = listOf(pool.white - (need["W"] ?: 0), pool.blue - (need["U"] ?: 0),
            pool.black - (need["B"] ?: 0), pool.red - (need["R"] ?: 0), pool.green - (need["G"] ?: 0),
            pool.colorless - (need["C"] ?: 0))
        return remaining.all { it >= 0 } && remaining.sum() >= pips.sumOf { it.toIntOrNull() ?: 0 }
    }
    private fun remaining(card: EntityFeatures) = ((card.toughness ?: 0) - card.damageMarked).coerceAtLeast(0)
    private val EntityFeatures.creature get() = "CREATURE" in types
    private val EntityFeatures.land get() = "LAND" in types
    private val EntityFeatures.artifact get() = "ARTIFACT" in types
}
