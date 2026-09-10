import { test, expect } from '../../../fixtures/scenarioFixture'

/**
 * E2E browser tests for Goblin Taskmaster repeat activation.
 *
 * Card: Goblin Taskmaster ({R}) — Creature — Goblin (1/1)
 * "{1}{R}: Target Goblin creature gets +1/+0 until end of turn."
 * Morph {R}
 *
 * Covers: Repeat activation count selector for self-targeting mana-only abilities.
 * When Goblin Taskmaster is the only Goblin and the player has enough mana,
 * the UI should show a repeat count selector instead of requiring N individual activations.
 */
test('activate ability 3 times with repeat selector', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Player1',
    player2Name: 'Opponent',
    player1: {
      hand: ['Mountain'],
      battlefield: [
        { name: 'Goblin Taskmaster', tapped: false, summoningSickness: false },
        { name: 'Mountain', tapped: false },
        { name: 'Mountain', tapped: false },
        { name: 'Mountain', tapped: false },
        { name: 'Mountain', tapped: false },
        { name: 'Mountain', tapped: false },
        { name: 'Mountain', tapped: false },
      ],
      library: ['Mountain'],
    },
    player2: {
      battlefield: [],
      library: ['Mountain'],
    },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })

  const p1 = player1.gamePage
  const p2 = player2.gamePage

  // Click Goblin Taskmaster and select its activated ability
  await p1.clickCard('Goblin Taskmaster')
  await p1.selectAction('+1/+0')

  // Repeat count selector should appear (reuses X cost selector UI)
  // Select 3 activations
  await p1.selectXValue(3)

  // P2 sees 3 abilities on the stack — resolve each one
  // Stack resolves LIFO, so P2 needs to pass priority for each item
  await p2.resolveStack('Goblin Taskmaster ability')
  await p2.resolveStack('Goblin Taskmaster ability')
  await p2.resolveStack('Goblin Taskmaster ability')

  // After resolution, Goblin Taskmaster should be 4/1 (base 1/1 + 3×(+1/+0))
  await p1.expectStats('Goblin Taskmaster', '4/1')
})
