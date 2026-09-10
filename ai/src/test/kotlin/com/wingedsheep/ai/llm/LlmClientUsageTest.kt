package com.wingedsheep.ai.llm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * Verifies the OpenRouter usage/cost fields deserialize off a representative chat-completion body.
 * (The live cost numbers must be verified against OpenRouter itself; this pins the parsing.)
 */
class LlmClientUsageTest : StringSpec({

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    "parses usage.cost and token counts from an OpenRouter response" {
        // Shape returned by OpenRouter when the request sets usage: { include: true }.
        val body = """
            {
              "id": "gen-abc123",
              "model": "anthropic/claude-sonnet-4",
              "choices": [
                { "message": { "role": "assistant", "content": "Forest" }, "finish_reason": "stop" }
              ],
              "usage": {
                "prompt_tokens": 1200,
                "completion_tokens": 50,
                "total_tokens": 1250,
                "cost": 0.004235,
                "cost_details": { "upstream_inference_cost": 0.004 },
                "prompt_tokens_details": { "cached_tokens": 800 }
              }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<ChatCompletionResponse>(body)

        parsed.id shouldBe "gen-abc123"
        parsed.model shouldBe "anthropic/claude-sonnet-4"
        parsed.choices.first().message?.content shouldBe "Forest"
        parsed.usage?.promptTokens shouldBe 1200
        parsed.usage?.completionTokens shouldBe 50
        parsed.usage?.totalTokens shouldBe 1250
        parsed.usage!!.cost!! shouldBeExactly 0.004235
    }

    "tolerates a response with usage tokens but no cost (non-OpenRouter / cost not requested)" {
        val body = """
            {
              "choices": [ { "message": { "role": "assistant", "content": "ok" } } ],
              "usage": { "prompt_tokens": 10, "completion_tokens": 2, "total_tokens": 12 }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<ChatCompletionResponse>(body)
        parsed.usage?.totalTokens shouldBe 12
        parsed.usage?.cost.shouldBeNull()
    }

    "tolerates a response with no usage block at all" {
        val body = """{ "choices": [ { "message": { "role": "assistant", "content": "ok" } } ] }"""
        val parsed = json.decodeFromString<ChatCompletionResponse>(body)
        parsed.usage.shouldBeNull()
        parsed.choices.first().message?.content shouldBe "ok"
    }
})
