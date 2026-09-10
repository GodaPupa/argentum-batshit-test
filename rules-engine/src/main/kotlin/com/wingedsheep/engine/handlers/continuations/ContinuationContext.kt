package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.state.GameState

typealias CheckForMore = (GameState, List<GameEvent>) -> ExecutionResult
