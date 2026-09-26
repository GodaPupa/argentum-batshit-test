package com.wingedsheep.engine.event

import com.wingedsheep.engine.core.CardEntityFactory
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GrantTriggeredAbility
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.effects.LoseLifeEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TriggerAbilityResolverTest : FunSpec({
    test("both lookup paths retain grant order, duplicate abilities, and the ungranted base list") {
        fun ability(name: String) = TriggeredAbility(
            AbilityId(name), Triggers.Dies.event,
            effect = LoseLifeEffect(1, EffectTarget.PlayerRef(Player.You)),
        )
        val owner = EntityId.of("owner")
        val targetId = EntityId.of("target")
        val providerId = EntityId.of("provider")
        val base = listOf(ability("base"))
        val temporary = ability("temporary")
        val static = ability("static")
        val grant = GrantTriggeredAbility(static, GroupFilter.AllCreatures)
        val target = CardDefinition.creature("Trigger Target", ManaCost.ZERO, subtypes = emptySet(), power = 1, toughness = 1,
            script = CardScript(triggeredAbilities = base))
        val provider = CardDefinition.enchantment("Trigger Provider", ManaCost.ZERO,
            script = CardScript(staticAbilities = listOf(grant)))
        val registry = CardRegistry().apply { register(listOf(target, provider)) }
        val abilities = AbilityRegistry().apply { register(target.name, base) }
        val resolver = TriggerAbilityResolver(registry, abilities)
        val zone = ZoneKey(owner, Zone.BATTLEFIELD)
        val ungranted = GameState(
            entities = mapOf(targetId to CardEntityFactory.create(target, owner)),
            zones = mapOf(zone to listOf(targetId)),
        )
        (resolver.getTriggeredAbilities(targetId, target.name, ungranted) === base) shouldBe true
        (resolver.getTriggeredAbilitiesWithProviders(targetId, target.name, ungranted, emptyList()) === base) shouldBe true

        val fallback = TriggerAbilityResolver(registry, AbilityRegistry())
        (fallback.getTriggeredAbilities(targetId, target.name, ungranted) === base) shouldBe true
        (fallback.getTriggeredAbilitiesWithProviders(targetId, target.name, ungranted, emptyList()) === base) shouldBe true

        val granted = ungranted.copy(
            entities = ungranted.entities + (providerId to CardEntityFactory.create(provider, owner)),
            zones = mapOf(zone to listOf(targetId, providerId)),
            grantedTriggeredAbilities = listOf(
                GrantedTriggeredAbility(providerId, ability("unrelated"), Duration.Permanent),
                GrantedTriggeredAbility(targetId, temporary, Duration.Permanent),
                GrantedTriggeredAbility(targetId, ability("expired"), Duration.WhileAffectedTapped),
                GrantedTriggeredAbility(targetId, temporary, Duration.Permanent),
            ),
        )
        val expected = base + listOf(temporary, temporary, static)
        resolver.getTriggeredAbilities(targetId, target.name, granted) shouldBe expected
        resolver.getTriggeredAbilitiesWithProviders(
            targetId, target.name, granted,
            listOf(TriggerIndex.GrantProviderEntry(grant, owner, providerId)),
        ) shouldBe expected
    }

    test("both lookup paths apply artifact, subtype, controller, state and self-exclusion filters") {
        fun ability(name: String) = TriggeredAbility(
            AbilityId(name), Triggers.YourUpkeep.event,
            effect = LoseLifeEffect(1, EffectTarget.PlayerRef(Player.You)),
        )
        val owner = EntityId.of("owner")
        val opponent = EntityId.of("opponent")
        val providerId = EntityId.of("provider")
        val ownElfId = EntityId.of("own-elf")
        val opposingElfId = EntityId.of("opposing-elf")
        val humanId = EntityId.of("tapped-human")
        val artifactId = EntityId.of("artifact")
        val enchantmentId = EntityId.of("enchantment")
        val artifactAbility = ability("artifacts")
        val elfAbility = ability("other-owned-elves")
        val tappedAbility = ability("tapped-creatures")
        val grants = listOf(
            GrantTriggeredAbility(artifactAbility, GroupFilter.AllArtifacts),
            GrantTriggeredAbility(elfAbility, GroupFilter.allCreaturesWithSubtype("Elf").youControl().other()),
            GrantTriggeredAbility(tappedAbility, GroupFilter.AllCreatures.tapped()),
        )
        val provider = CardDefinition.creature(
            "Granting Elf", ManaCost.ZERO, setOf(Subtype.ELF), 1, 1,
            script = CardScript(staticAbilities = grants),
        )
        val elf = CardDefinition.creature("Ordinary Elf", ManaCost.ZERO, setOf(Subtype.ELF), 1, 1)
        val human = CardDefinition.creature("Ordinary Human", ManaCost.ZERO, setOf(Subtype.HUMAN), 1, 1)
        val artifact = CardDefinition.artifact("Ordinary Artifact", ManaCost.ZERO)
        val enchantment = CardDefinition.enchantment("Ordinary Enchantment", ManaCost.ZERO)
        val registry = CardRegistry().apply { register(listOf(provider, elf, human, artifact, enchantment)) }
        val resolver = TriggerAbilityResolver(registry, AbilityRegistry())
        val state = GameState(
            entities = mapOf(
                providerId to CardEntityFactory.create(provider, owner),
                ownElfId to CardEntityFactory.create(elf, owner),
                opposingElfId to CardEntityFactory.create(elf, opponent),
                humanId to CardEntityFactory.create(human, owner).with(TappedComponent),
                artifactId to CardEntityFactory.create(artifact, owner),
                enchantmentId to CardEntityFactory.create(enchantment, owner),
            ),
            zones = mapOf(
                ZoneKey(owner, Zone.BATTLEFIELD) to listOf(providerId, ownElfId, humanId, artifactId, enchantmentId),
                ZoneKey(opponent, Zone.BATTLEFIELD) to listOf(opposingElfId),
            ),
        )
        val providers = grants.map { TriggerIndex.GrantProviderEntry(it, owner, providerId) }
        val expectations = listOf(
            Triple(providerId, provider.name, emptyList()),
            Triple(ownElfId, elf.name, listOf(elfAbility)),
            Triple(opposingElfId, elf.name, emptyList()),
            Triple(humanId, human.name, listOf(tappedAbility)),
            Triple(artifactId, artifact.name, listOf(artifactAbility)),
            Triple(enchantmentId, enchantment.name, emptyList()),
        )
        for ((id, name, expected) in expectations) {
            withClue("$id ($name): only matching static grants apply") {
                resolver.getTriggeredAbilities(id, name, state) shouldBe expected
                resolver.getTriggeredAbilitiesWithProviders(id, name, state, providers) shouldBe expected
            }
        }
    }
})
