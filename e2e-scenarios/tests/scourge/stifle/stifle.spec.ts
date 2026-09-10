import { test } from '../../../fixtures/scenarioFixture'

/**
 * E2E browser test for Stifle.
 *
 * Card: Stifle ({U}) — Instant
 * "Counter target activated or triggered ability. (Mana abilities can't be targeted.)"
 *
 * Scenario:
 * - P1 has Sparksmith + Goblin Sky Raider on the battlefield
 * - P2 has Stifle in hand, Island untapped, and Glory Seeker on the battlefield
 * - P1 activates Sparksmith targeting Glory Seeker (ability goes on stack)
 * - P2 responds with Stifle targeting Sparksmith's ability on the stack
 * - Stifle resolves → Sparksmith's ability is countered
 * - Glory Seeker survives, P1 takes no self-damage from Sparksmith
 */
test.describe('Stifle', () => {
  test('counter activated ability on the stack', async ({ createGame }) => {
    const { player1, player2 } = await createGame({
      player1Name: 'Goblin Player',
      player2Name: 'Defender',
      player1: {
        battlefield: [
          { name: 'Sparksmith', tapped: false, summoningSickness: false },
          { name: 'Goblin Sky Raider', tapped: false, summoningSickness: false },
        ],
        library: ['Mountain'],
      },
      player2: {
        hand: ['Stifle'],
        battlefield: [
          { name: 'Glory Seeker', tapped: false, summoningSickness: false },
          { name: 'Island', tapped: false },
        ],
        library: ['Island'],
      },
      phase: 'PRECOMBAT_MAIN',
      activePlayer: 1,
    })

    const p1 = player1.gamePage
    const p2 = player2.gamePage

    // P1 activates Sparksmith's ability targeting Glory Seeker
    // With 2 Goblins, Sparksmith deals 2 damage to target and 2 to P1
    await p1.clickCard('Sparksmith')
    await p1.selectAction('damage to target')
    await p1.selectTarget('Glory Seeker')
    await p1.confirmTargets()

    // P1 auto-passes, P2 gets priority with Sparksmith's ability on the stack
    // P2 responds with Stifle targeting Sparksmith's ability
    await p2.clickCard('Stifle')
    await p2.selectAction('Cast Stifle')
    await p2.selectTarget('Sparksmith ability')
    await p2.confirmTargets()

    // Stifle resolves (P1 has no response) → counters Sparksmith's ability
    // Sparksmith's ability is removed from the stack and never resolves
    await p1.resolveStack('Stifle')

    // Verify: Glory Seeker survived (ability was countered)
    await p1.expectOnBattlefield('Glory Seeker')

    // Verify: P1 took no self-damage (Sparksmith's ability never resolved)
    await p1.expectLifeTotal(player1.playerId, 20)

    // Verify: Sparksmith is tapped (cost was paid) but ability did nothing
    await p1.expectTapped('Sparksmith')

    // Verify: Stifle is in P2's graveyard
    await p2.expectNotInHand('Stifle')
  })
})
