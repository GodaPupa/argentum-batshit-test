import { test, expect } from '../../fixtures/scenarioFixture'

/**
 * One prospective fixed browser case: CR 510.1c permits an ordinary attacker to divide
 * damage without first making a blocker lethal. This uses the real scenario endpoint,
 * two browser players, combat controls and server submission. No store/state injection.
 */
test('ordinary damage can split one and one without making either blocker lethal', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Attacker',
    player2Name: 'Defender',
    player1: {
      battlefield: [{ name: 'Grizzly Bears', tapped: false, summoningSickness: false }],
      library: ['Mountain'],
    },
    player2: {
      battlefield: [{ name: 'Glory Seeker' }, { name: 'Hill Giant' }],
      library: ['Mountain'],
    },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.pass()
  await p1.attackAll()
  await p2.declareBlocker('Glory Seeker', 'Grizzly Bears')
  await p2.declareBlocker('Hill Giant', 'Grizzly Bears')
  await p2.confirmBlockers()

  await expect(player1.page.getByRole('heading', { name: 'Assign Combat Damage', exact: true })).toBeVisible()
  await expect(player1.page.getByRole('button', { name: 'Confirm Order', exact: true })).not.toBeVisible()
  await expect(player1.page.getByRole('button', { name: 'Order Blockers', exact: true })).not.toBeVisible()
  await expect(player1.page.getByText("Banding: you assign this creature's damage", { exact: true })).not.toBeVisible()

  // Same visible row structure the retained GamePage combat helpers use. Restrict to an
  // actual target label and a +/- row; the source card elsewhere on the board is excluded.
  const row = (name: string) => player1.page.locator('div')
    .filter({ has: player1.page.getByText(name, { exact: true }) })
    .filter({ has: player1.page.getByRole('button', { name: '+', exact: true }) })
    .last()
  await expect(row('Glory Seeker').locator('span').last()).toHaveText('2')
  await expect(row('Hill Giant').locator('span').last()).toHaveText('0')
  await p1.decreaseCombatDamage('Glory Seeker', 1)
  await p1.increaseCombatDamage('Hill Giant', 1)
  await expect(row('Glory Seeker').locator('span').last()).toHaveText('1')
  await expect(row('Hill Giant').locator('span').last()).toHaveText('1')
  await p1.screenshot('Legal nonlethal one-and-one allocation')
  await p1.confirmDamage()

  // Both blockers assign their full damage. Neither received lethal from the attacker.
  await p1.expectNotOnBattlefield('Grizzly Bears')
  await p1.expectOnBattlefield('Glory Seeker')
  await p1.expectOnBattlefield('Hill Giant')
  await p1.expectLifeTotal(player2.playerId, 20)
  await expect(player1.page.getByRole('heading', { name: 'Assign Combat Damage', exact: true })).not.toBeVisible()
  await p1.screenshot('Both blockers survive the accepted free division')
})
