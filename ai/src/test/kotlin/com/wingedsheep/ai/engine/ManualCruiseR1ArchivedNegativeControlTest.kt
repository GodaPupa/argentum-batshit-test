package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * REAL archived immutable R5 decision snapshots, evaluated independently
 * under the exact same source with R5 and the isolated CRUISE R1 candidate.
 *
 * These are deliberately NEGATIVE controls: no historical cast opportunity
 * for Llanowar Elves is offered. An unchanged choice proves source isolation,
 * not that the new CRUISE policy wins more games.
 *
 * NO GameInitializer, no deck shuffle, no action-loop, no turn advancement
 * and no full games. Previously published archive snapshots are not a fresh
 * blinded prospective sample.
 */
class ManualCruiseR1ArchivedNegativeControlTest : FunSpec({
    test("original R15 snapshots remain R5-identical when Elf cannot be cast") {
        val archive = Path.of(requireNotNull(System.getenv("MANUAL_CRUISE_R15_ARCHIVE")))
        val original = "251dc4cd30d955bbee3fef6172ca285664df78923859efa258299e5cd3fe7a5f"
        fun digest(bytes: ByteArray) =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
        digest(Files.readAllBytes(archive)) shouldBe original

        val snapshots = listOf(
            Triple(1336, "9fed665708996ce0468b87337bc2924a09e748acaee41dd5877a74a4f537ce1f",
                29),
            Triple(1497, "0a8d7182119fd3c7e19f6e02092179131b9fcfdb50d447e8b1e44c794584dffe",
                33),
            Triple(1998, "f309851969bdfd5c51c08a89e3dc306840b357ed1c8aebf95039581e947408db",
                41),
        )
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val serializer = Json {
            serializersModule = engineSerializersModule
            allowStructuredMapKeys = true
        }
        val sim = GameSimulator(registry)

        ZipFile(archive.toFile()).use { zip ->
            for ((index, stateDigest, globalTurn) in snapshots) {
                val stateBytes = zip.getInputStream(
                    requireNotNull(zip.getEntry("r15-output/state-before-$index.json"))
                ).readBytes()
                digest(stateBytes) shouldBe stateDigest
                val state = serializer.decodeFromString<GameState>(stateBytes.decodeToString())
                val insightBytes = zip.getInputStream(
                    requireNotNull(zip.getEntry("r15-output/insight-before-$index.json"))
                ).readBytes()
                val insight = serializer.parseToJsonElement(
                    insightBytes.decodeToString()
                ).jsonObject
                val seat = EntityId(insight.getValue("playerId").jsonPrimitive.content)
                state.turnNumber shouldBe globalTurn
                val offered = insight.getValue("options").jsonArray
                    .mapNotNull { it.jsonObject["cardName"]?.jsonPrimitive?.contentOrNull }
                check("Llanowar Elves" !in offered) {
                    "Source archive is not an Elf-free negative control at $index"
                }
                val elfCasts = sim.getLegalActions(state, seat).filter { action ->
                    val spell = action.action as? CastSpell ?: return@filter false
                    state.getEntity(spell.cardId)?.get<CardComponent>()?.name == "Llanowar Elves" &&
                        action.affordable
                }
                elfCasts.size shouldBe 0

                val r5 = AIPlayer.create(
                    registry, seat, ManualTransmissionR5Candidate.profile
                ).chooseAction(state)
                val cruise = AIPlayer.create(
                    registry, seat, ManualTransmissionCruiseR1Candidate.profile
                ).chooseAction(state)
                cruise shouldBe r5
                println(
                    "CRUISE_ARCHIVED_NEGATIVE actionIndex=$index turn=$globalTurn " +
                    "player=${seat.value} originalR5=$r5 candidate=$cruise " +
                    "affordableElfCount=0 noGames=true"
                )
            }
        }
    }
})
