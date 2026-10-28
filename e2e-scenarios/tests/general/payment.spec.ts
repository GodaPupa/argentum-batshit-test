import { test, expect, type Page, type Browser } from '@playwright/test'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
const fixtureDir = process.env.MANUAL_PAYMENT_FIXTURE_DIR
if (!fixtureDir) throw new Error('Compiled payment fixtures required')
const server = process.env.E2E_SERVER_URL ?? 'http://localhost:8080'
type Meta = { source: string; owner: string; other: string; life: number; blue: number; description: string }
type Wire = { type: string; action?: { type: string; playerId: string; paymentStrategy?: { type: string; phyrexianLifePayments?: string[] } } }
async function observe(page: Page) {
  return page.evaluate(async () => {
    const modulePath = '/src/store/gameStore.ts'
    const { useGameStore } = await import(modulePath)
    const s = useGameStore.getState()
    return { playerId: s.playerId, game: s.gameState, error: s.lastError, selection: s.manaSelectionState }
  })
}
async function openFixture(browser: Browser, name: string) {
  const state = await readFile(path.join(fixtureDir!, `${name}.json`), 'utf8')
  const meta: Meta = JSON.parse(await readFile(path.join(fixtureDir!, `${name}-meta.json`), 'utf8'))
  const response = await fetch(`${server}/api/scenarios/from-state?mode=TWO_PLAYER`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: state,
  })
  expect(response.status, 'UI_SNAPSHOT_LOADED').toBe(200)
  const seats = await response.json() as { player1: { token: string; name: string }; player2: { token: string; name: string } }
  const contexts = await Promise.all([browser.newContext(), browser.newContext()])
  await Promise.all(contexts.map(context => context.tracing.start({ screenshots: true, snapshots: true, sources: true })))
  const pages: Page[] = []
  const wire: Wire[][] = [[], []]
  for (const [i, seat] of [seats.player1, seats.player2].entries()) {
    await contexts[i].addInitScript(({ token, name }) => {
      localStorage.setItem('argentum-token', token)
      localStorage.setItem('argentum-player-name', name)
    }, seat)
    const page = await contexts[i].newPage()
    page.on('websocket', socket => socket.on('framesent', frame => {
      try {
        const message = JSON.parse(String(frame.payload)) as Wire
        if (message.action?.type === 'ActivateAbility') wire[i].push(message)
      } catch { /* binary frames carry no decision */ }
    }))
    pages.push(page)
  }
  await Promise.all(pages.map(page => page.goto(process.env.E2E_BASE_URL ?? 'http://localhost:5173')))
  const [owner, other] = pages
  await expect.poll(async () => (await observe(owner)).playerId).toBe(meta.owner)
  await expect.poll(async () => (await observe(other)).playerId).toBe(meta.other)
  await expect(owner.locator(`[data-card-id="${meta.source}"]`)).toBeVisible()
  const initial = await observe(owner)
  const attachBytes = async (name: string, body: string | Buffer, contentType: string) => {
    const file = test.info().outputPath(name)
    await writeFile(file, body)
    await test.info().attach(name, { path: file, contentType })
  }
  const close = async () => {
    await attachBytes('decision-wire.json', JSON.stringify(wire, null, 2), 'application/json')
    await attachBytes('owner-observed.json', JSON.stringify(await observe(owner), null, 2), 'application/json')
    await attachBytes('owner-dom.html', await owner.content(), 'text/html')
    await attachBytes('owner-screen.png', await owner.screenshot(), 'image/png')
    for (const [index, context] of contexts.entries()) {
      const trace = test.info().outputPath(`seat-${index}-trace.zip`)
      await context.tracing.stop({ path: trace })
      await test.info().attach(`seat-${index}-trace`, { path: trace, contentType: 'application/zip' })
      await context.close()
    }
  }
  return { owner, other, meta, initial, wire, close }
}

async function openPayment(f: Awaited<ReturnType<typeof openFixture>>) {
  await f.owner.locator(`[data-card-id="${f.meta.source}"]`).click()
  await f.owner.getByRole('button').filter({ hasText: /gain 1 life/i }).first().click()
  await expect(f.owner.getByText('Pay with life:', { exact: true })).toBeVisible()
}
const manaToggle = (page: Page) => page.getByRole('button', { name: /mana$/ }).filter({ has: page.locator('i, img, svg, span') })
async function life(f: Awaited<ReturnType<typeof openFixture>>) {
  // The toggle's icon can contribute accessible text, so bind by its visible suffix.
  await manaToggle(f.owner).click()
  await expect(f.owner.getByRole('button', { name: /2 life$/ })).toBeVisible()
}
async function pay(f: Awaited<ReturnType<typeof openFixture>>, useLife: boolean, marker: string) {
  await openPayment(f)
  if (useLife) await life(f)
  await f.owner.getByRole('button', { name: 'Confirm', exact: true }).click()
  await expect.poll(() => f.wire[0].length).toBe(1)
  expect(f.wire[0][0].action?.paymentStrategy?.phyrexianLifePayments, marker).toEqual(useLife ? ['BLUE'] : [])
  expect(f.wire[0][0].action?.playerId).toBe(f.meta.owner)
  await expect.poll(async () => (await observe(f.owner)).game.players.find((p: { playerId: string }) => p.playerId === f.meta.owner).life).toBe(f.meta.life - (useLife ? 2 : 0))
  const state = await observe(f.owner)
  expect(state.game.players.find((p: { playerId: string }) => p.playerId === f.meta.owner).manaPool.blue).toBe(f.meta.blue - (useLife ? 0 : 1))
  // Resolve through the two rendered priority controls, never through store injection.
  for (let i = 0; i < 4; i++) {
    const observed = await observe(f.owner)
    if (observed.game.players.find((p: { playerId: string }) => p.playerId === f.meta.owner).life === f.meta.life - (useLife ? 2 : 0) + 1) break
    for (const page of [f.owner, f.other]) {
      const pass = page.getByRole('button', { name: /^Pass(?: |$)|^Resolve/ }).first()
      if (await pass.isVisible() && await pass.isEnabled()) await pass.click()
    }
  }
  for (const page of [f.owner, f.other]) await expect.poll(async () => (await observe(page)).game.players.find((p: { playerId: string }) => p.playerId === f.meta.owner).life).toBe(f.meta.life - (useLife ? 2 : 0) + 1)
  expect(f.wire[0]).toHaveLength(1); expect(f.wire[1]).toEqual([])
}
for (const [name, fixture, useLife, marker] of [
  ['UI payment life without blue', 'life-only', true, 'PAYMENT_BLUE_COLOR'],
  ['UI payment life preserves blue', 'blue-or-life', true, 'PAYMENT_LIFE_CHOICE'],
  ['UI payment blue mana', 'blue-or-life', false, 'PAYMENT_MANA_CHOICE'],
] as const) test(name, async ({ browser }) => {
  const f = await openFixture(browser, fixture)
  try { await pay(f, useLife, marker) } finally { await f.close() }
})
test('UI payment cancel control', async ({ browser }) => {
  const f = await openFixture(browser, 'blue-or-life')
  try {
    await openPayment(f); await life(f)
    await f.owner.getByRole('button', { name: 'Cancel', exact: true }).click()
    await expect(f.owner.getByText('Pay with life:', { exact: true })).toHaveCount(0)
    expect(f.wire).toEqual([[], []]); expect((await observe(f.owner)).game).toEqual(f.initial.game)
  } finally { await f.close() }
})
test('UI payment insufficient life toggle disabled', async ({ browser }) => {
  const f = await openFixture(browser, 'insufficient-life')
  try { await openPayment(f); await expect(manaToggle(f.owner)).toBeDisabled(); expect(f.wire).toEqual([[], []]) } finally { await f.close() }
})
test('UI payment unaffordable activation disabled', async ({ browser }) => {
  const f = await openFixture(browser, 'unaffordable')
  try {
    await f.owner.locator(`[data-card-id="${f.meta.source}"]`).click()
    await expect(f.owner.getByRole('button').filter({ hasText: /gain 1 life/i }).first()).toBeDisabled()
    expect(f.wire).toEqual([[], []])
  } finally { await f.close() }
})
test('UI payment wrong seat cannot activate', async ({ browser }) => {
  const f = await openFixture(browser, 'blue-or-life')
  try {
    await f.other.locator(`[data-card-id="${f.meta.source}"]`).click()
    await expect(f.other.getByText('Pay with life:', { exact: true })).toHaveCount(0)
    expect(f.wire).toEqual([[], []])
  } finally { await f.close() }
})
test('UI payment stale confirmation ignored', async ({ browser }) => {
  const f = await openFixture(browser, 'blue-or-life')
  try {
    await openPayment(f)
    await f.owner.getByRole('button', { name: 'Confirm', exact: true }).evaluate(element => {
      const key = Object.keys(element).find(k => k.startsWith('__reactProps$'))!
      const props = (element as unknown as Record<string, { onClick: () => void }>)[key]
      ;(window as unknown as { heldPaymentConfirm: () => void }).heldPaymentConfirm = props.onClick
    })
    await life(f)
    await f.owner.evaluate(() => (window as unknown as { heldPaymentConfirm: () => void }).heldPaymentConfirm())
    await f.owner.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
    expect(f.wire[0].length, 'PAYMENT_STALE_SENT_NOTHING').toBe(0)
    await expect(f.owner.getByRole('button', { name: /2 life$/ })).toBeVisible()
  } finally { await f.close() }
})
