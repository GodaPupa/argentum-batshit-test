import { test, expect } from '../../fixtures/scenarioFixture'

/**
 * E2E coverage for Retribution of the Ancients' composite counter-removal cost.
 *
 * The counter distribution starts with no allocations. Every creature with a
 * matching counter must still be offered, and an explicit allocation can be
 * spread across multiple eligible creatures.
 */
test.describe('Retribution of the Ancients', () => {
  test('offers all creatures with valid counters before any allocation is specified', async ({ createGame }) => {
    const { player1 } = await createGame({
      player1Name: 'AbzanPlayer',
      player2Name: 'Opponent',
      player1: {
        battlefield: [
          { name: 'Retribution of the Ancients' },
          { name: 'Swamp' },
          { name: 'Daru Stinger', summoningSickness: false, counters: { PLUS_ONE_PLUS_ONE: 2 } },
          { name: 'Glory Seeker', summoningSickness: false, counters: { PLUS_ONE_PLUS_ONE: 1 } },
          { name: 'Grizzly Bears', summoningSickness: false },
        ],
      },
      player2: {
        battlefield: [{ name: 'Glory Seeker' }],
      },
      phase: 'PRECOMBAT_MAIN',
      activePlayer: 1,
    })

    const p1 = player1.gamePage
    await p1.clickCard('Retribution of the Ancients')
    await p1.selectAction(', Remove X +1/+1')

    await p1.expectCounterRemovalOption('Daru Stinger')
    await p1.expectCounterRemovalOption('Glory Seeker')
    await expect(
      p1.page.locator('img[alt="Grizzly Bears"]').first()
        .locator('xpath=ancestor::*[@data-card-id][1]')
        .locator('button').filter({ hasText: /^\+$/ }),
    ).toHaveCount(0)
  })

  test('accepts explicit counter allocations distributed across eligible creatures', async ({ createGame }) => {
    const { player1, player2 } = await createGame({
      player1Name: 'AbzanPlayer',
      player2Name: 'Opponent',
      player1: {
        battlefield: [
          { name: 'Retribution of the Ancients' },
          { name: 'Swamp' },
          { name: 'Daru Stinger', summoningSickness: false, counters: { PLUS_ONE_PLUS_ONE: 1 } },
          { name: 'Glory Seeker', summoningSickness: false, counters: { PLUS_ONE_PLUS_ONE: 1 } },
        ],
      },
      player2: {
        battlefield: [{ name: 'Grizzly Bears' }],
      },
      phase: 'PRECOMBAT_MAIN',
      activePlayer: 1,
    })

    const p1 = player1.gamePage
    const p2 = player2.gamePage
    await p1.clickCard('Retribution of the Ancients')
    await p1.selectAction(', Remove X +1/+1')
    await p1.allocateCounterRemoval('Daru Stinger', 1)
    await p1.allocateCounterRemoval('Glory Seeker', 1)
    await p1.confirmDistribution()

    await p1.selectTarget('Grizzly Bears')
    await p1.confirmTargets()
    await p2.pass()

    await p1.expectOnBattlefield('Daru Stinger')
    await p1.expectOnBattlefield('Glory Seeker')
  })
})
