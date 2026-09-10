package com.wingedsheep.gameserver.tournament.llm

import com.wingedsheep.ai.llm.LlmUsage
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/** Aggregated token usage + dollar cost for one bucket (a game session, or a deckbuild). */
data class LlmCost(
    val calls: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalTokens: Long,
    val costUsd: Double,
    /** False if any contributing call's endpoint didn't report a dollar cost (so [costUsd] is a floor). */
    val costKnown: Boolean
) {
    operator fun plus(other: LlmCost) = LlmCost(
        calls = calls + other.calls,
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        totalTokens = totalTokens + other.totalTokens,
        costUsd = costUsd + other.costUsd,
        costKnown = costKnown && other.costKnown
    )

    companion object {
        val ZERO = LlmCost(0, 0, 0, 0, 0.0, true)
    }
}

/**
 * Accumulates LLM token usage + cost per arbitrary string bucket. The LLM-tournament uses the game
 * session id as the bucket for in-game decisions, and a participant id for deck building, so cost
 * can be attributed per game / per deck build. Thread-safe (records arrive on AI coroutine threads).
 */
@Component
class LlmCostTracker {
    private val buckets = ConcurrentHashMap<String, MutableAcc>()

    private class MutableAcc {
        var calls = 0
        var promptTokens = 0L
        var completionTokens = 0L
        var totalTokens = 0L
        var costUsd = 0.0
        var costKnown = true

        @Synchronized
        fun add(usage: LlmUsage) {
            calls++
            promptTokens += usage.promptTokens
            completionTokens += usage.completionTokens
            totalTokens += usage.totalTokens
            val c = usage.costUsd
            if (c != null) costUsd += c else costKnown = false
        }

        @Synchronized
        fun snapshot() = LlmCost(calls, promptTokens, completionTokens, totalTokens, costUsd, costKnown)
    }

    fun record(bucket: String, usage: LlmUsage) {
        buckets.computeIfAbsent(bucket) { MutableAcc() }.add(usage)
    }

    fun snapshot(bucket: String): LlmCost = buckets[bucket]?.snapshot() ?: LlmCost.ZERO

    fun clear(bucket: String) {
        buckets.remove(bucket)
    }
}
