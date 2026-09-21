package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.arena.ArenaAgent
import com.wingedsheep.ai.arena.TableGameRunner
import com.wingedsheep.ai.arena.TableSetup
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class IndustrialWasteTempoGrixisPilotBenchmark : FunSpec({
    test("Industrial Waste Tempo A paired Grixis pilot").config(
        enabled = System.getenv("IW_TEMPO_GRIXIS_PILOT") == "true",
    ) {
        val repository = tempoGrixisRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
        }
        val industrialDecks = linkedMapOf(
            "control" to tempoGrixisParseMain(root.resolve("control/industrial-waste-v1.0-submitted.dck")),
            "tempo-a" to tempoGrixisParseMain(root.resolve("challengers/tempo-a.dck")),
        )
        val opponent = tempoGrixisParseMain(root.resolve("gauntlet/grixis-affinity-carlos-dc-2026-09-12.dck"))
        (industrialDecks.values + opponent).forEach { deck ->
            require(deck.size == 60)
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val seeds = (1..TEMPO_GRIXIS_SEED_COUNT).map { tempoGrixisSeedFor(TEMPO_GRIXIS_NAMESPACE, it) }
        val computedDigest = tempoGrixisVectorDigest(seeds)
        val registryText = Files.readString(root.resolve("seed-registry.json"))
        val registered = Regex(
            """(?s)"namespace"\s*:\s*"${TEMPO_GRIXIS_NAMESPACE}".*?"vector_sha256"\s*:\s*"([^"]+)""""
        ).find(registryText)?.groupValues?.get(1) ?: error("Tempo A Grixis namespace not registered")
        if (registered != computedDigest) {
            error("TEMPO_GRIXIS_DIGEST computed=" + computedDigest + " registered=" + registered)
        }

        val industrialBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val industrial = ArenaAgent(
            "industrial-waste-tempo-a-policy",
            industrialBase.copy(
                id = "industrial-waste-tempo-a-policy",
                advisorModules = industrialBase.advisorModules + IndustrialWasteAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )
        val grixisBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val grixis = ArenaAgent(
            "grixis-gate-6-readiness",
            grixisBase.copy(
                id = "grixis-gate-6-readiness",
                advisorModules = grixisBase.advisorModules + IndustrialWasteGrixisAdvisorModule,
            ),
        )

        val outcomes = buildList {
            seeds.forEachIndexed { seedIndex, seed ->
                industrialDecks.forEach { (deckName, deck) ->
                    repeat(2) { rotation ->
                        val industrialSeat = rotation
                        val observer = IndustrialWasteGoldfishObserver(registry, industrialSeat)
                        val agents = if (industrialSeat == 0) listOf(industrial, grixis) else listOf(grixis, industrial)
                        val decks = if (industrialSeat == 0) listOf(deck, opponent) else listOf(opponent, deck)
                        val game = TableGameRunner.play(
                            registry = registry, setup = TableSetup.HEADS_UP, agents = agents, decks = decks,
                            seed = seed, groupId = seedIndex + 1, rotation = rotation, maxTurns = 16,
                            maxActions = 4_000, trainingObserver = observer, skipMulligans = false,
                        )
                        add(TempoGrixisOutcome(
                            deckName, seedIndex + 1, seed, industrialSeat, game.completed, game.winnerSeat,
                            game.winnerSeat == industrialSeat, game.winnerSeat == 1 - industrialSeat,
                            game.turns, game.actions, game.drawReason, game.exception, game.illegalActions,
                            observer.snapshot()
                        ))
                    }
                }
            }
        }
        val invalid = outcomes.filter {
            it.exception != null || it.illegalActions.isNotEmpty() ||
                (it.drawReason.isNotEmpty() && !it.drawReason.startsWith("maxTurns") && !it.drawReason.startsWith("maxActions"))
        }
        val summaries = industrialDecks.keys.map { deck ->
            val s = outcomes.filter { it.deck == deck }
            TempoGrixisSummary(
                deck, s.size, s.count { it.industrialWon }, s.count { it.opponentWon },
                s.count { !it.industrialWon && !it.opponentWon }, s.count { it.metrics.tronByTurn5 },
                s.count { it.metrics.comboReadyTurn != null },
                s.sumOf { it.metrics.mulligans } / s.size.toDouble(),
                s.sumOf { it.metrics.coloredManaFailureTurns } / s.size.toDouble()
            )
        }
        val report = TempoGrixisReport(
            1, "preboard-matchup-capability-pilot", false, TEMPO_GRIXIS_NAMESPACE, seeds.size,
            computedDigest, outcomes, summaries, invalid.isEmpty()
        )
        val output = root.resolve("results/gate-6-tempo-a-grixis-pilot-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")
        check(invalid.isEmpty()) { "Tempo A Grixis pilot found ${invalid.size} invalid outcomes" }
    }
})

private const val TEMPO_GRIXIS_NAMESPACE = "IW-G6-TEMPO-A-GRIXIS-PILOT-V1"
private const val TEMPO_GRIXIS_SEED_COUNT = 2

@Serializable private data class TempoGrixisOutcome(
    val deck:String,val pair:Int,val seed:Long,val industrialSeat:Int,val completed:Boolean,
    val winnerSeat:Int?,val industrialWon:Boolean,val opponentWon:Boolean,val turns:Int,val actions:Int,
    val drawReason:String,val exception:String?,val illegalActions:Map<String,Int>,val metrics:IndustrialWasteGoldfishMetrics
)
@Serializable private data class TempoGrixisSummary(
    val deck:String,val games:Int,val industrialWins:Int,val opponentWins:Int,val draws:Int,
    val tronByTurn5:Int,val comboReady:Int,val meanMulligans:Double,val meanColoredManaFailureTurns:Double
)
@Serializable private data class TempoGrixisReport(
    val schemaVersion:Int,val evidenceClass:String,val promotionEligible:Boolean,val namespace:String,
    val seedCount:Int,val seedVectorSha256:String,val outcomes:List<TempoGrixisOutcome>,
    val summaries:List<TempoGrixisSummary>,val valid:Boolean
)
private fun tempoGrixisRepositoryRoot():Path {
    var p:Path?=Path.of("").toAbsolutePath()
    while(p!=null){if(Files.isDirectory(p.resolve("industrial-waste"))) return p;p=p.parent}
    error("Cannot locate repository root")
}
private fun tempoGrixisParseMain(path:Path):Deck {
    var main=false
    val cards=buildList {
        Files.readAllLines(path).forEach { raw ->
            val line=raw.trim()
            when {
                line=="[main]" -> main=true
                line.startsWith("[") -> main=false
                main && line.isNotEmpty() -> {
                    val i=line.indexOf(' '); val n=line.substring(0,i).toInt(); val name=line.substring(i+1)
                    repeat(n){add(name)}
                }
            }
        }
    }
    return Deck(cards)
}
private fun tempoGrixisSeedFor(namespace:String,index:Int):Long {
    val digest=MessageDigest.getInstance("SHA-256").digest("$namespace:$index".toByteArray(Charsets.UTF_8))
    return ByteBuffer.wrap(digest,0,Long.SIZE_BYTES).long.and(Long.MAX_VALUE).let { if(it==0L)1L else it }
}
private fun tempoGrixisVectorDigest(seeds:List<Long>):String =
    MessageDigest.getInstance("SHA-256").digest(seeds.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString(""){"%02x".format(it)}
