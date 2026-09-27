package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.core.ResolvedLibrarySearchPortion
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.sdk.model.EntityId

/**
 * One-step, nonserialized origin of an actual authorized search Gather.
 * Only the immediately following sibling Select may consume it.
 */
data class AuthorizedLibrarySearchCandidate(
    val collectionName: String,
    val gatheredObjects: List<ObjectRef>,
    val libraryOwner: EntityId,
    val searcher: EntityId,
    val portion: ResolvedLibrarySearchPortion,
    val sourceOrigin: ObjectRef,
    val resolutionKey: String,
)
