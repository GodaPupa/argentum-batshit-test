package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CantBeCounteredComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardLayout
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Ten fixed shared projection checks. These do not run a pilot or experimental game. */
class ActorSpellPaymentProjectionTest : ScenarioTestBase() {
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val resolver = StackResolver(cardRegistry)
    private val epoch = ActorEpoch("shared-spell-payment-projection-v1", "fixed-data", 0)
    private val split = card("Projection Hall // Projection Vault") {
        layout = CardLayout.SPLIT
        face("Projection Hall") { manaCost = "{U}"; typeLine = "Enchantment — Room" }
        face("Projection Vault") { manaCost = "{2}{U}"; typeLine = "Enchantment — Room" }
    }

    private fun fixture(name: String = "Mental Note", islands: Int = 3) = scenario()
        .withPlayers().withRngSeed(202609260301L)
        .withCardInHand(1, name).withLandsOnBattlefield(1, "Island", islands)
        .withCardInLibrary(1, "Island").withCardInLibrary(1, "Mental Note")
        .withCardInLibrary(1, "Island").withCardInLibrary(1, "Mental Note")
        .withCardInLibrary(2, "Island")
        .withActivePlayer(1).withPriorityPlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun input(state: GameState): ActorInput {
        val actor = state.priorityPlayerId!!
        return adapter.build(state, actor, completeActorLegalActions(state, actor, enumerator), epoch, 910301L)
    }

    private fun payment(state: GameState, id: EntityId): ActorBasicBluePayment = input(state).legalActions
        .single { (it.action as? CastSpell)?.let { cast -> cast.cardId == id && !cast.castFaceDown && cast.faceIndex == null } == true }
        .basicBluePayment!!

    private fun assertActualPayment(game: TestGame, id: EntityId, projected: ActorBasicBluePayment) {
        val before = game.state
        game.execute(CastSpell(game.player1Id, id)).error shouldBe null
        val tapped = before.getBattlefield(game.player1Id).filter {
            before.getEntity(it)?.has<TappedComponent>() == false &&
                game.state.getEntity(it)?.has<TappedComponent>() == true
        }.sortedBy { it.value }
        tapped shouldBe projected.tappedSourceIds
        val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
        pool.blue shouldBe projected.bluePoolAfterPayment
        val untappedIslands = game.state.getBattlefield(game.player1Id).count {
            game.state.getEntity(it)?.get<CardComponent>()?.name in setOf("Island", "Snow-Covered Island") &&
                game.state.getEntity(it)?.has<TappedComponent>() == false
        }
        pool.blue + untappedIslands shouldBe projected.blueRemainingAfterPayment
        game.state.stack shouldBe listOf(id)
    }

    init {
        cardRegistry.register(split)

        test("SP01 actual ordinary cast exposes current ownership mana value and counterability") {
            val game = fixture().build()
            val id = game.findCardsInHand(1, "Mental Note").single()
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            val public = input(game.state).observation.stack.single().spell!!
            public.ownerId shouldBe game.player1Id
            public.manaValue shouldBe 1
            public.faceDown shouldBe false
            public.counterable shouldBe true
            public.counterabilityUnavailableReason shouldBe null
            val countered = resolver.counterSpell(game.state, id)
            countered.error shouldBe null
            countered.state.stack shouldBe emptyList()
            countered.state.getGraveyard(game.player1Id) shouldBe listOf(id)
        }

        test("SP02 explicit uncounterable marker agrees with canonical counter execution") {
            val game = fixture().build()
            val id = game.findCardsInHand(1, "Mental Note").single()
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            val marked = game.state.updateEntity(id) { it.with(CantBeCounteredComponent) }
            input(marked).observation.stack.single().spell!!.counterable shouldBe false
            val unchanged = resolver.counterSpell(marked, id)
            unchanged.error shouldBe null
            unchanged.state shouldBe marked
        }

        test("SP03 concealed counter-grant source identity does not change the actor payload") {
            val game = fixture().withCardOnBattlefield(1, "Sphinx of the Final Word").build()
            val id = game.findCardsInHand(1, "Mental Note").single()
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            game.execute(PassPriority(game.player1Id)).error shouldBe null
            val source = game.findPermanent("Sphinx of the Final Word")!!
            val hidden = game.state.updateEntity(source) { it.with(FaceDownComponent) }
            val changed = hidden.updateEntity(source) { entity ->
                entity.with(entity.get<CardComponent>()!!.copy(cardDefinitionId = "Grizzly Bears", name = "Grizzly Bears"))
            }
            // This pins the inherited predicate's unqualified hidden-definition dependence.
            // The projection must return identical explicit unknowns without querying it.
            resolver.isSpellCounterable(hidden, id) shouldBe false
            resolver.isSpellCounterable(changed, id) shouldBe true
            val before = input(hidden)
            before.canonicalJson() shouldBe input(changed).canonicalJson()
            before.observation.stack.single().spell!!.counterable shouldBe null
            before.observation.stack.single().spell!!.counterabilityUnavailableReason shouldBe
                "Counterability with a face-down battlefield source needs canonical qualification"
        }

        test("SP04 actual selected split face publishes its face mana value not combined cost") {
            val game = fixture(split.name).build()
            val id = game.findCardsInHand(1, split.name).single()
            game.state.getEntity(id)!!.get<CardComponent>()!!.manaValue shouldBe 4
            game.execute(CastSpell(game.player1Id, id, faceIndex = 0)).error shouldBe null
            val public = input(game.state).observation.stack.single().spell!!
            public.manaValue shouldBe 1
            public.faceDown shouldBe false
            public.counterable shouldBe true
        }

        test("SP05 an actual face-down cast publishes zero mana value and explicit unknown counterability") {
            val game = fixture("Willbender").build()
            val id = game.findCardsInHand(1, "Willbender").single()
            game.execute(CastSpell(game.player1Id, id, castFaceDown = true)).error shouldBe null
            val public = input(game.state).observation.stack.single().spell!!
            public.ownerId shouldBe game.player1Id
            public.manaValue shouldBe 0
            public.faceDown shouldBe true
            public.counterable shouldBe null
            public.counterabilityUnavailableReason shouldBe "Counterability of a face-down spell needs canonical qualification"
        }

        test("SP06 one blue cast projects the same selected taps and reserve as the real payment") {
            val game = fixture().build()
            val before = game.state
            val id = game.findCardsInHand(1, "Mental Note").single()
            val projected = payment(before, id)
            game.state shouldBe before
            projected.status shouldBe ActorBasicBluePaymentStatus.PLANNED
            projected.availableBlueMana shouldBe 3
            projected.totalMana shouldBe 1
            projected.blueRemainingAfterPayment shouldBe 2
            projected.tappedSourceIds.size shouldBe 1
            assertActualPayment(game, id, projected)
        }

        test("SP07 existing floating blue is spent before land taps and actual remaining pool agrees") {
            val game = fixture().build()
            game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(blue = 2)) }
            val id = game.findCardsInHand(1, "Mental Note").single()
            val projected = payment(game.state, id)
            projected.status shouldBe ActorBasicBluePaymentStatus.PLANNED
            projected.availableBlueMana shouldBe 5
            projected.blueRemainingAfterPayment shouldBe 4
            projected.bluePoolAfterPayment shouldBe 1
            projected.tappedSourceIds shouldBe emptyList()
            assertActualPayment(game, id, projected)
        }

        test("SP08 generic plus blue payment consumes its full actual basic-blue cost") {
            val game = fixture("Sphinx's Approach").build()
            val id = game.findCardsInHand(1, "Sphinx's Approach").single()
            val projected = payment(game.state, id)
            projected.status shouldBe ActorBasicBluePaymentStatus.PLANNED
            projected.availableBlueMana shouldBe 3
            projected.totalMana shouldBe 3
            projected.blueRemainingAfterPayment shouldBe 0
            projected.tappedSourceIds.size shouldBe 3
            assertActualPayment(game, id, projected)
        }

        test("SP09 the full unaffordable offer is retained with a failed canonical payment") {
            val game = fixture(islands = 0).build()
            val id = game.findCardsInHand(1, "Mental Note").single()
            val projected = payment(game.state, id)
            projected.status shouldBe ActorBasicBluePaymentStatus.UNPAYABLE
            projected.availableBlueMana shouldBe 0
            projected.totalMana shouldBe 1
            projected.blueRemainingAfterPayment shouldBe null
            val original = game.state
            game.execute(CastSpell(game.player1Id, id)).error.shouldBeInstanceOf<String>()
            game.state shouldBe original
        }

        test("SP10 an out-of-representation mana source is explicit unsupported rather than false reserve") {
            val game = fixture().withCardOnBattlefield(1, "Forest").build()
            val id = game.findCardsInHand(1, "Mental Note").single()
            val projected = payment(game.state, id)
            projected.status shouldBe ActorBasicBluePaymentStatus.UNSUPPORTED
            projected.availableBlueMana shouldBe null
            projected.totalMana shouldBe null
            projected.blueRemainingAfterPayment shouldBe null
            projected.tappedSourceIds shouldBe emptyList()
        }
    }
}
