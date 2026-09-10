package com.wingedsheep.engine.mechanics.cost

import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.state.GameState

/**
 * Outcome of an attempt to pay a [com.wingedsheep.sdk.scripting.costs.PayCost] through the shared
 * [CostPaymentService].
 *
 * A *cost* (CR 118) is all-or-nothing and affordability-checkable before commitment, so its outcome
 * is a small closed set rather than a free-form effect result:
 *
 * - [Unaffordable] — the payer cannot pay (CR 118.3: "a player can't pay a cost they can't pay").
 *   Returned synchronously by [CostPaymentService.pay]; no decision is shown.
 * - [Pending] — payment requires player input. A [com.wingedsheep.engine.core.CostPaymentContinuation]
 *   has been pushed onto the continuation stack; the terminal outcome ([Paid] / [Declined]) is
 *   realized when that continuation resumes.
 * - [Paid] — the cost was fully paid; [state] already reflects the payment.
 * - [Declined] — the payer could afford the cost but chose not to pay it.
 *
 * [CostPaymentService.pay] returns only [Unaffordable] or [Pending] — every affordable cost prompts
 * before committing, so [Paid] / [Declined] are produced inside the continuation resumer (which runs
 * the corresponding `onPaid` / `onDeclined` follow-up from the [CostPaymentContext]). They exist as
 * first-class results so a future caller that drives payment without a follow-up effect can branch on
 * them directly.
 */
sealed interface PaymentResult {
    val state: GameState
    val events: List<GameEvent>

    /** The cost was fully paid; [state] reflects the payment. */
    data class Paid(
        override val state: GameState,
        override val events: List<GameEvent> = emptyList()
    ) : PaymentResult

    /** The payer could afford the cost but chose not to pay it. */
    data class Declined(
        override val state: GameState,
        override val events: List<GameEvent> = emptyList()
    ) : PaymentResult

    /** The payer cannot pay the cost; no prompt was shown (CR 118.3). */
    data class Unaffordable(
        override val state: GameState,
        override val events: List<GameEvent> = emptyList()
    ) : PaymentResult

    /** Payment needs player input; a [com.wingedsheep.engine.core.CostPaymentContinuation] is pushed. */
    data class Pending(
        override val state: GameState,
        val pendingDecision: PendingDecision,
        override val events: List<GameEvent> = emptyList()
    ) : PaymentResult
}
