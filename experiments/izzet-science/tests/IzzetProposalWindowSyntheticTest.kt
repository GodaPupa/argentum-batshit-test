package com.wingedsheep.gym.izzet

/** Pure literal protocol fixtures. No card registry, opponent, seed or engine is loaded. */
object IzzetProposalWindowSyntheticTest {
    @JvmStatic fun main(args: Array<String>) {
        val pins = IzzetSourcePins("a".repeat(40), "b".repeat(64), "c".repeat(64), "d".repeat(64))
        val epoch = IzzetDecisionEpoch("synthetic-attempt", 1, "actor", "actor", null)
        val offers = listOf(IzzetMaterializedOffer(7, "PassPriority", null, null, "exact-public-action"))
        val observation = "masked-fixture"
        fun open() = IzzetProposalWindow.open(pins, epoch, observation, offers)
        fun rejects(block: () -> Unit) { check(runCatching(block).isFailure) }
        var count = 0
        fun test(name: String, body: () -> Unit) { body(); count++; println("PASS $name") }
        test("exact-offer-resolution-and-one-use") {
            val w = open(); val p = IzzetNumberedProposal(w.pilotMenu().windowSha256, 7)
            check(w.resolve(p, pins, epoch, observation, offers) == "exact-public-action")
            rejects { w.resolve(p, pins, epoch, observation, offers) }
        }
        test("each-source-pin-drift-rejects") {
            listOf(pins.copy(sourceCommit = "e".repeat(40)), pins.copy(deckSha256 = "e".repeat(64)),
                pins.copy(policySha256 = "e".repeat(64)), pins.copy(runtimeSha256 = "e".repeat(64))).forEach {
                val w = open(); rejects { w.resolve(IzzetNumberedProposal(w.pilotMenu().windowSha256, 7), it, epoch, observation, offers) }
            }
        }
        test("actor-priority-attempt-sequence-drift") {
            listOf(epoch.copy(attemptId = "other"), epoch.copy(sequence = 2),
                epoch.copy(actor = "other", priorityActor = "other")).forEach {
                val w = open(); rejects { w.resolve(IzzetNumberedProposal(w.pilotMenu().windowSha256, 7), pins, it, observation, offers) }
            }
            rejects { epoch.copy(priorityActor = "other") }
        }
        test("changed-masked-observation-rejects") {
            val w = open(); rejects { w.resolve(IzzetNumberedProposal(w.pilotMenu().windowSha256, 7), pins, epoch, "changed", offers) }
        }
        test("changed-current-offer-rejects") {
            val w = open(); rejects { w.resolve(IzzetNumberedProposal(w.pilotMenu().windowSha256, 7), pins, epoch, observation,
                listOf(offers.single().copy(canonicalAction = "changed-action"))) }
        }
        test("unknown-id-consumes-without-fallback") {
            val w = open(); val hash = w.pilotMenu().windowSha256
            rejects { w.resolve(IzzetNumberedProposal(hash, 999), pins, epoch, observation, offers) }
            rejects { w.resolve(IzzetNumberedProposal(hash, 7), pins, epoch, observation, offers) }
        }
        test("edited-proposal-hash-rejects") {
            val w = open(); rejects { w.resolve(IzzetNumberedProposal("0".repeat(64), 7), pins, epoch, observation, offers) }
        }
        test("unknown-action-and-duplicate-ids-reject") {
            rejects { IzzetProposalWindow.open(pins, epoch, observation, offers + offers) }
            rejects { IzzetProposalWindow.open(pins, epoch, observation, listOf(offers.single().copy(actionKind = "UnreviewedAction"))) }
        }
        test("typed-domain-exact-id-and-unsupported-rejection") {
            val e = epoch.copy(decisionId = "d1")
            val q = IzzetMaterializedOffer(4, "SubmitDecision", "YesNo", "d1", "exact-answer")
            val w = IzzetProposalWindow.open(pins, e, observation, listOf(q))
            check(w.resolve(IzzetNumberedProposal(w.pilotMenu().windowSha256, 4), pins, e, observation, listOf(q)) == "exact-answer")
            rejects { IzzetProposalWindow.open(pins, e, observation, listOf(q.copy(decisionId = "d2"))) }
            rejects { IzzetProposalWindow.open(pins, e, observation, listOf(q.copy(decisionKind = "UnreviewedQuestion"))) }
            rejects { IzzetProposalWindow.open(pins, epoch, observation, listOf(q)) }
        }
        test("caller-list-mutation-does-not-change-captured-menu") {
            val mutable = offers.toMutableList()
            val w = IzzetProposalWindow.open(pins, epoch, observation, mutable)
            mutable.clear()
            check(w.pilotMenu().offers == offers)
            val exposed = w.pilotMenu().offers as MutableList<IzzetMaterializedOffer>
            exposed.clear()
            check(w.pilotMenu().offers == offers)
        }
        check(count == 10); println("RESULT 10/10")
    }
}
