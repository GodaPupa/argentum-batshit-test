package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.token.AttackingTokenDefenderChooser
import com.wingedsheep.engine.handlers.effects.token.CreateTokenExecutor
import com.wingedsheep.engine.handlers.effects.token.CreateTokenCopyOfTargetExecutor
import com.wingedsheep.engine.handlers.effects.token.TokenCreationReplacementHelper
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import com.wingedsheep.sdk.scripting.effects.CreateTokenCopyOfTargetEffect

/**
 * Handles token-related continuation resumptions:
 * - TokenCreationReplacementContinuation (Mirrormind Crown yes/no)
 */
class TokenContinuationResumer(
    private val services: EngineServices
) : ContinuationResumerModule, AutoResumerModule {

    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(TokenCreationReplacementContinuation::class, ::resumeTokenCreationReplacement),
        resumer(AttackingTokenDefenderContinuation::class, ::resumeAttackingDefenders),
    )

    override fun autoResumers(): List<AutoResumer<*>> = listOf(
        autoResumer(CreateTokenRecipientsContinuation::class) { state, continuation, events, checkForMore ->
            val result = tokenExecutor().createForRecipients(
                state, continuation.effect, continuation.context, continuation.baseCount,
                continuation.remainingControllers, continuation.createdTokens,
            )
            mergeAndContinue(publish(result).toExecutionResult(), events, checkForMore)
        },
    )

    private fun tokenExecutor() = CreateTokenExecutor(
        staticAbilityHandler = StaticAbilityHandler(services.cardRegistry),
        cardRegistry = services.cardRegistry,
        tokenArtRegistry = services.tokenArtRegistry,
    )

    private fun copyExecutor() = CreateTokenCopyOfTargetExecutor(
        staticAbilityHandler = StaticAbilityHandler(services.cardRegistry),
        cardRegistry = services.cardRegistry,
    )

    private fun publish(result: EffectResult): EffectResult = if (result.isSuccess) result.copy(
        state = exposeCollectionsToNextFrame(result.state, result.updatedCollections),
    ) else result

    private fun resumeAttackingDefenders(
        state: GameState,
        continuation: AttackingTokenDefenderContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore,
    ): ExecutionResult {
        if (response !is TargetsResponse || response.selectedTargets.keys != (0 until continuation.count).toSet()) {
            return ExecutionResult.error(state, "Expected one defender choice for each entering token")
        }
        val currentLegal = AttackingTokenDefenderChooser.legalDefenders(state, continuation.controllerId)
        val defenders = (0 until continuation.count).map { index ->
            val chosen = response.selectedTargets[index]?.singleOrNull()
            if (chosen == null || chosen !in continuation.legalDefenders || chosen !in currentLegal) {
                return ExecutionResult.error(state, "Illegal defender for entering token ${index + 1}")
            }
            chosen
        }
        val result = when (val effect = continuation.effect) {
            is CreateTokenEffect -> tokenExecutor().createPreparedTokens(
                state, effect, continuation.context, continuation.count, continuation.controllerId,
                attackingDefenders = defenders, checkReplacements = false,
            )
            is CreateTokenCopyOfTargetEffect -> copyExecutor().createTokens(
                state, effect, continuation.context, continuation.controllerId, continuation.count,
                auraHostId = null, attackingDefenders = defenders,
            )
            else -> EffectResult.error(state, "Unsupported attacking token effect")
        }
        return mergeAndContinue(publish(result).toExecutionResult(), emptyList(), checkForMore)
    }

    private fun resumeTokenCreationReplacement(
        state: GameState,
        continuation: TokenCreationReplacementContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is YesNoResponse) {
            return ExecutionResult.error(state, "Expected yes/no response for token creation replacement")
        }

        val context = continuation.effectContext

        if (response.choice) {
            // Player chose to replace: create copies of the attached permanent.
            // Pass cardRegistry so the token applies the attached permanent's printed
            // "enters with N counters" replacement effects (per Mirrormind Crown rulings).
            val result = if (continuation.preparedCount) TokenCreationReplacementHelper.createPreparedReplacementCopies(
                state, continuation.attachedPermanentId, continuation.tokenControllerId ?: context.controllerId,
                continuation.tokenCount, continuation.originalEffect, context,
                services.cardRegistry, StaticAbilityHandler(services.cardRegistry),
            ) else TokenCreationReplacementHelper.createAttachedPermanentCopies(
                state,
                continuation.attachedPermanentId,
                continuation.tokenControllerId ?: context.controllerId,
                continuation.tokenCount,
                cardRegistry = services.cardRegistry,
                staticAbilityHandler = StaticAbilityHandler(services.cardRegistry)
            )
            return mergeAndContinue(publish(result).toExecutionResult(), emptyList(), checkForMore)
        } else {
            // Player declined: execute original token creation effect
            // The source is already marked as "offered this turn" so the replacement
            // won't fire again when we re-execute the original effect.
            val controllerId = continuation.tokenControllerId ?: context.controllerId
            val effectResult = if (continuation.preparedCount) when (val effect = continuation.originalEffect) {
                is CreateTokenEffect -> tokenExecutor().createPreparedTokens(
                    state, effect, context, continuation.tokenCount, controllerId,
                )
                is CreateTokenCopyOfTargetEffect -> copyExecutor().createPreparedTokens(
                    state, effect, context, controllerId, continuation.tokenCount,
                )
                else -> EffectResult.error(state, "Unsupported prepared token replacement")
            } else services.effectExecutorRegistry.execute(state, continuation.originalEffect, context)
            return mergeAndContinue(publish(effectResult).toExecutionResult(), emptyList(), checkForMore)
        }
    }
}
