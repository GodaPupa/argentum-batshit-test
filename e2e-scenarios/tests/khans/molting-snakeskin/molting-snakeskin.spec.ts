import { test, expect } from '../../../fixtures/scenarioFixture'

/**
 * E2E browser tests for Molting Snakeskin.
 *
 * Card: Molting Snakeskin ({B}) — Enchantment — Aura
 * Enchant creature
 * Enchanted creature gets +2/+0 and has "{2}{B}: Regenerate this creature."
 *
 * Setup:
 * - P1 controls a Grizzly Bears (2/2) enchanted with Molting Snakeskin (becomes 4/2)
 *   and has 3 untapped Swamps ({2}{B} for regenerate)
 * - P2 controls a Hill Giant (3/3) as blocker
 *
 * Flow:
 * 1. P1 attacks with enchanted Grizzly Bears (4/2)
 * 2. P2 blocks with Hill Giant (3/3) — lethal damage to the 4/2
 * 3. P1 activates "{2}{B}: Regenerate this creature" on Grizzly Bears
 * 4. Combat damage: Hill Giant deals 3 to Bears (lethal), Bears deal 4 to Giant (lethal)
 * 5. Regeneration shield saves Grizzly Bears — it survives tapped
 * 6. Hill Giant dies (no regeneration)
 *
 * Covers: Aura-granted activated ability (regenerate) used during combat
 */
test.describe('Molting Snakeskin', () => {
  test('regenerate ability saves enchanted creature in combat', async ({ createGame }) => {
    const { player1, player2 } = await createGame({
      player1Name: 'Attacker',
      player2Name: 'Defender',
      player1: {
        battlefield: [
          { name: 'Grizzly Bears', tapped: false, summoningSickness: false },
          { name: 'Molting Snakeskin', attachedTo: 'Grizzly Bears' },
          { name: 'Swamp', tapped: false },
          { name: 'Swamp', tapped: false },
          { name: 'Swamp', tapped: false },
        ],
        library: ['Swamp'],
      },
      player2: {
        battlefield: [
          { name: 'Hill Giant' },
        ],
        library: ['Mountain'],
      },
      phase: 'PRECOMBAT_MAIN',
      activePlayer: 1,
      // Stop P1 at declare blockers so they can activate regenerate before damage
      player1StopAtSteps: ['DECLARE_BLOCKERS'],
    })

    const p1 = player1.gamePage
    const p2 = player2.gamePage

    // Grizzly Bears should be 4/2 with Molting Snakeskin (+2/+0)
    await p1.expectStats('Grizzly Bears', '4/2')

    // P1 passes main phase to advance to combat
    await p1.pass()

    // P1 attacks with Grizzly Bears
    await p1.attackWith('Grizzly Bears')

    // P2 blocks with Hill Giant
    await p2.declareBlocker('Hill Giant', 'Grizzly Bears')
    await p2.confirmBlockers()

    // P1 has priority at declare blockers step (stopped via stopAtSteps)
    // P1 activates regenerate on Grizzly Bears: {2}{B}
    await p1.clickCard('Grizzly Bears')
    await p1.selectAction('Regenerate')

    // P2 resolves the regenerate ability
    await p2.pass()

    // P1 passes to advance past declare blockers
    await p1.pass()

    // Combat damage resolves:
    // Hill Giant (3/3) deals 3 damage to Grizzly Bears (4/2) — lethal, but regeneration shield saves it
    // Grizzly Bears (4/2) deals 4 damage to Hill Giant (3/3) — lethal, Hill Giant dies

    // Grizzly Bears should survive (regenerated) and be tapped
    await p1.expectOnBattlefield('Grizzly Bears')
    await p1.expectTapped('Grizzly Bears')

    // Hill Giant should be dead
    await p1.expectNotOnBattlefield('Hill Giant')

    // Molting Snakeskin should still be on Grizzly Bears
    await p1.expectOnBattlefield('Molting Snakeskin')

    // No damage to defending player (attacker was blocked)
    await p1.expectLifeTotal(player2.playerId, 20)

    await p1.screenshot('Grizzly Bears survived via regeneration')
  })
})
