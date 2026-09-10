package com.wingedsheep.ai.solitaire

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.lea.cards.LlanowarElves
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Deterministic readiness gate for the Project X v0.2 solitaire policy. */
class ProjectXSolitaireAgentTest : ScenarioTestBase() {
    private fun agent(game: TestGame) = ProjectXSolitaireAgent(cardRegistry, game.player1Id)

    private fun name(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun libraryIds(game: TestGame): List<EntityId> = game.state.getLibrary(game.player1Id)

    private fun searchDecision(game: TestGame): SearchLibraryDecision {
        val options = libraryIds(game)
        val info = options.associateWith { id ->
            val card = game.state.getEntity(id)!!.get<CardComponent>()!!
            SearchCardInfo(card.name, card.manaCost.toString(), card.typeLine.toString())
        }
        return SearchLibraryDecision(
            id = "herald-search",
            playerId = game.player1Id,
            prompt = "Search for an Elf",
            context = DecisionContext(sourceName = ProjectXStateAnalyzer.WIREWOOD_HERALD),
            options = options,
            minSelections = 0,
            maxSelections = 1,
            cards = info,
            filterDescription = "Elf card",
        )
    }

    private fun chosenName(game: TestGame, response: DecisionResponse): String? {
        val selected = response.shouldBeInstanceOf<CardsSelectedResponse>().selectedCards.single()
        return name(game, selected)
    }

    private fun resolveWith(agent: ProjectXSolitaireAgent, game: TestGame, limit: Int = 80) {
        repeat(limit) {
            if (game.state.pendingDecision == null && game.state.stack.isEmpty()) return
            val decision = game.state.pendingDecision
            val result = if (decision != null) {
                game.execute(SubmitDecision(decision.playerId, agent.respondToDecision(game.state, decision)))
            } else {
                game.execute(PassPriority(game.state.priorityPlayerId!!))
            }
            check(result.error == null) {
                "Project X resolution transition failed: action=${result.events}; error=${result.error}"
            }
        }
        error("Project X resolution did not become quiet within $limit transitions")
    }

    init {
        // TestCards also carries an intentionally minimal namesake mana-dork fixture. Project X
        // exercises the printed card, so keep this class pinned to the canonical LEA definition.
        cardRegistry.register(LlanowarElves)

        test("frozen v0.2 deck is exact and has no sideboard") {
            ProjectXDeck.V02.size shouldBe 60
            ProjectXDeck.V02.sideboard shouldBe emptyList()
            ProjectXDeck.V02.cards.groupingBy { it }.eachCount() shouldBe linkedMapOf(
                "Carrion Feeder" to 4, "Safehold Elite" to 4, "Ivy Lane Denizen" to 4,
                "Wirewood Herald" to 4, "Evolution Witness" to 4, "Nettle Sentinel" to 4,
                "Birchlore Rangers" to 4, "Falkenrath Noble" to 2, "Essence Warden" to 1,
                "Masked Vandal" to 1, "Quirion Ranger" to 2, "Winding Way" to 4,
                "Lead the Stampede" to 4, "Forest" to 9, "Swamp" to 7,
                "Khalni Garden" to 1, "Haunted Mire" to 1,
            )
        }

        test("Experiment A changes only one Noble and one Vandal into two Llanowar Elves") {
            ProjectXDeck.EXPERIMENT_A.size shouldBe 60
            ProjectXDeck.EXPERIMENT_A.sideboard shouldBe emptyList()
            val control = ProjectXDeck.V02.cards.groupingBy { it }.eachCount()
            val variant = ProjectXDeck.EXPERIMENT_A.cards.groupingBy { it }.eachCount()

            variant shouldBe control.toMutableMap().apply {
                this["Falkenrath Noble"] = 1
                remove("Masked Vandal")
                this["Llanowar Elves"] = 2
            }
            variant["Evolution Witness"] shouldBe 4
            variant["Falkenrath Noble"] shouldBe 1
        }

        test("Herald tutors a missing Safehold Elite over generic Elf value") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardInLibrary(1, "Nettle Sentinel")
                .withCardInLibrary(1, "Safehold Elite")
                .build()

            chosenName(game, agent(game).respondToDecision(game.state, searchDecision(game))) shouldBe "Safehold Elite"
        }

        test("Herald tutors a missing Ivy Lane Denizen over generic Elf value") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder")
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardInLibrary(1, "Nettle Sentinel")
                .withCardInLibrary(1, "Ivy Lane Denizen")
                .build()

            chosenName(game, agent(game).respondToDecision(game.state, searchDecision(game))) shouldBe "Ivy Lane Denizen"
        }

        test("Herald toolbox finds Essence Warden when the primary engine is complete") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder", summoningSickness = true)
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardInLibrary(1, "Nettle Sentinel")
                .withCardInLibrary(1, "Essence Warden")
                .build()

            chosenName(game, agent(game).respondToDecision(game.state, searchDecision(game))) shouldBe "Essence Warden"
        }

        test("Herald toolbox finds Evolution Witness when a primary permanent needs recursion") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardInGraveyard(1, "Safehold Elite")
                .withCardInLibrary(1, "Nettle Sentinel")
                .withCardInLibrary(1, "Evolution Witness")
                .build()

            chosenName(game, agent(game).respondToDecision(game.state, searchDecision(game))) shouldBe "Evolution Witness"
        }

        test("Feeder sacrifices Herald when the tutor closes the only missing primary role") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardOnBattlefield(1, "Wirewood Herald")
                .withCardInLibrary(1, "Safehold Elite")
                .build()

            val action = agent(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Carrion Feeder"
            name(game, action.costPayment!!.sacrificedPermanents.single()) shouldBe "Wirewood Herald"
        }

        test("real Herald death pipeline tutors the missing role through SelectCardsDecision") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardOnBattlefield(1, "Wirewood Herald")
                .withCardInLibrary(1, "Nettle Sentinel")
                .withCardInLibrary(1, "Safehold Elite")
                .build()
            val solitaire = agent(game)

            val sacrifice = solitaire.chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, sacrifice.costPayment!!.sacrificedPermanents.single()) shouldBe "Wirewood Herald"
            game.execute(sacrifice).error shouldBe null

            var realSearchDecision: SelectCardsDecision? = null
            for (transition in 0 until 80) {
                val decision = game.state.pendingDecision
                if (decision is SelectCardsDecision &&
                    decision.context.sourceName == ProjectXStateAnalyzer.WIREWOOD_HERALD
                ) {
                    realSearchDecision = decision
                    break
                }
                val result = if (decision != null) {
                    game.execute(SubmitDecision(decision.playerId, solitaire.respondToDecision(game.state, decision)))
                } else {
                    game.execute(PassPriority(game.state.priorityPlayerId!!))
                }
                result.error shouldBe null
            }

            val search = realSearchDecision.shouldBeInstanceOf<SelectCardsDecision>()
            solitaire.analyzer.missingPrimaryRoles(game.state, game.player1Id) shouldBe setOf("Safehold Elite")
            val offeredNames = search.options.map { name(game, it) }.toSet()
            check("Nettle Sentinel" in offeredNames && "Safehold Elite" in offeredNames) {
                val library = libraryIds(game).associate { id ->
                    name(game, id) to game.state.getEntity(id)?.get<CardComponent>()?.typeLine.toString()
                }
                "Herald offered $offeredNames while library contained $library"
            }
            val response = solitaire.respondToDecision(game.state, search)
            chosenName(game, response) shouldBe "Safehold Elite"
            game.execute(SubmitDecision(search.playerId, response)).error shouldBe null
            resolveWith(solitaire, game)
            game.isInHand(1, "Safehold Elite") shouldBe true
            game.isInHand(1, "Nettle Sentinel") shouldBe false
        }

        test("Birchlore taps the available Elf pair and makes black for Carrion Feeder") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Birchlore Rangers")
                .withCardOnBattlefield(1, "Nettle Sentinel")
                .withCardInHand(1, "Carrion Feeder")
                .build()

            val action = agent(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Birchlore Rangers"
            action.manaColorChoice shouldBe Color.BLACK
            action.costPayment!!.tappedPermanents.map { name(game, it) }.toSet() shouldBe
                setOf("Birchlore Rangers", "Nettle Sentinel")
        }

        test("Nettle untap after a green spell is usable by the next Birchlore activation") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Birchlore Rangers")
                .withCardOnBattlefield(1, "Nettle Sentinel", tapped = true)
                .withCardInHand(1, "Winding Way")
                .withCardInHand(1, "Carrion Feeder")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Safehold Elite")
                .build()
            val solitaire = agent(game)

            val cast = solitaire.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, cast.cardId) shouldBe "Winding Way"
            game.execute(cast).error shouldBe null
            resolveWith(solitaire, game)
            val nettle = game.findPermanent("Nettle Sentinel")!!
            game.state.getEntity(nettle)!!.has<TappedComponent>().shouldBeFalse()

            val mana = solitaire.chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, mana.sourceId) shouldBe "Birchlore Rangers"
            mana.manaColorChoice shouldBe Color.BLACK
            mana.costPayment!!.tappedPermanents.toSet() shouldBe setOf(
                game.findPermanent("Birchlore Rangers")!!, nettle,
            )
        }

        test("Quirion returns a Forest to untap Nettle when that unlocks Birchlore black mana") {
            val game = scenario().withPlayers()
                // Quirion's ability has no tap-symbol cost, so a tapped Ranger may still
                // return the Forest. With only Birchlore untapped, Nettle is the mana unlock.
                .withCardOnBattlefield(1, "Quirion Ranger", tapped = true)
                .withCardOnBattlefield(1, "Birchlore Rangers")
                .withCardOnBattlefield(1, "Nettle Sentinel", tapped = true)
                .withCardInHand(1, "Carrion Feeder")
                .withLandsOnBattlefield(1, "Forest", 1)
                .build()

            val action = agent(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Quirion Ranger"
            val target = action.targets.single().shouldBeInstanceOf<com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent>()
            name(game, target.entityId) shouldBe "Nettle Sentinel"
            name(game, action.costPayment!!.bouncedPermanents.single()) shouldBe "Forest"
        }

        test("Llanowar makes Ivy Lane Denizen a legal cast from three lands") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardInHand(1, "Ivy Lane Denizen")
                .withLandsOnBattlefield(1, "Forest", 3)
                .build()
            val solitaire = agent(game)

            val mana = solitaire.chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, mana.sourceId) shouldBe "Llanowar Elves"
            game.execute(mana).error shouldBe null

            val cast = solitaire.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, cast.cardId) shouldBe "Ivy Lane Denizen"
        }

        test("Llanowar activation funds Ivy Lane Denizen after three lands") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardInHand(1, "Ivy Lane Denizen")
                .withLandsOnBattlefield(1, "Forest", 3)
                .build()
            val solitaire = agent(game)
            val elves = game.findPermanent("Llanowar Elves")!!

            val mana = solitaire.chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, mana.sourceId) shouldBe "Llanowar Elves"
            game.execute(mana).error shouldBe null

            val cast = solitaire.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, cast.cardId) shouldBe "Ivy Lane Denizen"
            game.execute(cast).error shouldBe null
            game.state.getEntity(elves)!!.has<TappedComponent>().shouldBeTrue()
        }

        test("diagnostic acceleration choice is an activation") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardInHand(1, "Ivy Lane Denizen")
                .withLandsOnBattlefield(1, "Forest", 3)
                .build()
            (agent(game).chooseAction(game.state) is ActivateAbility).shouldBeTrue()
        }

        test("diagnostic acceleration choice is a cast") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardInHand(1, "Ivy Lane Denizen")
                .withLandsOnBattlefield(1, "Forest", 3)
                .build()
            (agent(game).chooseAction(game.state) is CastSpell).shouldBeTrue()
        }

        test("diagnostic acceleration activation source is Llanowar") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardInHand(1, "Ivy Lane Denizen")
                .withLandsOnBattlefield(1, "Forest", 3)
                .build()
            val action = agent(game).chooseAction(game.state) as? ActivateAbility
            action?.let { name(game, it.sourceId) } shouldBe "Llanowar Elves"
        }

        test("diagnostic acceleration activation source is Forest") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardInHand(1, "Ivy Lane Denizen")
                .withLandsOnBattlefield(1, "Forest", 3)
                .build()
            val action = agent(game).chooseAction(game.state) as? ActivateAbility
            action?.let { name(game, it.sourceId) } shouldBe "Forest"
        }

        test("diagnostic choice after Llanowar activation is a cast") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardInHand(1, "Ivy Lane Denizen")
                .withLandsOnBattlefield(1, "Forest", 3)
                .build()
            val elves = game.findPermanent("Llanowar Elves")!!
            game.execute(ActivateAbility(game.player1Id, elves, LlanowarElves.activatedAbilities.single().id)).error shouldBe null
            (agent(game).chooseAction(game.state) is CastSpell).shouldBeTrue()
        }

        test("diagnostic choice after Llanowar activation is a Forest activation") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardInHand(1, "Ivy Lane Denizen")
                .withLandsOnBattlefield(1, "Forest", 3)
                .build()
            val elves = game.findPermanent("Llanowar Elves")!!
            game.execute(ActivateAbility(game.player1Id, elves, LlanowarElves.activatedAbilities.single().id)).error shouldBe null
            val action = agent(game).chooseAction(game.state) as? ActivateAbility
            action?.let { name(game, it.sourceId) } shouldBe "Forest"
        }

        test("Llanowar is deployed before a generic one-drop when it accelerates Denizen") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Llanowar Elves")
                .withCardInHand(1, "Nettle Sentinel")
                .withCardInHand(1, "Ivy Lane Denizen")
                .withLandsOnBattlefield(1, "Forest", 1)
                .build()

            val cast = agent(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, cast.cardId) shouldBe "Llanowar Elves"
        }

        test("a generic one-drop remains preferred when acceleration does not improve a future cast") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Llanowar Elves")
                .withCardInHand(1, "Nettle Sentinel")
                .withLandsOnBattlefield(1, "Forest", 1)
                .build()

            val cast = agent(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, cast.cardId) shouldBe "Nettle Sentinel"
        }

        test("Llanowar is green according to the card properties inspected by the policy") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Llanowar Elves")
                .build()

            val llanowar = game.state.getHand(game.player1Id).single()
            game.state.getEntity(llanowar)!!.get<CardComponent>()!!.colors shouldBe setOf(Color.GREEN)
        }

        test("casting property-green Llanowar creates and resolves Nettle's untap trigger") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Nettle Sentinel", tapped = true)
                .withCardInHand(1, "Llanowar Elves")
                .withLandsOnBattlefield(1, "Forest", 1)
                .build()
            val solitaire = agent(game)
            val nettle = game.findPermanent("Nettle Sentinel")!!

            val castResult = game.castSpell(1, "Llanowar Elves")
            check(castResult.error == null) { "Canonical Llanowar cast failed: ${castResult.error}" }
            resolveWith(solitaire, game)

            check(!game.state.getEntity(nettle)!!.has<TappedComponent>()) {
                "Nettle Sentinel remained tapped after resolving a green Llanowar spell"
            }
        }

        test("Birchlore tap selection accounts for Nettle untapping after an actual green spell") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Birchlore Rangers")
                .withCardOnBattlefield(1, "Nettle Sentinel")
                .withCardOnBattlefield(1, "Wirewood Herald")
                .withCardInHand(1, "Carrion Feeder")
                .withCardInHand(1, "Llanowar Elves")
                .build()

            val action = agent(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Birchlore Rangers"
            action.costPayment!!.tappedPermanents.map { name(game, it) }.toSet() shouldBe
                setOf("Birchlore Rangers", "Nettle Sentinel")
        }

        test("Winding Way selects land when mana constrained and creature when developing roles") {
            fun decision(game: TestGame) = ChooseModeDecision(
                id = "winding-mode", playerId = game.player1Id, prompt = "Choose creature or land",
                context = DecisionContext(sourceName = "Winding Way"),
                modes = listOf(ModeOption(0, "Creature"), ModeOption(1, "Land")),
            )
            val constrained = scenario().withPlayers().withLandsOnBattlefield(1, "Forest", 1).build()
            val developed = scenario().withPlayers().withLandsOnBattlefield(1, "Forest", 3).build()

            agent(constrained).respondToDecision(constrained.state, decision(constrained))
                .shouldBeInstanceOf<ModesChosenResponse>().selectedModes shouldBe listOf(1)
            agent(developed).respondToDecision(developed.state, decision(developed))
                .shouldBeInstanceOf<ModesChosenResponse>().selectedModes shouldBe listOf(0)
        }

        test("Winding Way uses the card-type option primitive emitted by the real rules effect") {
            fun decision(game: TestGame) = ChooseOptionDecision(
                id = "winding-type", playerId = game.player1Id, prompt = "Choose creature or land",
                context = DecisionContext(sourceName = "Winding Way"),
                options = listOf("Creature", "Land"),
            )
            val constrained = scenario().withPlayers().withLandsOnBattlefield(1, "Forest", 1).build()
            val developed = scenario().withPlayers().withLandsOnBattlefield(1, "Forest", 3).build()

            agent(constrained).respondToDecision(constrained.state, decision(constrained))
                .shouldBeInstanceOf<OptionChosenResponse>().optionIndex shouldBe 1
            agent(developed).respondToDecision(developed.state, decision(developed))
                .shouldBeInstanceOf<OptionChosenResponse>().optionIndex shouldBe 0
        }

        test("Lead is cast before generic board value when primary creature roles are missing") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardInHand(1, "Lead the Stampede")
                .withCardInHand(1, "Nettle Sentinel")
                .build()

            val action = agent(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Lead the Stampede"
        }

        test("Lead keeps every offered creature and no land") {
            val game = scenario().withPlayers()
                .withCardInLibrary(1, "Safehold Elite")
                .withCardInLibrary(1, "Ivy Lane Denizen")
                .withCardInLibrary(1, "Forest")
                .build()
            val options = libraryIds(game)
            val decision = SelectCardsDecision(
                id = "lead-select", playerId = game.player1Id, prompt = "Put creatures into your hand",
                context = DecisionContext(sourceName = "Lead the Stampede"), options = options,
                minSelections = 0, maxSelections = 5,
            )

            val selected = agent(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<CardsSelectedResponse>().selectedCards
            selected.map { name(game, it) }.toSet() shouldBe setOf("Safehold Elite", "Ivy Lane Denizen")
        }

        test("Evolution Witness adapts and returns the missing permanent role") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardOnBattlefield(1, "Evolution Witness")
                .withCardInGraveyard(1, "Safehold Elite")
                .withLandsOnBattlefield(1, "Forest", 2)
                .build()
            val solitaire = agent(game)

            val adapt = solitaire.chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, adapt.sourceId) shouldBe "Evolution Witness"
            game.execute(adapt).error shouldBe null
            resolveWith(solitaire, game)
            game.state.getHand(game.player1Id).map { name(game, it) } shouldBe listOf("Safehold Elite")
        }

        test("primary loop is recognized symbolically as an infinite engine and unbounded Feeder") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder", summoningSickness = true)
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .build()

            val outcome = agent(game).outcome(game.state)
            outcome.completeInfiniteEngine.shouldBeTrue()
            outcome.arbitrarilyLargeCarrionFeeder.shouldBeTrue()
            outcome.arbitraryLife.shouldBeFalse()
            outcome.nobleDeterministicLethal.shouldBeFalse()
        }

        test("outcomes distinguish arbitrary life from immediate Noble lethal") {
            val life = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder", summoningSickness = true)
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardOnBattlefield(1, "Essence Warden")
                .build()
            val lethal = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder", summoningSickness = true)
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardOnBattlefield(1, "Falkenrath Noble")
                .build()

            agent(life).outcome(life.state).let {
                it.arbitraryLife.shouldBeTrue()
                it.immediateDeterministicLethal.shouldBeFalse()
            }
            agent(lethal).outcome(lethal.state).let {
                it.nobleDeterministicLethal.shouldBeTrue()
                it.immediateDeterministicLethal.shouldBeTrue()
            }
        }

        test("summoning-sick unbounded Feeder is not classified as immediate combat lethal") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder", summoningSickness = true)
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .build()

            val outcome = agent(game).outcome(game.state)
            outcome.arbitrarilyLargeCarrionFeeder.shouldBeTrue()
            outcome.feederCombatLethalThisTurn.shouldBeFalse()
            outcome.immediateDeterministicLethal.shouldBeFalse()
        }

        test("an unbounded Feeder that can attack is deterministic combat lethal without materializing counters") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder")
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .build()

            val outcome = agent(game).outcome(game.state)
            outcome.arbitrarilyLargeCarrionFeeder.shouldBeTrue()
            outcome.feederCombatLethalThisTurn.shouldBeTrue()
            outcome.immediateDeterministicLethal.shouldBeTrue()
        }

        test("Noble lethal is terminal immediately without executing loop iterations") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder", summoningSickness = true)
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardOnBattlefield(1, "Falkenrath Noble")
                .build()

            agent(game).outcome(game.state).nobleDeterministicLethal.shouldBeTrue()
            game.state.stack shouldBe emptyList()
        }

        test("secondary Witness loop is recognized with its rules-correct priority sequence") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardOnBattlefield(1, "Evolution Witness")
                .withCardOnBattlefield(1, "Birchlore Rangers")
                .withCardOnBattlefield(1, "Nettle Sentinel")
                .withCardInHand(1, "Quirion Ranger")
                .build()

            val solitaire = agent(game)
            solitaire.outcome(game.state).secondaryWitnessLoop.shouldBeTrue()
            solitaire.analyzer.secondaryWitnessSequence(game.state, game.player1Id) shouldBe
                SecondaryWitnessStep.entries
        }

        test("secondary Witness loop propagates to huge Feeder life and Noble lethal outcomes") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder", summoningSickness = true)
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardOnBattlefield(1, "Evolution Witness")
                .withCardOnBattlefield(1, "Birchlore Rangers")
                .withCardOnBattlefield(1, "Nettle Sentinel")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Falkenrath Noble")
                .withCardInHand(1, "Quirion Ranger")
                .build()

            agent(game).outcome(game.state).let {
                it.completeInfiniteEngine.shouldBeFalse()
                it.secondaryWitnessLoop.shouldBeTrue()
                it.arbitrarilyLargeCarrionFeeder.shouldBeTrue()
                it.arbitraryLife.shouldBeTrue()
                it.nobleDeterministicLethal.shouldBeTrue()
            }
        }

        test("recognized primary loop advances the game instead of physically iterating sacrifices") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder", summoningSickness = true)
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .build()

            agent(game).chooseAction(game.state) shouldBe PassPriority(game.player1Id)
        }

        test("recognized loop preserves its creatures while deploying Noble for immediate lethal") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder", summoningSickness = true)
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardOnBattlefield(1, "Wirewood Herald")
                .withCardInHand(1, "Falkenrath Noble")
                .withLandsOnBattlefield(1, "Swamp", 4)
                .build()

            val action = agent(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Falkenrath Noble"
        }
    }
}
