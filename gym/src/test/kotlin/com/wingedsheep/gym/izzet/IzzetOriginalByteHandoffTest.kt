package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.TestCards
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*

/** Two 60-basic-card seats; ONE real synthetic initializer and ONE accepted synthetic action. */
class IzzetOriginalByteHandoffTest : FunSpec({
    val registry = CardRegistry().apply { register(TestCards.all) }
    val json = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }
    val decks = listOf(List(60){ if(it % 2 == 0) "Forest" else "Island" },List(60){"Island"})
    val identity = IzzetSyntheticAttemptIdentity("izzet-synthetic-byte-handoff-20261009",
        IzzetSourcePins("77b91694b64c367cffeba9e3a2180b4772f6d76b",
            IzzetBoundInitialization.deckPin(decks[0]), "3".repeat(64), "4".repeat(64)))
    val spec = IzzetInitializationSpecification(identityJson=identity.bytes().toString(Charsets.UTF_8),
        seed=0x495A5A45545052L,startingPlayerIndex=0,
        players=(0..1).map { i -> IzzetInitializationPlayer("EXCLUDED_BASIC_"+i,
            "excluded-handoff-seat-"+i,decks[i],IzzetBoundInitialization.deckPin(decks[i])) })
    fun hash(b: ByteArray) = IzzetBoundInitialization.sha256(b)
    fun root(): Path = Files.createTempDirectory("izzet-original-handoff-").toRealPath()
    fun filePin(dir: Path, name: String) = hash(Files.readAllBytes(dir.resolve(name)))
    data class Case(val specRoot: Path,val firstRoot: Path,val handoffRoot: Path,
        val actionRoot: Path,val collectorRoot: Path,val pins: IzzetOriginalHandoffPins)
    fun all(c: Case) = listOf(c.specRoot,c.firstRoot,c.handoffRoot,c.actionRoot,c.collectorRoot)
    fun snapshot(roots:List<Path>) = roots.map { root -> Files.walk(root).use { s ->
        s.filter { Files.isRegularFile(it) }.toList().associate {
            root.relativize(it).toString() to Files.readAllBytes(it).toList()
        } } }
    fun prepared(): Case {
        val specRoot=root();val firstRoot=root();val handoffRoot=root();val actionRoot=root();val collectorRoot=root()
        val bytes=IzzetBoundInitialization.encode(spec);val sPin=hash(bytes)
        val firstPin=IzzetBoundInitialization.initializeOnce(firstRoot,specRoot,identity,bytes,sPin,registry)
        val witness=IzzetOriginalByteHandoff.reserveOnce(specRoot,firstRoot,handoffRoot,
            actionRoot,collectorRoot,identity,sPin,firstPin,registry)
        val first=Files.readAllBytes(firstRoot.resolve(identity.attemptId).resolve("initial.bin"))
        val canonical=json.decodeFromString(GameState.serializer(),first.toString(Charsets.UTF_8))
        json.encodeToString(GameState.serializer(),canonical).toByteArray().toList() shouldBe first.toList()
        // The accepted journal's trustedInitialize callback transfers already-stored original
        // canonical bytes. It never calls GameInitializer again or reopens first-root's slot.
        IzzetJournaledSubmission.create(actionRoot,identity,registry) {
            json.decodeFromString(GameState.serializer(),
                Files.readAllBytes(firstRoot.resolve(identity.attemptId).resolve("initial.bin"))
                    .toString(Charsets.UTF_8))
        }.executeOnce { menu -> IzzetNumberedProposal(menu.windowSha256,
            menu.offers.first { it.actionKind == "KeepHand" }.id) }
        val action=actionRoot.resolve(identity.attemptId)
        Files.readAllBytes(action.resolve("initial.bin")).toList() shouldBe first.toList()
        val intent=filePin(action,"0.intent");val result=filePin(action,"0.result")
        val evidence=IzzetReplayEvidence.collect(action,identity,firstPin,intent,result,registry)
        val ePin=hash(evidence)
        IzzetStoredReplayEvidence.publish(collectorRoot,action,identity,firstPin,intent,result,evidence,ePin,registry)
        return Case(specRoot,firstRoot,handoffRoot,actionRoot,collectorRoot,
            IzzetOriginalHandoffPins(sPin,firstPin,witness,intent,result,ePin))
    }
    fun check(c: Case,expected:Boolean=true,pins:IzzetOriginalHandoffPins=c.pins) {
        val roots=all(c);val before=snapshot(roots)
        val outcome=runCatching { IzzetOriginalByteHandoff.verify(c.specRoot,c.firstRoot,
            c.handoffRoot,c.actionRoot,c.collectorRoot,identity,pins,registry) }
        snapshot(roots) shouldBe before
        outcome.isSuccess shouldBe expected
    }

    test("one original first state transfers byte for byte into one accepted journal and stored collector") {
        val c=prepared();check(c);check(c)
        val originalDir=c.firstRoot.resolve(identity.attemptId)
        Files.list(originalDir).use { it.map { x->x.fileName.toString() }.toList().toSet() } shouldBe
            setOf("identity.json","initialization-intent","initial.bin","initialized.sha256")
        shouldThrowAny { IzzetOriginalByteHandoff.reserveOnce(c.specRoot,c.firstRoot,c.handoffRoot,
            c.actionRoot,c.collectorRoot,identity,c.pins.specification,c.pins.firstInitial,registry) }
    }
    test("each independently held reference mismatches without opening source or action writers") {
        val c=prepared()
        for (bad in listOf(c.pins.copy(specification="0".repeat(64)),
            c.pins.copy(firstInitial="0".repeat(64)),c.pins.copy(handoff="0".repeat(64)),
            c.pins.copy(actionIntent="0".repeat(64)),c.pins.copy(actionResult="0".repeat(64)),
            c.pins.copy(collector="0".repeat(64))))check(c,false,bad)
    }
    test("altered, missing, faulted and extra records across all independent roots fail read-only") {
        for (kind in listOf("spec","init","witness","witnessFault","action","collector","extra")) {
            val c=prepared();val d=identity.attemptId
            when(kind) {
                "spec" -> Files.delete(c.specRoot.resolve(d).resolve("specification.complete"))
                "init" -> Files.writeString(c.firstRoot.resolve(d).resolve("initialized.sha256"),"0".repeat(64)+"\n")
                "witness" -> Files.delete(c.handoffRoot.resolve(d).resolve("complete.sha256"))
                "witnessFault" -> Files.writeString(c.handoffRoot.resolve(d).resolve("fault"),"FAULT\n")
                "action" -> Files.delete(c.actionRoot.resolve(d).resolve("0.result"))
                "collector" -> Files.delete(c.collectorRoot.resolve(d).resolve("complete.sha256"))
                "extra" -> Files.writeString(c.actionRoot.resolve(d).resolve("extra"),"EXCLUDED\n")
            }
            check(c,false)
        }
    }
    test("wrong roots and coherent labels do not replace exact original material or witness") {
        val c=prepared()
        check(c.copy(actionRoot=c.firstRoot),false)
        check(c.copy(collectorRoot=c.actionRoot),false)
        val other=prepared()
        Files.writeString(other.firstRoot.resolve(identity.attemptId).resolve("initial.bin"),"{}")
        check(c.copy(firstRoot=other.firstRoot),false)
        Files.writeString(other.handoffRoot.resolve(identity.attemptId).resolve("binding.json"),"{}")
        check(c.copy(handoffRoot=other.handoffRoot),false)
    }
})
