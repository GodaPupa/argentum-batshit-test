package com.wingedsheep.mtg.sets

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.mtg.sets.definitions.ogw.cards.RecklessBushwhacker
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.serialization.CardExporter
import com.wingedsheep.sdk.serialization.CardLoader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class RecklessBushwhackerRegistrationTest : FunSpec({
    val card=RecklessBushwhacker
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun root():Path=generateSequence(Path.of("").toAbsolutePath()){it.parent}.first{Files.exists(it.resolve("settings.gradle.kts"))}
    test("RG01 OGW116 canonical discovery exactly once") {
        val found=MtgSetCatalog.all.flatMap { set->set.cards.filter { it.name==card.name }.map { set.code to it } }
        found.size shouldBe 1;found.single().first shouldBe "OGW"
        found.single().second.metadata.collectorNumber shouldBe "116"
        CardDiscovery.findIn("com.wingedsheep.mtg.sets.definitions.ogw.cards").count { it.name==card.name } shouldBe 1
    }
    test("RG02 printed fields match pinned source") {
        card.name shouldBe "Reckless Bushwhacker";card.manaCost shouldBe ManaCost.parse("{2}{R}")
        card.typeLine.toString() shouldBe "Creature — Goblin Warrior Ally"
        card.creatureStats?.power shouldBe com.wingedsheep.sdk.model.CharacteristicValue.Fixed(2)
        card.creatureStats?.toughness shouldBe com.wingedsheep.sdk.model.CharacteristicValue.Fixed(1)
        card.colors shouldBe setOf(Color.RED);card.metadata.rarity shouldBe Rarity.UNCOMMON
        card.metadata.collectorNumber shouldBe "116";card.metadata.artist shouldBe "Kieran Yanner"
        card.metadata.imageUri shouldBe "https://cards.scryfall.io/normal/front/0/4/0405b1b9-976a-4aaf-bec6-fa006decea74.jpg?1783937905"
    }
    test("RG03 snapshot preserves all existing OGW entries") {
        val text=Files.readString(root().resolve("mtg-sets/src/test/resources/snapshots/cards/OGW.json"))
        val chunks=text.trimEnd('\n').split("\n\n// ")
        chunks.count { it.startsWith("Reckless Bushwhacker\n") } shouldBe 1
        val original=chunks.filterNot{it.startsWith("Reckless Bushwhacker\n")}.joinToString("\n\n// ")+"\n"
        hash(original.toByteArray()) shouldBe "0b4900db42ff6f4e8f94ebdca3b1b8d59c96ac6e877743b4ba825f93f2558ae3"
    }
    test("RG04 serialized script matches exact surge composition") {
        val restored=CardLoader.fromJson(CardExporter.exportToJson(card))
        restored.script.selfAlternativeCost shouldBe SelfAlternativeCost(ManaCost.parse("{1}{R}"),condition=Conditions.YouCastSpellsThisTurn(1))
        restored.keywords shouldBe setOf(Keyword.HASTE)
        val trigger=restored.script.triggeredAbilities.single()
        trigger.trigger shouldBe Triggers.EntersBattlefield.event
        trigger.interveningIf shouldBe Conditions.CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE)
        val each=trigger.effect as ForEachEffect
        (each.space as IterationSpace.Group).filter shouldBe GroupFilter(GameObjectFilter.Creature.youControl()).other()
        val body=each.body as CompositeEffect
        body.effects shouldBe listOf(
            com.wingedsheep.sdk.dsl.Effects.ModifyStats(1,0,EffectTarget.Self,Duration.EndOfTurn),
            com.wingedsheep.sdk.dsl.Effects.GrantKeyword(Keyword.HASTE,EffectTarget.Self,Duration.EndOfTurn))
    }
    test("RG05 Manual registry resolves new card without official games") {
        val deck=Files.readAllBytes(root().resolve("experiments/manual-transmission/control/v0.7.decklist.txt"))
        hash(deck) shouldBe "2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1"
        deck.toString(Charsets.UTF_8).lineSequence().count { it=="1 Reckless Bushwhacker" } shouldBe 1
        val registry=MtgSetCatalog.all.flatMap { it.cards }.groupBy { it.name }
        registry["Reckless Bushwhacker"]!!.single() shouldBe card
        // Name lookup only. No game, pilot, random seed or deck mutation is constructed here.
    }
})
