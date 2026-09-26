package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Direct-test-only prerelease fixture. Never registered in MtgSetCatalog or a sanctioned card pool.
 * Text: ferocity-recycling/sources/ferocity-of-the-hunt-canonical.json, retrieved 2026-09-26.
 * Source record says FRA #134, common, release 2026-10-02, Pauper not_legal at retrieval.
 * The triggered ability belongs to the Aura; it is not granted to the creature.
 */
internal val ferocityResearchFixture = card("Ferocity of the Hunt") {
    manaCost = "{1}{B/G}"
    colorIdentity = "BG"
    typeLine = "Enchantment — Aura"
    oracleText = "Flash\nEnchant creature\n" +
        "Enchanted creature gets +1/+0 and has deathtouch.\n" +
        "When enchanted creature dies, return that card to the battlefield tapped under its owner's control."
    keywords(Keyword.FLASH)
    auraTarget = Targets.Creature
    staticAbility { ability = ModifyStats(1, 0) }
    staticAbility { ability = GrantKeyword(Keyword.DEATHTOUCH) }
    triggeredAbility {
        trigger = Triggers.leavesBattlefield(to = Zone.GRAVEYARD, binding = TriggerBinding.ATTACHED)
        effect = Effects.PutOntoBattlefieldFromGraveyard(EffectTarget.TriggeringEntity, tapped = true)
    }
}

class FerocityOfTheHuntScenarioTest : ScenarioTestBase() {
    private val slay = card("Ferocity Fixture Slay") {
        manaCost = "{0}"; typeLine = "Instant"; oracleText = "Destroy target creature."
        spell { val t = target("target creature", Targets.Creature); effect = Effects.Destroy(t) }
    }
    private val exileCreature = card("Ferocity Fixture Exile") {
        manaCost = "{0}"; typeLine = "Instant"; oracleText = "Exile target creature."
        spell { val t = target("target creature", Targets.Creature); effect = Effects.Exile(t) }
    }
    private val bounceCreature = card("Ferocity Fixture Bounce") {
        manaCost = "{0}"; typeLine = "Instant"; oracleText = "Return target creature to its owner's hand."
        spell { val t = target("target creature", Targets.Creature); effect = Effects.ReturnToHand(t) }
    }
    private val exileGraveyardCard = card("Ferocity Fixture Graveyard Exile") {
        manaCost = "{0}"; typeLine = "Instant"; oracleText = "Exile target card from a graveyard."
        spell { val t = target("target card", Targets.CardInGraveyard); effect = Effects.Exile(t, fromZone = Zone.GRAVEYARD) }
    }
    private val suppress = card("Ferocity Fixture Suppress") {
        manaCost = "{0}"; typeLine = "Instant"; oracleText = "Target permanent loses all abilities until end of turn."
        spell { val t = target("target permanent", Targets.Permanent); effect = Effects.RemoveAllAbilities(t) }
    }
    private val removeAura = card("Ferocity Fixture Remove Aura") {
        manaCost = "{0}"; typeLine = "Instant"; oracleText = "Destroy target enchantment."
        spell { val t = target("target enchantment", Targets.Enchantment); effect = Effects.Destroy(t) }
    }

    private fun setup() = scenario().withPlayers("Ferocity", "Opponent")
        .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Swamp")
        .withCardInLibrary(2, "Island").withCardInLibrary(2, "Mountain")
        .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun TestGame.fixed(): TestGame = apply { state = state.copy(rng = GameRng.seeded(0xFE000001)) }

    private fun TestGame.finish() {
        resolveStack().forEach { it.error shouldBe null }
        state.pendingDecision shouldBe null
        state.stack shouldBe emptyList()
        state.gameOver shouldBe false
    }

    private fun TestGame.resolveOne() {
        passPriority().error shouldBe null
        passPriority().error shouldBe null
        state.pendingDecision shouldBe null
    }

    init {
        cardRegistry.register(listOf(ferocityResearchFixture, slay, exileCreature, bounceCreature,
            exileGraveyardCard, suppress, removeAura))

        for (land in listOf("Swamp", "Forest")) {
            test("hybrid paid with $land and flash works in opponent combat") {
                val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInHand(1, ferocityResearchFixture.name).withLandsOnBattlefield(1, land, 2)
                    .withActivePlayer(2).withPriorityPlayer(1).inPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                    .build().fixed()
                val bears = game.findPermanent("Grizzly Bears")!!
                game.castSpell(1, ferocityResearchFixture.name, bears).error shouldBe null
                game.finish()
                game.state.projectedState.getPower(bears) shouldBe 3
                game.state.projectedState.getToughness(bears) shouldBe 2
                game.state.projectedState.hasKeyword(bears, Keyword.DEATHTOUCH) shouldBe true
                val aura = game.findPermanent(ferocityResearchFixture.name)!!
                game.state.getEntity(aura)?.get<AttachedToComponent>()?.targetId shouldBe bears
                game.findPermanents(land).all { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe true
                game.state.grantedTriggeredAbilities.any { it.entityId == bears } shouldBe false
            }
        }

        test("two red sources cannot pay the hybrid pip and rejection consumes nothing") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, ferocityResearchFixture.name).withLandsOnBattlefield(1, "Mountain", 2)
                .build().fixed()
            val before = game.state
            game.castSpell(1, ferocityResearchFixture.name, game.findPermanent("Grizzly Bears")).error.shouldNotBeNull()
            game.state shouldBe before
        }

        test("an Aura cannot be cast without its required creature target") {
            val game = setup().withCardInHand(1, ferocityResearchFixture.name)
                .withLandsOnBattlefield(1, "Swamp", 2).build().fixed()
            val before = game.state
            game.castSpell(1, ferocityResearchFixture.name).error.shouldNotBeNull()
            game.state shouldBe before
        }

        test("target removal in response puts Aura and creature in graveyard without returning either") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, ferocityResearchFixture.name).withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(2, slay.name).build().fixed()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, ferocityResearchFixture.name, bears).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, slay.name, bears).error shouldBe null
            game.finish()
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(1, ferocityResearchFixture.name) shouldBe true
            game.isOnBattlefield("Grizzly Bears") shouldBe false
        }

        test("countering the Aura leaves the creature without either continuous benefit") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, ferocityResearchFixture.name).withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(2, "Counterspell").withLandsOnBattlefield(2, "Island", 2).build().fixed()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, ferocityResearchFixture.name, bears).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(2, "Counterspell", ferocityResearchFixture.name).error shouldBe null
            game.finish()
            game.isInGraveyard(1, ferocityResearchFixture.name) shouldBe true
            game.state.projectedState.getPower(bears) shouldBe 2
            game.state.projectedState.hasKeyword(bears, Keyword.DEATHTOUCH) shouldBe false
        }

        for (stolen in listOf(false, true)) {
            test("death returns a new tapped object under its owner control; previously stolen=$stolen") {
                val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardAttachedTo(1, ferocityResearchFixture.name, "Grizzly Bears")
                    .withCardInHand(1, slay.name).build().fixed()
                val bears = game.findPermanent("Grizzly Bears")!!
                if (stolen) game.state = game.state.updateEntity(bears) {
                    it.with(OwnerComponent(game.player2Id)).with(it.get<CardComponent>()!!.copy(ownerId = game.player2Id))
                }
                val origin = game.state.objectRef(bears).shouldNotBeNull()
                val aura = game.findPermanent(ferocityResearchFixture.name)!!
                game.castSpell(1, slay.name, bears).error shouldBe null
                game.resolveOne()
                val trigger = game.state.stack.mapNotNull {
                    game.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()
                }.single()
                trigger.sourceId shouldBe aura
                trigger.triggeringEntityId shouldBe bears
                game.isOnBattlefield("Grizzly Bears") shouldBe false
                game.isInGraveyard(1, ferocityResearchFixture.name) shouldBe true
                game.finish()
                val owner = if (stolen) game.player2Id else game.player1Id
                (bears in game.state.getBattlefield()) shouldBe true
                game.state.projectedState.getController(bears) shouldBe owner
                game.state.objectRef(bears) shouldNotBe origin
                game.state.getEntity(bears)!!.has<TappedComponent>() shouldBe true
                game.state.getEntity(bears)!!.has<SummoningSicknessComponent>() shouldBe true
                game.state.projectedState.hasKeyword(bears, Keyword.HASTE) shouldBe false
                game.state.projectedState.hasKeyword(bears, Keyword.DEATHTOUCH) shouldBe false
                game.state.projectedState.getPower(bears) shouldBe 2
                game.state.projectedState.getToughness(bears) shouldBe 2
                game.isInGraveyard(1, ferocityResearchFixture.name) shouldBe true
            }
        }

        test("a departed token is not returned") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears", isToken = true)
                .withCardAttachedTo(1, ferocityResearchFixture.name, "Grizzly Bears")
                .withCardInHand(1, slay.name).build().fixed()
            val token = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, slay.name, token).error shouldBe null
            game.finish()
            game.state.getEntity(token) shouldBe null
            game.isOnBattlefield("Grizzly Bears") shouldBe false
            game.isInGraveyard(1, ferocityResearchFixture.name) shouldBe true
        }

        for (removal in listOf(exileCreature, bounceCreature)) {
            test("${removal.name} is not a death and never returns the creature") {
                val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardAttachedTo(1, ferocityResearchFixture.name, "Grizzly Bears")
                    .withCardInHand(1, removal.name).build().fixed()
                val bears = game.findPermanent("Grizzly Bears")!!
                game.castSpell(1, removal.name, bears).error shouldBe null
                game.finish()
                game.isOnBattlefield("Grizzly Bears") shouldBe false
                game.isInGraveyard(1, "Grizzly Bears") shouldBe false
                val expectedZone = if (removal == exileCreature) game.state.getExile(game.player1Id)
                    else game.state.getHand(game.player1Id)
                (bears in expectedZone) shouldBe true
                game.isInGraveyard(1, ferocityResearchFixture.name) shouldBe true
            }
        }

        test("graveyard exile between death and return invalidates the return reference") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, ferocityResearchFixture.name, "Grizzly Bears")
                .withCardInHand(1, slay.name).withCardInHand(1, exileGraveyardCard.name).build().fixed()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, slay.name, bears).error shouldBe null
            game.resolveOne()
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.state.stack.size shouldBe 1
            game.castSpellTargetingGraveyardCard(1, exileGraveyardCard.name, listOf(bears)).error shouldBe null
            game.finish()
            (bears in game.state.getExile(game.player1Id)) shouldBe true
            game.isOnBattlefield("Grizzly Bears") shouldBe false
        }

        test("graveyard replacement means the enchanted creature never dies") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, ferocityResearchFixture.name, "Grizzly Bears")
                .withCardOnBattlefield(2, "Rest in Peace").withCardInHand(1, slay.name).build().fixed()
            val bears = game.findPermanent("Grizzly Bears")!!
            val aura = game.findPermanent(ferocityResearchFixture.name)!!
            game.castSpell(1, slay.name, bears).error shouldBe null
            game.finish()
            (bears in game.state.getExile(game.player1Id)) shouldBe true
            (aura in game.state.getExile(game.player1Id)) shouldBe true
            game.isOnBattlefield("Grizzly Bears") shouldBe false
        }

        test("destroying the Aura before creature death removes both bonuses and protection") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, ferocityResearchFixture.name, "Grizzly Bears")
                .withCardInHand(1, removeAura.name).withCardInHand(1, slay.name).build().fixed()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, removeAura.name, game.findPermanent(ferocityResearchFixture.name)).error shouldBe null
            game.finish()
            game.state.projectedState.hasKeyword(bears, Keyword.DEATHTOUCH) shouldBe false
            game.state.projectedState.getPower(bears) shouldBe 2
            game.castSpell(1, slay.name, bears).error shouldBe null
            game.finish()
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
        }

        for (suppressAura in listOf(false, true)) {
            test("ability loss acts on the actual trigger owner; suppress Aura=$suppressAura") {
                val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardAttachedTo(1, ferocityResearchFixture.name, "Grizzly Bears")
                    .withCardInHand(1, suppress.name).withCardInHand(1, slay.name).build().fixed()
                val bears = game.findPermanent("Grizzly Bears")!!
                val target = if (suppressAura) game.findPermanent(ferocityResearchFixture.name)!! else bears
                game.castSpell(1, suppress.name, target).error shouldBe null
                game.finish()
                game.castSpell(1, slay.name, bears).error shouldBe null
                game.finish()
                game.isOnBattlefield("Grizzly Bears") shouldBe !suppressAura
                game.isInGraveyard(1, ferocityResearchFixture.name) shouldBe true
            }
        }

        test("sacrifice as a spell cost is atomic and the Aura trigger resolves before the draw spell") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, ferocityResearchFixture.name, "Grizzly Bears")
                .withCardInHand(1, "Village Rites").withLandsOnBattlefield(1, "Swamp", 1).build().fixed()
            game.castSpellWithAdditionalSacrifice(1, "Village Rites", "Grizzly Bears").error shouldBe null
            withClue("cost has been paid before either player can respond") {
                game.isOnBattlefield("Grizzly Bears") shouldBe false
                game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            }
            game.state.stack.size shouldBe 2
            val top = game.state.getEntity(game.state.stack.last())?.get<TriggeredAbilityOnStackComponent>()
            top.shouldNotBeNull().sourceName shouldBe ferocityResearchFixture.name
            game.resolveOne()
            game.isOnBattlefield("Grizzly Bears") shouldBe true
            game.state.getHand(game.player1Id).size shouldBe 0
            game.finish()
            game.state.getHand(game.player1Id).size shouldBe 2
        }

        test("returned Visionary entry trigger resolves above the pending sacrifice draw spell") {
            val game = setup().withCardOnBattlefield(1, "Elvish Visionary")
                .withCardAttachedTo(1, ferocityResearchFixture.name, "Elvish Visionary")
                .withCardInHand(1, "Village Rites").withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInLibrary(1, "Mountain").build().fixed()
            game.castSpellWithAdditionalSacrifice(1, "Village Rites", "Elvish Visionary").error shouldBe null
            game.state.stack.size shouldBe 2
            game.resolveOne()
            game.isOnBattlefield("Elvish Visionary") shouldBe true
            game.state.getHand(game.player1Id).size shouldBe 0
            val entry = game.state.getEntity(game.state.stack.last())?.get<TriggeredAbilityOnStackComponent>()
            entry.shouldNotBeNull().sourceName shouldBe "Elvish Visionary"
            game.resolveOne()
            game.state.getHand(game.player1Id).size shouldBe 1
            game.state.stack.size shouldBe 1
            game.finish()
            game.state.getHand(game.player1Id).size shouldBe 3
        }

        test("Shaman kills friendly and opposing nonfliers, spares flying and returns without the Aura") {
            val game = setup().withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardAttachedTo(1, ferocityResearchFixture.name, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(1, "Bonesplitter")
                .withCardOnBattlefield(2, "Craw Wurm").withCardOnBattlefield(2, "Ornithopter").build().fixed()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            val fodder = game.findPermanent("Bonesplitter")!!
            val ability = cardRegistry.requireCard("Krark-Clan Shaman").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, shaman, ability,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder)))).error shouldBe null
            game.isInGraveyard(1, "Bonesplitter") shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe true
            game.finish()
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.isOnBattlefield("Ornithopter") shouldBe true
            game.isOnBattlefield("Krark-Clan Shaman") shouldBe true
            game.state.getEntity(shaman)!!.has<TappedComponent>() shouldBe true
            game.state.projectedState.hasKeyword(shaman, Keyword.DEATHTOUCH) shouldBe false
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
        }

        test("an older queued Shaman activation kills the returned one-toughness creature") {
            val game = setup().withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardAttachedTo(1, ferocityResearchFixture.name, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Bonesplitter").withCardOnBattlefield(1, "Bonesplitter")
                .withCardOnBattlefield(2, "Craw Wurm").build().fixed()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            val ability = cardRegistry.requireCard("Krark-Clan Shaman").activatedAbilities.single().id
            val fodder = game.findPermanents("Bonesplitter")
            for (artifact in fodder) {
                game.execute(ActivateAbility(game.player1Id, shaman, ability,
                    costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(artifact)))).error shouldBe null
            }
            game.state.stack.size shouldBe 2
            game.finish()
            game.isInGraveyard(1, "Krark-Clan Shaman") shouldBe true
            game.isInGraveyard(1, ferocityResearchFixture.name) shouldBe true
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
        }

        test("a still-live Shaman gains deathtouch when Ferocity resolves after its activation") {
            val game = setup().withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardInHand(1, ferocityResearchFixture.name).withLandsOnBattlefield(1, "Swamp", 2)
                .withCardOnBattlefield(1, "Bonesplitter").withCardOnBattlefield(2, "Craw Wurm").build().fixed()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            val ability = cardRegistry.requireCard("Krark-Clan Shaman").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, shaman, ability,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(game.findPermanent("Bonesplitter")!!)))).error shouldBe null
            game.castSpell(1, ferocityResearchFixture.name, shaman).error shouldBe null
            game.finish()
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.isOnBattlefield("Krark-Clan Shaman") shouldBe true
        }

        test("a still-live Shaman loses deathtouch when the Aura is destroyed before its damage") {
            val game = setup().withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardAttachedTo(1, ferocityResearchFixture.name, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Bonesplitter").withCardInHand(2, removeAura.name)
                .withCardOnBattlefield(2, "Craw Wurm").build().fixed()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            val ability = cardRegistry.requireCard("Krark-Clan Shaman").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, shaman, ability,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(game.findPermanent("Bonesplitter")!!)))).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, removeAura.name, game.findPermanent(ferocityResearchFixture.name)).error shouldBe null
            game.finish()
            game.isInGraveyard(1, "Krark-Clan Shaman") shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe true
        }

        test("pending Shaman damage uses old-source deathtouch after removal and Aura return") {
            val game = setup().withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardAttachedTo(1, ferocityResearchFixture.name, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Bonesplitter").withCardInHand(2, slay.name)
                .withCardOnBattlefield(2, "Craw Wurm").build().fixed()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            val original = game.state.objectRef(shaman)
            val ability = cardRegistry.requireCard("Krark-Clan Shaman").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, shaman, ability,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(game.findPermanent("Bonesplitter")!!)))).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, slay.name, shaman).error shouldBe null
            game.resolveOne()
            game.resolveOne()
            game.isOnBattlefield("Krark-Clan Shaman") shouldBe true
            game.state.objectRef(shaman) shouldNotBe original
            game.state.projectedState.hasKeyword(shaman, Keyword.DEATHTOUCH) shouldBe false
            game.finish()
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.isInGraveyard(1, "Krark-Clan Shaman") shouldBe true
        }

        test("pending Shaman damage keeps deathtouch when graveyard hate prevents its return") {
            val game = setup().withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardAttachedTo(1, ferocityResearchFixture.name, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Bonesplitter").withCardInHand(2, slay.name)
                .withCardInHand(1, exileGraveyardCard.name).withCardOnBattlefield(2, "Craw Wurm").build().fixed()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            val ability = cardRegistry.requireCard("Krark-Clan Shaman").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, shaman, ability,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(game.findPermanent("Bonesplitter")!!)))).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, slay.name, shaman).error shouldBe null
            game.resolveOne()
            game.isInGraveyard(1, "Krark-Clan Shaman") shouldBe true
            game.castSpellTargetingGraveyardCard(1, exileGraveyardCard.name, listOf(shaman)).error shouldBe null
            game.finish()
            (shaman in game.state.getExile(game.player1Id)) shouldBe true
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
        }
    }
}
