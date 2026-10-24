import { describe, it, expect } from 'vitest'
import { mergeResult } from './pipelinePhases'
import type { GameAction, LegalActionInfo, ClientGameState, EntityId } from '@/types'

const id = (s: string) => s as EntityId
const action = { type: 'CastSpell', playerId: id('p'), cardId: id('spell'), chosenModes: [0], splicedCardIds: [id('splice')] } as GameAction
const info = { action, actionType: 'CastSpell', description: 'fixture' } as LegalActionInfo
const state = { players: [{ playerId: id('p') }], cards: {} } as unknown as ClientGameState

describe('public target group transport', () => {
  it('retains an omitted original group before a later choice', () => {
    const result = mergeResult(action, info, { type: 'targeting', selectedTargets: [id('p')], selectedTargetGroups: [[], [id('p')]] }, state)
    expect(result).toMatchObject({ targets: [{ type: 'Player', playerId: 'p' }], targetGroups: [[], [{ type: 'Player', playerId: 'p' }]] })
  })
  it('retains separate occurrences of the same recipient', () => {
    const result = mergeResult(action, info, { type: 'targeting', selectedTargets: [id('p'), id('p')], selectedTargetGroups: [[id('p')], [id('p')]] }, state)
    expect(result).toMatchObject({ targetGroups: [[{ type: 'Player', playerId: 'p' }], [{ type: 'Player', playerId: 'p' }]] })
  })
  it('keeps mode and splice choices unchanged', () => {
    const result = mergeResult(action, info, { type: 'targeting', selectedTargets: [id('p')], selectedTargetGroups: [[id('p')]] }, state)
    expect(result).toMatchObject({ chosenModes: [0], splicedCardIds: ['splice'] })
  })
  it('legacy flat phase does not invent a group boundary', () => {
    const result = mergeResult(action, info, { type: 'targeting', selectedTargets: [id('p')] }, state)
    expect(result).not.toHaveProperty('targetGroups')
  })
  it('later cost merge preserves captured public groups', () => {
    const grouped = mergeResult(action, info, { type: 'targeting', selectedTargets: [id('p')], selectedTargetGroups: [[], [id('p')]] }, state)
    const paid = mergeResult(grouped, info, { type: 'costPayment', costType: 'Sacrifice', selectedTargets: [id('cost')] }, state)
    expect(paid).toMatchObject({ targetGroups: [[], [{ type: 'Player', playerId: 'p' }]] })
  })
})

import { create } from 'zustand'
import { vi } from 'vitest'
import { createTargetingSlice } from './targetingSlice'
import type { GameStore, TargetingState } from '../types'

function groupedStore() {
  const advancePipeline = vi.fn()
  const store = create<GameStore>()((set, get, api) => ({
    ...createTargetingSlice(set, get, api), interactionEpoch: 'group-epoch',
    pipelineState: { interactionEpoch: 'group-epoch', actionInfo: info, accumulatedAction: action, remainingPhases: [{ type: 'targeting' }] },
    gameState: state, advancePipeline, cancelPipeline: vi.fn(),
  }) as unknown as GameStore)
  const initial: TargetingState = {
    action, validTargets: [id('a'), id('b')], selectedTargets: [], minTargets: 0, maxTargets: 1,
    currentRequirementIndex: 0, allSelectedTargets: [], totalRequirements: 2,
    targetRequirements: [
      { index: 0, description: 'optional', minTargets: 0, maxTargets: 1, validTargets: [id('a'), id('b')] },
      { index: 1, description: 'required', minTargets: 1, maxTargets: 1, validTargets: [id('a'), id('b')] },
    ],
  }
  store.getState().startTargeting(initial)
  return { store, advancePipeline }
}

describe('public target group collection', () => {
  it('saves the omitted group before calling the legacy phase entrypoint', () => {
    const { store, advancePipeline } = groupedStore()
    store.getState().confirmTargeting('group-epoch')
    store.getState().addTarget(id('b'))
    store.getState().confirmTargeting('group-epoch')
    expect(store.getState().pipelineState?.pendingTargetGroups).toEqual([[], [id('b')]])
    expect(advancePipeline).toHaveBeenCalledWith({ type: 'targeting', selectedTargets: [id('b')] })
  })
  it('revising an earlier requirement preserves only the revised groups', () => {
    const { store } = groupedStore()
    store.getState().addTarget(id('a'))
    store.getState().confirmTargeting('group-epoch')
    store.getState().goBackTargeting()
    store.getState().removeTarget(id('a'))
    store.getState().confirmTargeting('group-epoch')
    store.getState().addTarget(id('b'))
    store.getState().confirmTargeting('group-epoch')
    expect(store.getState().pipelineState?.pendingTargetGroups).toEqual([[], [id('b')]])
  })
})
