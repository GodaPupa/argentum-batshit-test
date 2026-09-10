package com.wingedsheep.engine.mechanics

/**
 * Generic maximum bipartite matching via Kuhn's augmenting-path algorithm.
 *
 * A single definition of "match each left node to a distinct right node", parameterized only by an
 * edge predicate, shared by every rules situation that reduces to bipartite matching:
 *  - [com.wingedsheep.engine.handlers.costs.CraftSlotMatching] — heterogeneous Craft slots ↔ chosen
 *    materials (CR 702.167): every slot must be saturated.
 *  - [com.wingedsheep.engine.mechanics.combat.BlockPhaseManager] — "must be blocked if able"
 *    attackers ↔ blockers free to cover them (CR 509.1c): the *size* of the maximum matching bounds
 *    how many requirements can be obeyed.
 *
 * Left and right node identities may share a type (e.g. both `EntityId`); the matcher treats them
 * as disjoint sides regardless, so a node appearing on both sides is not conflated.
 */
object BipartiteMatching {

    /**
     * A maximum matching between [left] and [right] under [edge]. Returns a map from each matched
     * right node to the left node it's assigned to (so `result.size` is the matching's size and
     * `result.values` are the matched left nodes). Left nodes that can't be augmented are absent.
     *
     * Running Kuhn's augmenting search once per left node yields a maximum-cardinality matching.
     *
     * @param edge may this left node be matched to this right node?
     */
    fun <L, R> maximumMatching(left: List<L>, right: List<R>, edge: (L, R) -> Boolean): Map<R, L> {
        val matchedRightToLeft = HashMap<R, L>()

        fun augment(leftNode: L, visited: MutableSet<R>): Boolean {
            for (rightNode in right) {
                if (!edge(leftNode, rightNode)) continue
                if (!visited.add(rightNode)) continue
                val currentLeft = matchedRightToLeft[rightNode]
                if (currentLeft == null || augment(currentLeft, visited)) {
                    matchedRightToLeft[rightNode] = leftNode
                    return true
                }
            }
            return false
        }

        for (leftNode in left) augment(leftNode, HashSet())
        return matchedRightToLeft
    }

    /**
     * True iff every node in [left] can be matched to its own distinct right node under [edge] —
     * i.e. there is a matching that saturates the whole left side. Trivially true when [left] is
     * empty.
     */
    fun <L, R> canSaturateLeft(left: List<L>, right: List<R>, edge: (L, R) -> Boolean): Boolean =
        left.isEmpty() || maximumMatching(left, right, edge).size == left.size
}
