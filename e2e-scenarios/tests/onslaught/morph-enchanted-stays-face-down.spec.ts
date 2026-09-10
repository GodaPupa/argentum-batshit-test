import { test } from '../../fixtures/scenarioFixture'

/**
 * Regression test: a face-down (morph) creature must keep rendering as a card
 * back to its controller after it gains an attachment (an Aura / Equipment).
 *
 * Bug: the controller receives the real card data + isFaceDown=true from the
 * server (so they can peek), and the client is responsible for painting the card
 * back. The battlefield's no-attachment path passed `faceDown` to GameCard, but
 * the with-attachments path dropped it, so enchanting a morph flipped it face-up
 * for its controller. See ClientStateTransformer (controller keeps real data +
 * isFaceDown) and Battlefield.renderWithAttachments.
 *
 * Cards: Towering Baloth ({6}{G}{G}, Morph {6}{G}) cast face-down as a 2/2;
 * Improvised Armor ({3}{W}) — Enchant creature, +2/+5.
 */
test.describe('Morph enchanted stays face-down', () => {
  test('controller still sees a card back after enchanting a face-down creature', async ({
    createGame,
  }) => {
    const { player1, player2 } = await createGame({
      player1Name: 'Morpher',
      player2Name: 'Opponent',
      player1: {
        hand: ['Towering Baloth', 'Improvised Armor'],
        battlefield: [
          { name: 'Plains' },
          { name: 'Plains' },
          { name: 'Plains' },
          { name: 'Plains' },
          { name: 'Plains' },
          { name: 'Plains' },
          { name: 'Plains' },
        ],
        library: ['Plains'],
      },
      player2: {
        library: ['Mountain'],
      },
      phase: 'PRECOMBAT_MAIN',
      activePlayer: 1,
    })

    const p1 = player1.gamePage
    const p2 = player2.gamePage

    // Cast Towering Baloth face-down (costs {3}) — appears as a card back.
    await p1.castFaceDown('Towering Baloth')
    await p1.expectOnBattlefield('Card back')

    // Enchant the face-down creature with Improvised Armor.
    await p1.clickCard('Improvised Armor')
    await p1.selectAction('Cast Improvised Armor')
    await p1.selectTarget('Card back')
    await p1.confirmTargets()

    // P2 resolves the aura on the stack (auras always stop the opponent for priority).
    await p2.resolveStack('Improvised Armor')

    // The aura is attached…
    await p1.expectOnBattlefield('Improvised Armor')
    // …and crucially, the morph is STILL a card back to its controller — its real
    // identity must not leak onto the board just because it gained an attachment.
    await p1.expectOnBattlefield('Card back')
    await p1.expectNotOnBattlefield('Towering Baloth')

    await p1.screenshot('Enchanted morph stays face-down')
  })
})
