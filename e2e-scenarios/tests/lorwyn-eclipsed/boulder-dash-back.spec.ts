import { test, expect } from '../../fixtures/scenarioFixture'

/**
 * E2E browser tests for multi-target back navigation (Boulder Dash).
 *
 * Card: Boulder Dash ({1}{R}) — Sorcery
 * "Boulder Dash deals 2 damage to any target and 1 damage to any other target."
 *
 * Covers: revising an already-confirmed target via the "← Back" button before
 * the spell is submitted — the restored step keeps its pick selected, and the
 * revised pick correctly re-filters the second requirement ("any other target").
 */
test.describe('Boulder Dash — multi-target back navigation', () => {
  test('go back to revise the first target before casting', async ({ createGame }) => {
    const { player1, player2 } = await createGame({
      player1Name: 'Caster',
      player2Name: 'Opponent',
      player1: {
        hand: ['Boulder Dash'],
        battlefield: [{ name: 'Mountain' }, { name: 'Mountain' }],
      },
      player2: {
        battlefield: [{ name: 'Glory Seeker' }, { name: 'Grizzly Bears' }],
        library: ['Mountain'],
      },
      phase: 'PRECOMBAT_MAIN',
      activePlayer: 1,
    })

    const p1 = player1.gamePage
    const p2 = player2.gamePage

    // Cast Boulder Dash — first target requirement (2 damage)
    await p1.clickCard('Boulder Dash')
    await p1.selectAction('Cast Boulder Dash')
    await expect(p1.page.getByText('Step 1/2')).toBeVisible()

    // Pick Glory Seeker for the 2 damage and confirm
    await p1.selectTarget('Glory Seeker')
    await p1.confirmTargets()
    await expect(p1.page.getByText('Step 2/2')).toBeVisible()

    // Second thoughts — step back. The first step returns with its pick intact.
    await p1.goBackTargetStep()
    await expect(p1.page.getByText('Step 1/2')).toBeVisible()
    await expect(p1.page.getByRole('button', { name: 'Confirm (1)' })).toBeVisible()

    // Revise: the 2 damage should hit Grizzly Bears instead (single-target step,
    // so clicking a new target replaces the previous pick)
    await p1.selectTarget('Grizzly Bears')
    await p1.confirmTargets()
    await expect(p1.page.getByText('Step 2/2')).toBeVisible()

    // 1 damage to Glory Seeker, and submit
    await p1.selectTarget('Glory Seeker')
    await p1.confirmTargets()

    // Opponent lets it resolve
    await p2.resolveStack('Boulder Dash')

    // Grizzly Bears (2/2) took 2 damage and died; Glory Seeker (2/2) took 1 and lives
    await p1.expectNotOnBattlefield('Grizzly Bears')
    await p1.expectOnBattlefield('Glory Seeker')

    await p1.screenshot('End state')
  })
})
