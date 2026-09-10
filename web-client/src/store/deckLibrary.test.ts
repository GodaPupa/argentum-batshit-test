import { describe, expect, it } from 'vitest'
import { buildDraftedDeckSave, type PoolCardPrinting } from './deckLibrary'

const card = (name: string, setCode?: string, collectorNumber?: string): PoolCardPrinting => ({
  name,
  setCode: setCode ?? null,
  collectorNumber: collectorNumber ?? null,
})

describe('buildDraftedDeckSave', () => {
  it('pins each drafted card to the printing it was opened as', () => {
    const pool = [card('Lightning Bolt', 'M10', '146'), card('Llanowar Elves', 'M10', '189')]
    const { cards, entries } = buildDraftedDeckSave(['Lightning Bolt', 'Lightning Bolt', 'Llanowar Elves'], {}, pool)

    expect(cards).toEqual({ 'Lightning Bolt': 2, 'Llanowar Elves': 1 })
    expect(entries).toEqual([
      { name: 'Lightning Bolt', count: 2, printing: { setCode: 'M10', collectorNumber: '146' } },
      { name: 'Llanowar Elves', count: 1, printing: { setCode: 'M10', collectorNumber: '189' } },
    ])
  })

  it('folds basic-land counts into the same cards/entries, preserving their printing', () => {
    const pool = [card('Grizzly Bears', 'LEA', '195')]
    const basics = [card('Forest', 'BLB', '280')]
    const { cards, entries } = buildDraftedDeckSave(['Grizzly Bears'], { Forest: 7 }, [...pool, ...basics])

    expect(cards).toEqual({ 'Grizzly Bears': 1, Forest: 7 })
    expect(entries).toContainEqual({ name: 'Forest', count: 7, printing: { setCode: 'BLB', collectorNumber: '280' } })
  })

  it('keeps a card without a printing as a name-only entry', () => {
    const pool = [card('Real Card', 'BLB', '1'), card('Test Card')]
    const { entries } = buildDraftedDeckSave(['Real Card', 'Test Card'], {}, pool)

    expect(entries).toEqual([
      { name: 'Real Card', count: 1, printing: { setCode: 'BLB', collectorNumber: '1' } },
      { name: 'Test Card', count: 1 },
    ])
  })

  it('returns undefined entries when no card resolves to a printing (stays name-only)', () => {
    const pool = [card('Test A'), card('Test B')]
    const { cards, entries } = buildDraftedDeckSave(['Test A', 'Test B'], {}, pool)

    expect(cards).toEqual({ 'Test A': 1, 'Test B': 1 })
    expect(entries).toBeUndefined()
  })

  it('takes the first printing seen when the pool holds the same name twice', () => {
    const pool = [card('Plains', 'BLB', '270'), card('Plains', 'M10', '232')]
    const { entries } = buildDraftedDeckSave([], { Plains: 3 }, pool)

    expect(entries).toEqual([{ name: 'Plains', count: 3, printing: { setCode: 'BLB', collectorNumber: '270' } }])
  })
})
