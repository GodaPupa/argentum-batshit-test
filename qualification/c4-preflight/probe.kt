package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.ai.insight.*
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/** Excluded synthetic probe. Compilation does not construct a scenario or choose an action. */
class R5R6DecisionDifferentialTest : FunSpec({
    test("six fixtures and two fixed decision sequences; no action submission") {
        check(System.getenv("ARGENTUM_R5_R6_DIFFERENTIAL_EXECUTE") == "AUTHORIZED_NEW_ORIGINAL_ONLY")
        check(System.getenv("GITHUB_RUN_ATTEMPT") == "1" && System.getenv("GITHUB_RUN_NUMBER") == "1")
        check(System.getenv("GITHUB_REPOSITORY") == "GodaPupa/argentum-batshit-test")
        check(System.getenv("GITHUB_EVENT_NAME") == "push")
        val out = Path.of(checkNotNull(System.getenv("ARGENTUM_DIFF_OUTPUT")))
        Files.createDirectories(out.parent)
        check(!Files.exists(out)) { "Refuse overwrite of original evidence" }
        val revision = checkNotNull(System.getenv("ARGENTUM_DIFF_REVISION"))
        check(revision in setOf("R5", "R6"))
        fun emit(body: JsonObject) {
            Files.writeString(out, body.toString()+"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
        val sequences = listOf(listOf(1),listOf(2),listOf(3),listOf(4),listOf(5),listOf(6),listOf(2,2),listOf(1,5,1))
        var failed = false
        for ((group, ids) in sequences.withIndex()) {
            val harness = DifferentialScenarios()
            val games = ids.distinct().associateWith { harness.fixture(it) }
            val captured = mutableListOf<Pair<GameState,AiDecisionInsight>>()
            val player = harness.player(games.getValue(ids.first()).player1Id, captured)
            for ((index, id) in ids.withIndex()) {
                val game = games.getValue(id); val state = game.state
                try {
                    val before = stateBytes(state)
                    check(wire.decodeFromString(GameState.serializer(), before) == state) { "State roundtrip loss" }
                    val legal = harness.legal(state, game.player1Id)
                    val input = buildJsonObject {
                        put("profileId", PestMonsterTronPolicy.profile.id); put("stateBytes", before); put("legal", exact(legal)); put("memory", memory(player))
                    }
                    emit(buildJsonObject { put("type","INPUT");put("revision",revision);put("group",group);put("index",index);put("fixture",id);put("input",input) })
                    captured.clear(); R6DiffObserver.begin()
                    val choice = try { player.chooseFrom(state, legal) } catch (t: Throwable) {
                        val observation=R6DiffObserver.end()
                        emit(buildJsonObject {put("type","ERROR");put("group",group);put("index",index);put("exception",t.javaClass.name);put("message",t.message ?: "");put("observation",observation)})
                        throw t
                    }
                    val observation=R6DiffObserver.end()
                    val after=stateBytes(state)
                    check(game.state === state && before == after) { "Input mutated" }
                    check(captured.size == 1) { "Expected one full priority insight" }
                    check(stateBytes(captured.single().first) == before) { "Evaluation state differs from fixture" }
                    val insight=captured.single().second
                    val semantic=buildJsonObject {
                        put("choice",exact(choice));put("insight",exact(insight, setOf("thinkTimeMs")))
                        put("stateAfter",after);put("memoryAfter",memory(player))
                    }
                    emit(buildJsonObject {
                        put("type","OUTPUT");put("revision",revision);put("group",group);put("index",index);put("fixture",id)
                        put("semantic",semantic);put("thinkTimeMs",insight.thinkTimeMs);put("observation",observation)
                    })
                } catch (t: Throwable) {
                    failed=true
                    emit(buildJsonObject {put("type","INCOMPLETE");put("group",group);put("index",index);put("exception",t.javaClass.name);put("message",t.message ?: "")})
                    break
                }
            }
        }
        emit(buildJsonObject {put("type","END");put("revision",revision);put("complete",!failed)})
        check(!failed) { "Incomplete original: inspect retained JSONL" }
    }
})

private val wire = Json { encodeDefaults=true; allowStructuredMapKeys=true }
private fun stateBytes(s: GameState) = wire.encodeToString(GameState.serializer(),s)

/** Exact scalar bits and collection iteration; no entity renaming, array sorting or tolerances. */
private fun exact(x: Any?, omit: Set<String> = emptySet()): JsonElement = when(x) {
    null -> JsonNull
    is String -> JsonPrimitive(x)
    is Boolean -> JsonPrimitive(x)
    is Double -> buildJsonObject {put("doubleBits",x.toRawBits().toString())}
    is Float -> buildJsonObject {put("floatBits",x.toRawBits().toString())}
    is Number -> JsonPrimitive(x.toString())
    is Enum<*> -> JsonPrimitive(x.javaClass.name+":"+x.name)
    is GameAction -> wire.encodeToJsonElement(GameAction.serializer(),x)
    is Map<*,*> -> JsonArray(x.entries.map { JsonArray(listOf(exact(it.key),exact(it.value))) })
    is Iterable<*> -> JsonArray(x.map {exact(it)})
    is Array<*> -> JsonArray(x.map {exact(it)})
    else -> {
        val k=x::class
        val names=k.primaryConstructor?.parameters?.mapNotNull {it.name}
        check(names != null || k.objectInstance != null) { "Unsupported observation type: ${k.qualifiedName}" }
        buildJsonObject {
            put("class",k.qualifiedName ?: error("Anonymous state"))
            for(n in names.orEmpty()) if(n !in omit) {
                val p=k.memberProperties.single {it.name==n};p.isAccessible=true
                put(n,exact(p.getter.call(x)))
            }
        }
    }
}
private fun field(x: Any,n: String): Any? = x.javaClass.getDeclaredField(n).let {it.isAccessible=true;it.get(x)}
private fun memory(player: AIPlayer): JsonObject {
    val strategist=checkNotNull(field(player,"strategist"))
    val responder=checkNotNull(field(player,"responder"))
    val simulator=checkNotNull(field(player,"simulator"))
    return buildJsonObject {
        for(n in listOf("positionsActedFrom","expiringConditionCommitment","survivalCommitment","pendingSurvivalSetup")) put(n,exact(field(strategist,n)))
        put("committedFollowUp",exact(field(responder,"committedFollowUp")))
        put("isResolving",exact(field(simulator,"isResolving")))
    }
}

private class DifferentialScenarios : ScenarioTestBase() {
    private fun seeded()=scenario().withPlayers().withRngSeed(0x0E57_C017L)
    fun player(id: com.wingedsheep.sdk.model.EntityId,captured: MutableList<Pair<GameState,AiDecisionInsight>>) =
        AIPlayer.create(cardRegistry,id,PestMonsterTronPolicy.profile,insightSink={s,i->captured.add(s to i)})
    fun legal(s: GameState,id: com.wingedsheep.sdk.model.EntityId)=LegalActionEnumerator.create(cardRegistry).enumerate(s,id,com.wingedsheep.engine.legalactions.EnumerationMode.ACTIONS_ONLY)
    fun fixture(id: Int): TestGame = when(id) {
        1 -> {
            val game = seeded()
                .withTurnNumber(6)
                .withLifeTotal(1, 22)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Blood Researcher")
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Chainer's Edict")
                .withCardInHand(1, "Chainer's Edict")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Follow the Lumarets")
                .withCardOnBattlefield(1, "Blood Researcher")
                .build()
            game.state = game.state.copy(phase = Phase.POSTCOMBAT_MAIN, step = Step.POSTCOMBAT_MAIN)
            game
        }
        2 -> {
            val game = seeded()
                .withTurnNumber(6)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Blood Researcher")
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Blood Researcher")
                .build()
            game.state = game.state.copy(phase = Phase.POSTCOMBAT_MAIN, step = Step.POSTCOMBAT_MAIN)
            game
        }
        3 -> {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Blood Researcher")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Blood Researcher")
                .build()
            game
        }
        4 -> {
            val game = seeded()
                .withLifeTotal(1, 2)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Carrier Thrall")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Pest Mascot")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()
            game
        }
        5 -> {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Duress")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Pest Mascot")
                .build()
            game
        }
        6 -> {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Bone Shards")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Pest Mascot")
                .withCardOnBattlefield(2, "Mons's Goblin Raiders")
                .build()
            game
        }
        else -> error("Unapproved fixture")
    }
}
