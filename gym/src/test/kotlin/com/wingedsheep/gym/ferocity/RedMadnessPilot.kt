package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment

/**
 * MisterTwin's exact 2026-09-24 Red Madness 60, first-cell policy v0.1.
 * Pure ActorInput -> ActorProposal. Named priorities are tactical choices, never game outcomes.
 * Card facts and complete source hashes are in policy-development/red-madness-v0.1/card-identity.json.
 */
class RedMadnessPilot {
    companion object {
        const val VERSION = "red-madness-v0.1.1"
        val MAIN_NAMES = setOf("Faithless Looting", "Fiery Temper", "Fireblast", "Grab the Prize",
            "Guttersnipe", "Highway Robbery", "Kessig Flamebreather", "Lava Dart", "Lightning Bolt",
            "Mountain", "Sazacap's Brew", "Sneaky Snacker")
        val PUBLIC_POOL = MAIN_NAMES + setOf("Baleful Strix", "Blood Fountain", "Cast Down", "Drossforge Bridge",
            "Ferocity of the Hunt", "Galvanic Blast", "Great Furnace", "Ichor Wellspring", "Krark-Clan Shaman",
            "Makeshift Munitions", "Mistvault Bridge", "Myr Enforcer", "Nihil Spellbomb", "Not Dead After All",
            "Reckoner's Bargain", "Refurbished Familiar", "Seat of the Synod", "Silverbluff Bridge", "Thoughtcast",
            "Toxin Analysis", "Vault of Whispers", "Blood", "Clue", "Treasure", "Fish", "Wicked Role", "Role",
            "Island") // Island is the declared fixed Counterspell disruption fixture, not a gauntlet-list change.
        private val BURN = mapOf("Lightning Bolt" to 3, "Fiery Temper" to 3, "Fireblast" to 4, "Lava Dart" to 1)
        private val DRAW = setOf("Faithless Looting", "Grab the Prize", "Highway Robbery", "Sazacap's Brew")
    }

    fun choose(input: ActorInput): ActorProposal {
        input.verifyBinding(input.epoch, input.actorId)
        val cards = ActorPublicCards(input)
        require(cards.hand.all { it.name in MAIN_NAMES }) { "Red policy has an unqualified hand card" }
        require((cards.allBoard + cards.graveyard + input.observation.decisionCards).all { it.name in PUBLIC_POOL }) {
            "Red policy encountered a card outside the finite first-cell pool"
        }
        val action = if (input.decision != null) decide(input, cards) else act(input, cards)
        return ActorChoiceSupport.proposal(input, action)
    }

    private fun act(input: ActorInput, cards: ActorPublicCards): GameAction {
        val legal = input.legalActions.filter { it.affordable }
        if (legal.any { it.action is BottomCards }) {
            val count = cards.resources.cardsToBottom ?: throw UnsupportedPolicyInput("London bottom count missing")
            val bottom = bottomOrder(cards).take(count).map { it.entityId }
            require(bottom.size == count)
            return BottomCards(input.actorId, bottom)
        }
        if (legal.any { it.action is KeepHand }) {
            val mulligan = shouldMulligan(cards) && legal.any { it.action is TakeMulligan }
            return if (mulligan) TakeMulligan(input.actorId) else KeepHand(input.actorId)
        }
        legal.firstOrNull { it.action is DeclareAttackers }?.let {
            return ActorCombatPlanner.attackers(input, it, aggressive = true, value = ::combatValue)
        }
        legal.firstOrNull { it.action is DeclareBlockers }?.let {
            return ActorCombatPlanner.blockers(input, it, value = ::combatValue)
        }
        legal.firstOrNull { it.action is OrderBlockers }?.let {
            val template = it.action as OrderBlockers
            val creature = input.observation.combat.creatures.single { c -> c.entityId == template.attackerId }
            return template.copy(orderedBlockers = creature.blockerIds.sortedWith(
                compareByDescending<EntityId> { id -> combatValue(cards.card(id)) }.thenBy { id -> id.toString() }))
        }

        val casts = legal.filter { it.action is CastSpell }
        casts.forEach { require(cards.card((it.action as CastSpell).cardId).name in MAIN_NAMES) }
        val options = casts.mapNotNull { prepare(input, cards, it) }
        // Directly available lethal is acted on, but no winner is inferred from this calculation.
        if (!ownPendingLethal(input, cards)) options.filter { it.faceDamage >= cards.opponentLife }
            .minWithOrNull(compareBy<Prepared> { it.landsLost }.thenBy { it.manaSpent }.thenBy { it.name })
            ?.let { return it.action }

        // Killing a Shaman does not cancel an already stacked activation. Removal matters before
        // that activation, or while the opposing Ferocity/Toxin is still targeting the creature.
        val threatened = cards.opponentBoard.filter { enemy -> enemy.isType("CREATURE") &&
            (shamanMatters(input, cards, enemy) || attackingLethalPressure(input, cards, enemy)) }
        for (enemy in threatened.sortedWith(compareByDescending<EntityFeatures> {
            if (it.name == "Krark-Clan Shaman") 100 else it.power ?: 0
        }.thenBy { it.entityId.toString() })) {
            chooseRemoval(input, cards, options, enemy, urgent = true)?.let { return it }
        }
        options.firstOrNull { it.name == "Sazacap's Brew" && it.action.giftRecipient != null }
            ?.let { return it.action }
        if (input.observation.stack.isNotEmpty()) return pass(input, legal)

        legal.firstOrNull { it.action is PlayLand }?.let { return it.action }
        val ownMain = input.observation.activePlayerId == input.actorId &&
            input.observation.phase in setOf(Phase.PRECOMBAT_MAIN, Phase.POSTCOMBAT_MAIN)
        val endWindow = input.observation.step == Step.END && input.observation.activePlayerId != input.actorId

        if (ownMain) {
            // Persistent spell damage comes first when it can actually be cast and survive the
            // visible board. Neither body receives credit for a future unobserved spell or draw.
            options.filter { it.name == "Kessig Flamebreather" || it.name == "Guttersnipe" }
                .filter { engineCreatureDeploymentWorthwhile(cards, it.name) }
                .sortedWith(compareBy<Prepared> { if (it.name == "Kessig Flamebreather") 0 else 1 }
                    .thenBy { it.manaSpent }).firstOrNull()?.let { return it.action }

            options.filter { it.name in DRAW && productiveDraw(cards, it) }
                .sortedWith(compareByDescending<Prepared> { it.snackerReturns }
                    .thenByDescending { it.madnessReserved }
                    .thenByDescending { it.faceDamage }
                    .thenBy { it.manaSpent }
                    .thenBy { it.name }).firstOrNull()?.let { return it.action }

            // Plot spends this turn's real mana and exiles the actual card. It is not a cast.
            // The declared setup is a later Guttersnipe plus a free Robbery, or saving an outlet
            // whose present discard/sacrifice would consume the next required land.
            legal.firstOrNull { it.action is PlotCard && cards.card((it.action as PlotCard).cardId).name == "Highway Robbery" }
                ?.takeIf { redAvailable(cards) >= 2 &&
                    (cards.hand.any { it.name == "Guttersnipe" } || !hasProductiveDiscard(cards, 2, 0)) }
                ?.let { return it.action }

            for (enemy in cards.opponentBoard.filter { it.isType("CREATURE") }.sortedByDescending(::combatValue)) {
                if (enemy.name == "Baleful Strix" || enemy.hasKeyword("FLYING") || (enemy.power ?: 0) >= 4) {
                    chooseRemoval(input, cards, options, enemy, urgent = false)?.let { return it }
                }
            }
        }

        if (ownMain || endWindow) {
            options.filter { it.name == "Sazacap's Brew" && productiveDraw(cards, it) }
                .minByOrNull { it.landsLost }?.let { return it.action }
            options.filter { it.name in BURN && it.targetId == cards.opponentId }
                .filter { prepared ->
                    when {
                        prepared.name == "Fireblast" && prepared.landsLost > 0 ->
                            cards.ownBoard.count { it.isType("LAND") } >= 5 && cards.ownLife <= visibleNextAttack(cards)
                        prepared.name == "Lava Dart" && prepared.landsLost > 0 ->
                            cards.ownBoard.count { it.isType("LAND") } >= 4 && castDamage(cards) >= 1
                        prepared.name == "Lava Dart" -> castDamage(cards) >= 1 || cards.opponentLife <= 4
                        else -> endWindow || castDamage(cards) >= 1 || cards.hand.none { it.name in setOf("Kessig Flamebreather", "Guttersnipe") }
                    }
                }.minWithOrNull(compareBy<Prepared> { it.landsLost }.thenBy { it.manaSpent }.thenBy { it.name })
                ?.let { return it.action }
        }
        return pass(input, legal)
    }

    private data class Prepared(val name: String, val option: ActorLegalAction, val action: CastSpell,
                                val manaSpent: Int, val landsLost: Int, val faceDamage: Int,
                                val targetId: EntityId?, val madnessReserved: Boolean, val snackerReturns: Boolean)

    private fun prepare(input: ActorInput, cards: ActorPublicCards, option: ActorLegalAction): Prepared? {
        val template = option.action as CastSpell
        val card = cards.card(template.cardId)
        val spent = manaAmount(option.manaCostString ?: card.manaCost)
        val residual = (redAvailable(cards) - spent).coerceAtLeast(0)
        // Highway Robbery's discard-or-sacrifice choice belongs to resolution. Brew uses
        // actual Gift declarations. An eager modal surrogate for either is not admitted.
        require(option.modalEnumeration == null) { "Red spell exposes an unqualified cast-time modal branch" }
        val info = option.additionalCostInfo
        val discarded = if ((info?.discardCount ?: 0) > 0) {
            val domain = info!!.validDiscardTargets.map(cards::card)
            discardOrder(cards, domain, drawsAfterDiscard = if (card.name in DRAW) 2 else 0,
                redAfterPayment = residual, preferNonland = card.name == "Grab the Prize")
                .take(info.discardCount).map { it.entityId }
        } else emptyList()
        if (info != null && discarded.size < info.discardCount) return null
        val sacrifices = if (info?.validSacrificeTargets?.isNotEmpty() == true) {
            require(card.name in setOf("Fireblast", "Lava Dart")) { "Unexpected Red sacrifice cast" }
            val lands = info.validSacrificeTargets.map(cards::card)
            require(lands.all { it.controllerId == input.actorId && it.hasSubtype("Mountain") }) {
                "Red's alternative cost domain includes a non-Mountain"
            }
            lands.sortedWith(compareByDescending<EntityFeatures> { it.tapped }.thenBy { it.entityId.toString() })
                .take(info.sacrificeCount).map { it.entityId }
        } else emptyList()
        if (info != null && info.validSacrificeTargets.isNotEmpty() && sacrifices.size != info.sacrificeCount) return null
        val costs = if (info == null) null else AdditionalCostPayment(
            discardedCards = discarded, sacrificedPermanents = sacrifices, lifePaid = option.additionalLifeCost)
        val selected = linkedMapOf<Int, List<EntityId>>()
        val requirements = option.targetRequirements ?: if (option.requiresTargets) listOf(
            ActorTargetInfo(0, option.targetDescription ?: "Target", option.minTargets, option.targetCount,
                option.validTargets ?: throw UnsupportedPolicyInput("Red spell target domain missing"))) else emptyList()
        var target: EntityId? = null
        for (r in requirements) {
            val id = when (card.name) {
                in BURN -> if (cards.opponentId in r.validTargets) cards.opponentId else
                    bestDamageTarget(input, cards, r.validTargets, BURN.getValue(card.name))
                "Sazacap's Brew" -> when {
                    cards.actorId in r.validTargets -> cards.actorId
                    template.giftRecipient != null -> r.validTargets.map(cards::card)
                        .filter { it.controllerId == cards.actorId }
                        .maxWithOrNull(compareBy<EntityFeatures> { giftDamage(input, it) }.thenBy { it.entityId.toString() })?.entityId
                    else -> throw UnsupportedPolicyInput("Nongift Brew unexpectedly requires a creature target")
                }
                else -> throw UnsupportedPolicyInput("Unexpected targeted Red spell: ${card.name}")
            }
            if (id == null && r.minTargets > 0) return null
            selected[r.index] = id?.let(::listOf).orEmpty()
            if (card.name in BURN) target = id
        }
        if (card.name == "Sazacap's Brew" && template.giftRecipient != null) {
            require(template.giftRecipient == cards.opponentId)
            val friend = selected.values.flatten().firstOrNull { it != input.actorId }?.let(cards::card) ?: return null
            // The tapped Fish is a real opposing card. Promise it only for an immediate combat
            // lethal/trade benefit, not as an unconditional free pump.
            if (giftDamage(input, friend) < 2 || (friend.power ?: 0) + 2 < cards.opponentLife) return null
        }
        val action = ActorChoiceSupport.materialize(input, option, selected, costs) as CastSpell
        val direct = if (target == cards.opponentId) BURN[card.name] ?: 0 else if (card.name == "Grab the Prize" &&
            discarded.any { !cards.card(it).isType("LAND") }) 2 else 0
        val spellTriggers = if (!card.isType("CREATURE")) castDamage(cards) else 0
        // This forecasts an available resolution choice using only the present hand. It is
        // not committed here; the actual later decision rechecks that hand after responses.
        val plannedRobberyDiscard = if (card.name == "Highway Robbery") discardOrder(cards,
            cards.hand.filter { it.entityId != card.entityId }, 2, residual, false).firstOrNull() else null
        val returnSnacker = (cards.graveyard.any { it.name == "Sneaky Snacker" } ||
            discarded.any { cards.card(it).name == "Sneaky Snacker" } || plannedRobberyDiscard?.name == "Sneaky Snacker") &&
            card.name in DRAW && thirdDrawAhead(cards, 2)
        val reserved = (discarded.any { cards.card(it).name == "Fiery Temper" } ||
            plannedRobberyDiscard?.name == "Fiery Temper") && residual >= 1
        return Prepared(card.name, option, action, spent, sacrifices.size, direct + spellTriggers,
            target, reserved, returnSnacker)
    }

    private fun chooseRemoval(input: ActorInput, cards: ActorPublicCards, options: List<Prepared>,
                              enemy: EntityFeatures, urgent: Boolean): GameAction? {
        if (enemy.hasKeyword("INDESTRUCTIBLE")) return null
        val required = ((enemy.toughness ?: 0) - enemy.damageMarked).coerceAtLeast(1)
        val burn = options.filter { it.name in BURN &&
            it.option.targetRequirements?.any { r -> enemy.entityId in r.validTargets } != false &&
            (it.option.validTargets == null || enemy.entityId in it.option.validTargets) }
        val selected = burn.filter { BURN.getValue(it.name) >= required }
            .filter { urgent || it.landsLost == 0 }
            .minWithOrNull(compareBy<Prepared> { it.landsLost }.thenBy { BURN.getValue(it.name) - required }
                .thenBy { it.manaSpent })
            ?: burn.filter { first -> urgent && first.landsLost == 0 && burn.any { next ->
                next.action.cardId != first.action.cardId && next.landsLost == 0 &&
                    first.manaSpent + next.manaSpent <= redAvailable(cards) &&
                    BURN.getValue(first.name) + BURN.getValue(next.name) >= required
            } }.minByOrNull { it.manaSpent }
            ?: return null
        val r = selected.option.targetRequirements?.singleOrNull()
        val key = r?.index ?: 0
        return ActorChoiceSupport.materialize(input, selected.option, mapOf(key to listOf(enemy.entityId)),
            selected.action.additionalCostPayment)
    }

    private fun decide(input: ActorInput, cards: ActorPublicCards): GameAction {
        val q = requireNotNull(input.decision)
        val name = q.context.sourceName.orEmpty()
        val response: DecisionResponse = when (q) {
            is YesNoDecision -> when {
                name == "Fiery Temper" && q.prompt.contains("cast", ignoreCase = true) ->
                    YesNoResponse(q.id, redAvailable(cards) >= 1)
                else -> throw UnsupportedPolicyInput("Unqualified Red consent question: $name / ${q.prompt}")
            }
            is BatchYesNoDecision -> {
                require(name == "Fiery Temper" && q.prompt.contains("cast", ignoreCase = true))
                BatchYesNoResponse(q.id, redAvailable(cards) >= 1, applyToAll = false)
            }
            is ChooseTargetsDecision -> {
                require(name in BURN || name == "Sazacap's Brew") { "Unqualified Red target question: $name" }
                require(name != "Sazacap's Brew" || q.context.phase == DecisionPhase.CASTING) {
                    "Brew's targets must be selected while casting, not by a resolution-time gift workaround"
                }
                TargetsResponse(q.id, q.targetRequirements.associate { r ->
                    val domain = q.legalTargets[r.index] ?: throw UnsupportedPolicyInput("Target question lacks domain")
                    val id = if (name in BURN) bestDamageTarget(input, cards, domain, BURN.getValue(name))
                        else if (input.actorId in domain) input.actorId else domain.map(cards::card)
                            .filter { it.controllerId == input.actorId }.maxByOrNull { giftDamage(input, it) }?.entityId
                    require(id != null || r.minTargets == 0)
                    r.index to id?.let(::listOf).orEmpty()
                })
            }
            is SelectCardsDecision -> {
                val isDiscard = q.prompt.contains("discard", true) || name in setOf("Faithless Looting", "Refurbished Familiar")
                val isLandSacrifice = name == "Highway Robbery" && q.prompt.contains("sacrifice", true)
                require(isDiscard || isLandSacrifice) { "Unqualified Red card selection: $name / ${q.prompt}" }
                val selectable = q.options.filter { it !in q.nonSelectableOptions }.map(cards::card)
                val chosen = if (isLandSacrifice) selectable.sortedWith(
                    compareByDescending<EntityFeatures> { it.tapped }.thenBy { it.entityId.toString() })
                else discardOrder(cards, selectable, if (name == "Highway Robbery") 2 else 0, redAvailable(cards), false)
                CardsSelectedResponse(q.id, chosen.take(q.minSelections).map { it.entityId })
            }
            is ChooseModeDecision -> throw UnsupportedPolicyInput("Red spell has no qualified cast-time modal choice")
            is ChooseOptionDecision -> {
                when {
                    name == "Highway Robbery" -> {
                        require(q.context.phase == DecisionPhase.RESOLUTION) { "Robbery choice occurred before resolution" }
                        val discard = "Discard a card, then draw two cards"
                        val sacrifice = "Sacrifice a land, then draw two cards"
                        val decline = "Decline"
                        require(q.options.all { it in setOf(discard, sacrifice, decline) })
                        val desired = when {
                            cards.hand.isNotEmpty() && discard in q.options -> discard
                            cards.ownBoard.count { it.isType("LAND") } >= 4 && sacrifice in q.options -> sacrifice
                            else -> decline
                        }
                        val index = q.options.indexOf(desired)
                        require(index >= 0) { "Robbery's intended explicit resolution choice is unavailable" }
                        OptionChosenResponse(q.id, index)
                    }
                    name == "Triggered abilities" && q.prompt.contains("put on the stack") -> {
                        require(q.options.all { option -> listOf("Kessig Flamebreather", "Guttersnipe", "Fiery Temper", "Sneaky Snacker")
                            .any { it in option } }) { "Unqualified Red simultaneous-trigger group" }
                        // First goes deepest: the larger damage or madness opportunity resolves first.
                        val index = q.options.indices.minWithOrNull(compareBy<Int> { triggerPriority(q.options[it]) }.thenBy { it })!!
                        OptionChosenResponse(q.id, index)
                    }
                    name == "Sazacap's Brew" -> throw UnsupportedPolicyInput("Gift choices must be fixed at casting")
                    else -> throw UnsupportedPolicyInput("Unqualified Red option question: $name / ${q.prompt}")
                }
            }
            is SelectManaSourcesDecision -> {
                require(name == "Fiery Temper" || name in MAIN_NAMES || q.prompt.contains("ward", true)) {
                    "Unqualified Red optional mana payment"
                }
                require(q.waterbendPermanents.isEmpty() && q.availableSources.none { it.requiresTappingAnotherPermanent })
                val cost = manaAmount(q.requiredCost)
                val canPay = redAvailable(cards) >= cost && manaRed(q.requiredCost) <= redAvailable(cards)
                if (!canPay && !q.canDecline) throw UnsupportedPolicyInput("Mandatory payment cannot be met")
                ManaSourcesSelectedResponse(q.id, autoPay = canPay, declined = !canPay)
            }
            is OrderObjectsDecision -> ActorCombatPlanner.order(input, q, ::combatValue)
            is AssignDamageDecision -> ActorCombatPlanner.damage(q)
            is CombatResolutionDecision -> ActorCombatPlanner.combatDamage(input, q, ::combatValue)
            else -> throw UnsupportedPolicyInput("Unqualified Red decision schema: ${q::class.simpleName}")
        }
        return ActorChoiceSupport.submit(input, response)
    }

    private fun bestDamageTarget(input: ActorInput, cards: ActorPublicCards, domain: List<EntityId>, damage: Int): EntityId? {
        if (cards.opponentId in domain && cards.opponentLife <= damage + castDamage(cards)) return cards.opponentId
        val removable = domain.mapNotNull(cards::cardOrNull).filter { it.controllerId == cards.opponentId &&
            it.isType("CREATURE") && !it.hasKeyword("INDESTRUCTIBLE") && (it.toughness ?: 0) - it.damageMarked <= damage }
        removable.firstOrNull { shamanMatters(input, cards, it) }?.let { return it.entityId }
        removable.filter { attackingLethalPressure(input, cards, it) }.maxByOrNull { it.power ?: 0 }?.let { return it.entityId }
        if (cards.opponentId in domain) return cards.opponentId
        return removable.maxByOrNull(::combatValue)?.entityId
    }

    private fun shouldMulligan(cards: ActorPublicCards): Boolean {
        val lands = cards.hand.count { it.isType("LAND") }
        val taken = cards.resources.mulligansTaken ?: throw UnsupportedPolicyInput("Mulligan history missing")
        if (taken >= 3) return lands == 0
        val pressure = cards.hand.any { it.name in setOf("Kessig Flamebreather", "Guttersnipe", "Lightning Bolt", "Grab the Prize") }
        val cheapDraw = cards.hand.any { it.name == "Faithless Looting" }
        return lands == 0 || lands >= 6 && !cheapDraw || lands == 1 && !cheapDraw && taken == 0 || !pressure && !cheapDraw
    }

    private fun bottomOrder(cards: ActorPublicCards): List<EntityFeatures> {
        val lands = cards.hand.filter { it.isType("LAND") }.sortedBy { it.entityId.toString() }
        val keepLands = if (cards.hand.any { it.name == "Guttersnipe" }) 3 else 2
        val surplus = lands.drop(keepLands).toSet()
        return cards.hand.sortedWith(compareBy<EntityFeatures> {
            when {
                it in surplus -> 0
                it.name == "Sneaky Snacker" && cards.hand.none { c -> c.name in DRAW } -> 1
                it.name == "Fireblast" && cards.hand.count { c -> c.name == "Fireblast" } > 1 -> 2
                it.name == "Fiery Temper" && cards.hand.none { c -> c.name in DRAW } -> 3
                it.name in DRAW && cards.hand.count { c -> c.name in DRAW } > 2 -> 4
                it.isType("LAND") -> 20
                it.name in setOf("Kessig Flamebreather", "Guttersnipe") -> 15
                else -> 8
            }
        }.thenBy { it.entityId.toString() })
    }

    private fun discardOrder(cards: ActorPublicCards, domain: List<EntityFeatures>, drawsAfterDiscard: Int,
                             redAfterPayment: Int, preferNonland: Boolean): List<EntityFeatures> = domain.sortedWith(
        compareBy<EntityFeatures> { card -> when {
            card.name == "Fiery Temper" && redAfterPayment >= 1 -> 0
            card.name == "Sneaky Snacker" && thirdDrawAhead(cards, drawsAfterDiscard) -> 1
            card.name == "Sneaky Snacker" -> 2
            card.isType("LAND") && cards.ownBoard.count { it.isType("LAND") } + cards.hand.count { it.isType("LAND") } > 4 -> if (preferNonland) 6 else 3
            card.name == "Faithless Looting" -> 4
            card.name == "Fireblast" && cards.hand.count { it.name == "Fireblast" } > 1 -> 5
            card.name in DRAW && cards.hand.count { it.name in DRAW } > 2 -> 6
            card.name == "Fiery Temper" -> 7
            card.isType("LAND") -> 15
            card.name in setOf("Kessig Flamebreather", "Guttersnipe") && cards.ownBoard.none { it.name == card.name } -> 14
            else -> 10
        } }.thenBy { it.entityId.toString() })

    private fun hasProductiveDiscard(cards: ActorPublicCards, draws: Int, mana: Int) =
        cards.hand.any { it.name == "Fiery Temper" && mana >= 1 || it.name == "Sneaky Snacker" && thirdDrawAhead(cards, draws) } ||
            cards.ownBoard.count { it.isType("LAND") } + cards.hand.count { it.isType("LAND") } > 4

    private fun productiveDraw(cards: ActorPublicCards, prepared: Prepared): Boolean {
        if (cards.ownPlayer.librarySize < 2) return false
        if (prepared.name == "Highway Robbery" && cards.hand.size <= 1 && cards.ownBoard.count { it.isType("LAND") } < 4) return false
        if (prepared.name == "Highway Robbery" && cards.ownBoard.count { it.isType("LAND") } < 3 &&
            cards.hand.filter { it.entityId != prepared.action.cardId }.all {
                it.name in setOf("Kessig Flamebreather", "Guttersnipe")
            }) return false
        if (prepared.snackerReturns || prepared.madnessReserved || prepared.faceDamage >= 2) return true
        if (prepared.name == "Faithless Looting") return cards.hand.size >= 3 || cards.graveyard.any { it.name == "Sneaky Snacker" }
        return cards.hand.size <= 4 || hasProductiveDiscard(cards, 2, (redAvailable(cards) - prepared.manaSpent).coerceAtLeast(0))
    }

    private fun thirdDrawAhead(cards: ActorPublicCards, draws: Int): Boolean =
        cards.resources.cardsDrawnThisTurn < 3 && cards.resources.cardsDrawnThisTurn + draws >= 3

    private fun shamanMatters(input: ActorInput, cards: ActorPublicCards, creature: EntityFeatures): Boolean {
        if (creature.name != "Krark-Clan Shaman") return false
        val auraPending = input.observation.stack.any { it.view.controllerId == cards.opponentId &&
            it.view.name in setOf("Ferocity of the Hunt", "Toxin Analysis") && creature.entityId in it.view.targets }
        val alreadyQueued = input.observation.stack.filter { it.sourceId == creature.entityId &&
            it.view.name.contains("Krark-Clan Shaman") && it.view.kind.name == "ACTIVATED_ABILITY" }.any {
                val source = it.source ?: throw UnsupportedPolicyInput("Pending Shaman lacks a qualified public source identity")
                source.originalObjectIsCurrent && source.mode == ActorSourceMode.LIVE_BATTLEFIELD
            }
        if (alreadyQueued && !auraPending) return false
        // The ability costs only sacrificing an artifact. Tapped, summoning-sick and just-
        // returned Shamans can activate it; neither a tap nor red mana is required.
        val fodder = cards.opponentBoard.count { it.isType("ARTIFACT") }
        val vulnerable = cards.ownBoard.filter { it.isType("CREATURE") && !it.hasKeyword("FLYING") &&
            !it.hasKeyword("INDESTRUCTIBLE") }
        return fodder > 0 && (auraPending || vulnerable.size >= 2 || vulnerable.any {
            creature.hasKeyword("DEATHTOUCH") || (it.toughness ?: 0) - it.damageMarked <= fodder
        })
    }

    private fun attackingLethalPressure(input: ActorInput, cards: ActorPublicCards, creature: EntityFeatures): Boolean =
        input.observation.combat.creatures.any { it.entityId == creature.entityId && it.attackingDefenderId == input.actorId } &&
            input.observation.combat.creatures.filter { it.attackingDefenderId == input.actorId }
                .sumOf { (cards.card(it.entityId).power ?: 0).coerceAtLeast(0) } >= cards.ownLife

    private fun giftDamage(input: ActorInput, creature: EntityFeatures): Int =
        if (input.observation.step == Step.DECLARE_BLOCKERS &&
            input.observation.combat.playersWhoDeclaredBlockers.any { it != input.actorId } &&
            input.observation.combat.creatures.any { it.entityId == creature.entityId && it.attackingDefenderId != null &&
                !it.wasBlocked && it.blockerIds.isEmpty() }) 2 else 0

    private fun engineCreatureDeploymentWorthwhile(cards: ActorPublicCards, name: String): Boolean =
        cards.opponentBoard.none { shaman -> shaman.name == "Krark-Clan Shaman" &&
            cards.opponentBoard.count { p -> p.isType("ARTIFACT") } >=
                if (shaman.hasKeyword("DEATHTOUCH")) 1 else if (name == "Guttersnipe") 2 else 3 } ||
            // At ample life the original tactic permits a material trade to advance the clock;
            // it does not claim the newly cast body survives the known sweep.
            cards.ownLife > visibleNextAttack(cards) + 4

    private fun visibleNextAttack(cards: ActorPublicCards): Int = cards.opponentBoard.filter { it.isType("CREATURE") }
        .sumOf { (it.power ?: 0).coerceAtLeast(0) }
    private fun castDamage(cards: ActorPublicCards): Int = cards.ownBoard.sumOf { when (it.name) {
        "Kessig Flamebreather" -> 1; "Guttersnipe" -> 2; else -> 0
    } }
    private fun ownPendingLethal(input: ActorInput, cards: ActorPublicCards): Boolean =
        input.observation.stack.isNotEmpty() && input.observation.stack.all { it.view.controllerId == input.actorId } &&
            input.observation.stack.sumOf { item -> when {
                item.view.kind.name != "SPELL" && item.view.name == "Guttersnipe" -> 2
                item.view.kind.name != "SPELL" && item.view.name == "Kessig Flamebreather" -> 1
                cards.opponentId in item.view.targets -> BURN[item.view.name] ?: 0
                else -> 0
            } } >= cards.opponentLife

    private fun redAvailable(cards: ActorPublicCards): Int = cards.ownPlayer.manaPool.red +
        cards.ownBoard.count { !it.tapped && it.isType("LAND") && it.hasSubtype("Mountain") }
    private fun manaAmount(cost: String): Int = Regex("\\{([^}]+)}").findAll(cost).sumOf { match ->
        val symbol = match.groupValues[1]
        when { symbol.toIntOrNull() != null -> symbol.toInt(); symbol == "R" -> 1
            else -> throw UnsupportedPolicyInput("Unqualified Red mana symbol: $symbol") }
    }
    private fun manaRed(cost: String): Int = Regex("\\{R}").findAll(cost).count()
    private fun triggerPriority(text: String) = when {
        "Fiery Temper" in text -> 4; "Guttersnipe" in text -> 3; "Sneaky Snacker" in text -> 2; else -> 1
    }
    private fun combatValue(card: EntityFeatures): Double = when (card.name) {
        "Guttersnipe" -> 10.0; "Kessig Flamebreather" -> 8.0; "Krark-Clan Shaman" -> 7.0
        "Baleful Strix" -> 4.5; "Refurbished Familiar" -> 4.0; "Sneaky Snacker" -> 2.5
        else -> 1.0 + (card.power ?: 0).coerceAtLeast(0) + (card.toughness ?: 0).coerceAtLeast(0) * 0.25
    }
    private fun pass(input: ActorInput, legal: List<ActorLegalAction>): GameAction =
        legal.singleOrNull { it.action is PassPriority }?.action
            ?: throw UnsupportedPolicyInput("No supported action or legal priority pass exists for ${input.actorId}")
}
