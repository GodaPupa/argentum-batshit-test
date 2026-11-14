import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ClientCard, ClientGameState, LegalActionInfo, StateUpdateMessage } from '@/types'
import type { GameWebSocket } from '@/network/websocket'
import { buildActionOptions } from './actionOptions'

vi.stubGlobal('localStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
vi.stubGlobal('window', { addEventListener: () => {}, removeEventListener: () => {} })
const { useGameStore } = await import('../store/gameStore')
const { createGameplayHandlers } = await import('../store/slices/handlers/gameplayHandlers')
const { setWebSocket } = await import('../store/slices/shared')
const { entityId, Phase, Step } = await import('@/types')
const ME = entityId('surge-player')
const CARD = entityId('surge-card')
const send = vi.fn()
const handlers = createGameplayHandlers(useGameStore.setState, useGameStore.getState)
const card = { id: CARD, name: 'Reckless Bushwhacker', manaCost: '{2}{R}', cardTypes: ['CREATURE'] } as unknown as ClientCard
const position: ClientGameState = {
  viewingPlayerId: ME, cards: {}, zones: [], players: [], currentPhase: Phase.PRECOMBAT_MAIN,
  currentStep: Step.PRECOMBAT_MAIN, activePlayerId: ME, priorityPlayerId: ME, turnNumber: 1,
  isGameOver: false, winnerId: null, combat: null,
}
function offered(alternative: boolean, cost = alternative ? '{1}{R}' : '{2}{R}'): LegalActionInfo {
  return {
    actionType: alternative ? 'CastWithAlternativeCost' : 'CastSpell',
    description: alternative ? 'Cast Reckless Bushwhacker (alternative cost)' : 'Cast Reckless Bushwhacker',
    action: { type: 'CastSpell', playerId: ME, cardId: CARD,
      ...(alternative ? { useAlternativeCost: true, alternativeCostType: 'SELF_ALTERNATIVE' } : {}) },
    requiresTargets: false, targetCount: 0, validTargets: [], manaCostString: cost,
  }
}
function receive(epoch: string, legalActions: LegalActionInfo[]) {
  const message: StateUpdateMessage = { type: 'stateUpdate', state: position, events: [], legalActions, interactionEpoch: epoch }
  handlers.onStateUpdate(message)
}
beforeEach(() => {
  send.mockClear()
  setWebSocket({ send, onStateVersionReceived: vi.fn(), requestResync: vi.fn() } as unknown as GameWebSocket)
  useGameStore.setState({ sessionId: 'surge-transport', playerId: ME, interactionEpoch: null, gameOverState: null })
  receive('surge-origin', [offered(false), offered(true)])
})
describe('Surge action transport', () => {
  it('UI01 both server options survive menu', () => {
    const normal = offered(false), alternative = offered(true)
    const options = buildActionOptions(card, [normal, alternative]).filter(x => x.action !== null)
    expect(options).toHaveLength(2)
    expect(new Set(options.map(x => x.key)).size).toBe(2)
    expect(options.map(x => x.manaCost)).toEqual(['{2}{R}', '{1}{R}'])
    expect(options.map(x => x.action)).toEqual([normal, alternative])
  })
  it('UI02 alternative only menu retains selected payload', () => {
    const alternative = offered(true)
    const options = buildActionOptions(card, [alternative]).filter(x => x.action !== null)
    expect(options).toHaveLength(1)
    expect(options[0]!.manaCost).toBe('{1}{R}')
    expect(options[0]!.action).toBe(alternative)
    expect(options[0]!.action!.action).toMatchObject({ useAlternativeCost: true, alternativeCostType: 'SELF_ALTERNATIVE' })
  })
  it('UI03 normal only menu invents no surge action', () => {
    const normal = offered(false)
    const options = buildActionOptions(card, [normal]).filter(x => x.action !== null)
    expect(options).toHaveLength(1)
    expect(options[0]!.action).toBe(normal)
    expect(options[0]!.action!.action).not.toHaveProperty('alternativeCostType')
  })
  it('UI04 equal prices retain distinct selections', () => {
    const normal = offered(false, '{R}'), alternative = offered(true, '{R}')
    const options = buildActionOptions(card, [normal, alternative]).filter(x => x.action !== null)
    expect(options).toHaveLength(2)
    expect(options.map(x => x.manaCost)).toEqual(['{R}', '{R}'])
    expect(new Set(options.map(x => x.key)).size).toBe(2)
    expect(options.map(x => x.action!.action)).toEqual([normal.action, alternative.action])
  })
  it('UI05 serialization preserves selected alternative', () => {
    const alternative: LegalActionInfo = JSON.parse(JSON.stringify(offered(true)))
    receive('surge-serialized', [alternative])
    const action = useGameStore.getState().legalActions[0]!
    useGameStore.getState().startPipeline(action)
    expect(send).toHaveBeenCalledOnce()
    expect(send.mock.calls[0]![0]).toMatchObject({ type: 'submitAction', interactionEpoch: 'surge-serialized',
      action: { type: 'CastSpell', cardId: CARD, useAlternativeCost: true, alternativeCostType: 'SELF_ALTERNATIVE' } })
  })
  it('UI06 server action remains authoritative after stale update', () => {
    const stale = useGameStore.getState().legalActions[1]!
    receive('surge-replacement', [offered(false)])
    useGameStore.getState().startPipeline(stale)
    useGameStore.getState().submitAction(stale.action, stale.interactionEpoch)
    expect(send).not.toHaveBeenCalled()
    const options = buildActionOptions(card, useGameStore.getState().legalActions).filter(x => x.action !== null)
    expect(options).toHaveLength(1)
    expect(options[0]!.action!.action).not.toHaveProperty('alternativeCostType')
  })
})
