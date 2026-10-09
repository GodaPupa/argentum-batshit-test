package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Excluded initialization-only synthetic seats; no Izzet strategy or operational opponent. */
class IzzetBoundInitializationTest : FunSpec({
    val registry = CardRegistry().apply { register(TestCards.all) }
    val json = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }
    fun hash(b: ByteArray) = IzzetBoundInitialization.sha256(b)
    val decks = listOf(List(60) { if (it % 2 == 0) "Forest" else "Island" }, List(60) { "Island" })
    val identity = IzzetSyntheticAttemptIdentity("izzet-synthetic-bound-initialization",
        IzzetSourcePins("82df89920de93b4c04217ce58bd11565fdad32e1", IzzetBoundInitialization.deckPin(decks[0]),
            "3".repeat(64), "4".repeat(64)))
    fun spec() = IzzetInitializationSpecification(identityJson = identity.bytes().toString(Charsets.UTF_8),
        seed = 0x495A5A45545052L, startingPlayerIndex = 0,
        players = (0..1).map { IzzetInitializationPlayer("EXCLUDED_BASIC_$it", "excluded-initial-seat-$it",
            decks[it], IzzetBoundInitialization.deckPin(decks[it])) })
    fun bytes() = IzzetBoundInitialization.encode(spec())
    fun root(): Path {
        val e = System.getenv("IZZET_INITIALIZATION_EVIDENCE_ROOT")
        return (if (e == null) Files.createTempDirectory("izzet-init-") else Files.createTempDirectory(Path.of(e), "fixture-")).toRealPath()
    }
    fun snapshot(root: Path) = Files.walk(root).use { s -> s.filter { Files.isRegularFile(it) }.toList()
        .associate { root.relativize(it).toString() to Files.readAllBytes(it).toList() } }
    data class Case(val root: Path, val specRoot: Path, val initialPin: String)
    fun sd(c: Case) = c.specRoot.resolve(identity.attemptId)
    fun id(c: Case) = c.root.resolve(identity.attemptId)
    fun preserve(path: Path, values: Map<String, List<Byte>>) {
        values.forEach { (name, b) -> val p = path.resolve(name); Files.createDirectories(p.parent); Files.write(p, b.toByteArray()) }
    }
    fun create(): Case {
        val r = root(); val sr = root()
        val pin = IzzetBoundInitialization.initializeOnce(r, sr, identity, bytes(), hash(bytes()), registry)
        // Preserve the original successful initializer evidence before adversarial mutation.
        val original = root(); preserve(original.resolve("attempt"), snapshot(r)); preserve(original.resolve("specification"), snapshot(sr))
        return Case(r, sr, pin)
    }
    fun check(c: Case, success: Boolean = true, sPin: String = hash(bytes()), iPin: String = c.initialPin,
              expectedIdentity: IzzetSyntheticAttemptIdentity = identity, reg: CardRegistry = registry) {
        val before = snapshot(c.root) to snapshot(c.specRoot)
        val audit = root(); preserve(audit.resolve("before-attempt"), before.first); preserve(audit.resolve("before-spec"), before.second)
        Files.writeString(audit.resolve("inputs.json"), buildJsonObject {
            put("identity", expectedIdentity.bytes().toString(Charsets.UTF_8)); put("specPin", sPin); put("initialPin", iPin)
            put("expectedSuccess", success); put("executionAuthorized", false); put("authenticatedProvenance", false)
        }.toString())
        val result = runCatching { IzzetBoundInitialization.verify(sd(c), id(c), expectedIdentity, sPin, iPin, reg) }
        val after = snapshot(c.root) to snapshot(c.specRoot)
        preserve(audit.resolve("after-attempt"), after.first); preserve(audit.resolve("after-spec"), after.second)
        Files.writeString(audit.resolve("outcome.json"), buildJsonObject {
            put("success", result.isSuccess); put("expectedSuccess", success); put("unchanged", before == after)
            put("errorClass", result.exceptionOrNull()?.javaClass?.name); put("executionAuthorized", false)
        }.toString())
        after shouldBe before
        result.isSuccess shouldBe success
    }
    fun reseal(c: Case, b: ByteArray): String {
        val pin = hash(b); Files.write(sd(c).resolve("specification.json"), b)
        for (n in listOf("specification.sha256", "specification.complete")) Files.writeString(sd(c).resolve(n), pin + "\n")
        return pin
    }
    test("original pinned specification reproduces exact real initializer state without state return or actions") {
        val c = create(); check(c); check(c)
        val s = spec()
        val expected = GameInitializer(registry).initializeGame(GameConfig(players = s.players.map {
            PlayerConfig(it.name, Deck(it.cards), startingLife = 20, playerId = EntityId(it.playerId)) }, format = Format.Standard,
            startingHandSize = 7, skipMulligans = false, useHandSmoother = false, startingPlayerIndex = 0, seed = s.seed))
        val raw = json.encodeToString(GameState.serializer(), expected.state).toByteArray()
        Files.readAllBytes(id(c).resolve("initial.bin")).toList() shouldBe raw.toList()
        c.initialPin shouldBe hash(raw)
        Files.list(id(c)).use { it.map { p -> p.fileName.toString() }.toList().toSet() } shouldBe
            setOf("identity.json", "initialization-intent", "initial.bin", "initialized.sha256")
        val before = snapshot(c.root) to snapshot(c.specRoot)
        shouldThrowAny { IzzetBoundInitialization.initializeOnce(c.root, c.specRoot, identity, bytes(), hash(bytes()), registry) }
        (snapshot(c.root) to snapshot(c.specRoot)) shouldBe before
    }
    test("recomputed specification pins cannot conceal changed seed starting seat or player identity") {
        for (s in listOf(spec().copy(seed = spec().seed + 1), spec().copy(startingPlayerIndex = 1),
            spec().copy(players = spec().players.mapIndexed { i, p -> if (i == 1) p.copy(playerId = "changed-seat") else p }))) {
            val c = create(); val pin = reseal(c, IzzetBoundInitialization.encode(s)); check(c, false, sPin = pin)
        }
    }
    test("each declared source runtime policy deck or attempt identity drift fails") {
        val c = create()
        val p = identity.pins
        for (other in listOf(identity.copy(attemptId = "izzet-synthetic-other"),
            identity.copy(pins = p.copy(sourceCommit = "0".repeat(40))), identity.copy(pins = p.copy(runtimeSha256 = "0".repeat(64))),
            identity.copy(pins = p.copy(policySha256 = "0".repeat(64))), identity.copy(pins = p.copy(deckSha256 = "0".repeat(64)))))
            check(c, false, expectedIdentity = other)
        check(c, false, sPin = "0".repeat(64)); check(c, false, iPin = "0".repeat(64))
    }
    test("missing incomplete faulted and symlink evidence fails without repair") {
        for (mode in listOf("specification.json", "specification.sha256", "specification.complete", "initial.bin", "initialized.sha256", "fault", "symlink")) {
            val c = create()
            when (mode) {
                "fault" -> Files.writeString(sd(c).resolve("fault"), "EXCLUDED_FAULT\n")
                "symlink" -> {
                    val p = sd(c).resolve("specification.json"); val saved = c.specRoot.resolve("saved-spec.json")
                    Files.move(p, saved); Files.createSymbolicLink(p, saved)
                }
                "initial.bin", "initialized.sha256" -> Files.delete(id(c).resolve(mode))
                else -> Files.delete(sd(c).resolve(mode))
            }
            check(c, false)
        }
    }
    test("noncanonical extra fields promoted authority and unsupported profile fail even resealed") {
        val base = json.parseToJsonElement(bytes().toString(Charsets.UTF_8)).jsonObject
        val bad = listOf(" ".toByteArray() + bytes(),
            JsonObject(base + ("extra" to JsonPrimitive("x"))).toString().toByteArray(),
            IzzetBoundInitialization.encode(spec().copy(executionAuthorized = true)),
            IzzetBoundInitialization.encode(spec().copy(authenticatedProvenance = true)),
            IzzetBoundInitialization.encode(spec().copy(fixtureDeclarationsOnly = false)),
            IzzetBoundInitialization.encode(spec().copy(schema = "OTHER_PROFILE")))
        for (b in bad) { val c = create(); check(c, false, sPin = reseal(c, b)) }
    }
    test("changed deck contents or claimed deck digest fails original correspondence") {
        val c = create(); val s = spec(); val changed = List(60) { "Forest" }
        val other = s.copy(players = listOf(s.players[0], s.players[1].copy(cards = changed,
            deckSha256 = IzzetBoundInitialization.deckPin(changed))))
        check(c, false, sPin = reseal(c, IzzetBoundInitialization.encode(other)))
        val d = create(); val invalid = s.copy(players = listOf(s.players[0].copy(deckSha256 = "0".repeat(64)), s.players[1]))
        check(d, false, sPin = reseal(d, IzzetBoundInitialization.encode(invalid)))
    }
    test("resealed mismatched initial state and truncation cannot substitute for reconstruction") {
        val c = create()
        val state = json.decodeFromString(GameState.serializer(), Files.readString(id(c).resolve("initial.bin")))
        for (b in listOf(json.encodeToString(GameState.serializer(), state.copy(turnNumber = 999)).toByteArray(), byteArrayOf(1, 2, 3))) {
            Files.write(id(c).resolve("initial.bin"), b); Files.writeString(id(c).resolve("initialized.sha256"), hash(b) + "\n")
            check(c, false, iPin = hash(b))
        }
    }
    test("initializer failure preserves prebound inputs consumes attempt and cannot verify or retry") {
        val r = root(); val sr = root()
        shouldThrowAny { IzzetBoundInitialization.initializeOnce(r, sr, identity, bytes(), hash(bytes()), CardRegistry()) }
        val c = Case(r, sr, "0".repeat(64))
        Files.readAllBytes(sd(c).resolve("specification.json")).toList() shouldBe bytes().toList()
        Files.readString(sd(c).resolve("specification.complete")) shouldBe hash(bytes()) + "\n"
        Files.exists(id(c).resolve("initialization-intent")) shouldBe true
        Files.exists(id(c).resolve("initial.bin")) shouldBe false
        Files.exists(id(c).resolve("fault")) shouldBe true
        Files.exists(sd(c).resolve("fault")) shouldBe true
        check(c, false)
        val before = snapshot(r) to snapshot(sr)
        shouldThrowAny { IzzetBoundInitialization.initializeOnce(r, sr, identity, bytes(), hash(bytes()), registry) }
        (snapshot(r) to snapshot(sr)) shouldBe before
        val invalidRoot = root(); val invalidSpecRoot = root()
        shouldThrowAny { IzzetBoundInitialization.initializeOnce(invalidRoot, invalidSpecRoot, identity, bytes(), "0".repeat(64), registry) }
        Files.exists(invalidRoot.resolve(identity.attemptId)) shouldBe false
        Files.exists(invalidSpecRoot.resolve(identity.attemptId)) shouldBe false
    }
})
