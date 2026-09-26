package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.state.components.player.LibraryOrderingPlan
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/** Constructible only after the independent Python verifier durably consumes this exact allocation. */
internal class IndustrialWasteV2OfficialAdmission private constructor(
    val input: IndustrialWasteV2AllocationInput,
    val journalTail: String,
) {
    fun matches(plan: LibraryOrderingPlan): Boolean =
        plan.namespace == input.namespace && plan.row == input.row && plan.openingOrders == input.openingOrders

    companion object {
        fun admit(root: Path, output: Path, index: Int, tail: String): IndustrialWasteV2OfficialAdmission {
            val answer = command(root, output, "admit", "--index", index.toString(), "--tail", tail)
            check(answer.getValue("status").jsonPrimitive.content == "ADMITTED_ONCE")
            val input = IndustrialWasteV2AllocationTrace.CODEC.decodeFromJsonElement(
                IndustrialWasteV2AllocationInput.serializer(), answer.getValue("request"))
            check(input.allocationId == "IW_V2_R1_${index.toString().padStart(4, '0')}")
            check(input.namespace == "IW_V2_R1_ORDERINGS_2026_09_25")
            return IndustrialWasteV2OfficialAdmission(input, answer.getValue("tail").jsonPrimitive.content)
        }

        fun command(root: Path, output: Path, operation: String, vararg arguments: String): JsonObject {
            val process = ProcessBuilder(listOf("python3", "industrial-waste/v2/r1_execute.py", operation,
                "--root", root.toString(), "--output", output.toString()) + arguments)
                .directory(root.toFile()).redirectErrorStream(true).start()
            val answer = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "R1 $operation refused: $answer" }
            return IndustrialWasteV2AllocationTrace.CODEC.parseToJsonElement(answer).jsonObject
        }
    }
}

/**
 * Concrete dormant invocation. CI without IW_V2_R1_PREPARED_DIRECTORY performs no initialization.
 * With it, Python checks the published runtime/authorization/remote exclusive claim before every
 * allocation. The fixed order is exactly 1..512; any exception, invalid metric or replay failure
 * ends the attempt. No resumptions, replacement allocations, partial comparison or fallback exist.
 */
class IndustrialWasteV2R1OfficialExecutionTest : FunSpec({
    val requested = System.getenv("IW_V2_R1_PREPARED_DIRECTORY")
    test("execute the frozen R1 corpus only under independently verified durable authority").config(
        enabled = requested != null,
    ) {
        val output = Path.of(requireNotNull(requested)).toAbsolutePath().normalize()
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.isDirectory(it.resolve("industrial-waste/v2")) }
        var tail = IndustrialWasteV2AllocationTrace.CODEC.parseToJsonElement(
            Files.readString(output.resolve("tails/0000.json"))).jsonObject.getValue("tail").jsonPrimitive.content
        for (index in 1..512) {
            val admission = IndustrialWasteV2OfficialAdmission.admit(root, output, index, tail)
            tail = admission.journalTail
            IndustrialWasteV2AllocationRunner.runOfficial(admission,
                output.resolve("evidence").resolve(admission.input.allocationId))
            val result = IndustrialWasteV2OfficialAdmission.command(root, output, "complete",
                "--index", index.toString(), "--tail", tail)
            tail = result.getValue("tail").jsonPrimitive.content
            check(result.getValue("status").jsonPrimitive.content == "VALID") {
                "Invalid official allocation is quarantined; no successor allocation is permitted"
            }
        }
        IndustrialWasteV2OfficialAdmission.command(root, output, "finalize", "--tail", tail)
    }
})
