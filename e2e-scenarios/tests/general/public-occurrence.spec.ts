import { test, expect, type Page, type Browser } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const fixtureDir = process.env.MANUAL_UI_FIXTURE_DIR
if (!fixtureDir) throw new Error('MANUAL_UI_FIXTURE_DIR must name the compiled engine fixture output')
const server = process.env.E2E_SERVER_URL ?? 'http://localhost:8080'
const prompt = 'Choose which target to change'
type Meta = { stack: string; old: string; recipient: string; chooser: string; nonowner: string; options: string[]; initialLife: number }
type Wire = { type: string; action?: { type: string; playerId: string; response: { type: string; decisionId: string; optionIndex: number } } }

async function observe(page: Page) {
  return page.evaluate(async () => {
    // Observe the production store only; never inject state or replace transport.
    const modulePath = '/src/store/gameStore.ts'
    const { useGameStore } = await import(modulePath)
    const s = useGameStore.getState()
    return { decision: s.pendingDecision, playerId: s.playerId, game: s.gameState, error: s.lastError }
  })
}

async function openFixture(browser: Browser, count: number) {
  const state = await readFile(path.join(fixtureDir!, `occurrences-${count}.json`), 'utf8')
  const meta: Meta = JSON.parse(await readFile(path.join(fixtureDir!, `occurrences-${count}-meta.json`), 'utf8'))
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
        if (message.action?.type === 'SubmitDecision') wire[i].push(message)
      } catch { /* binary frames carry no decision */ }
    }))
    pages.push(page)
  }
  await Promise.all(pages.map(page => page.goto(process.env.E2E_BASE_URL ?? 'http://localhost:5173')))
  const [other, owner] = pages
  await expect(owner.getByRole('heading', { name: prompt, exact: true })).toBeVisible()
  await expect.poll(async () => (await observe(other)).playerId).toBe(meta.nonowner)
  const initial = await observe(owner)
  expect(initial.playerId, 'UI_OWNER_ID').toBe(meta.chooser)
  expect(initial.decision.playerId, 'UI_DECISION_OWNER').toBe(meta.chooser)
  expect(initial.decision.options, 'UI_DISTINCT_SERVER_LABELS').toEqual(meta.options)
  expect(Object.values(initial.decision.optionCardIds), 'UI_SHARED_PREVIEW_ENTITY').toEqual(Array(count).fill([meta.old]))
  const close = async () => {
    await test.info().attach('decision-wire.json', { body: JSON.stringify(wire, null, 2), contentType: 'application/json' })
    await test.info().attach('owner-observed.json', { body: JSON.stringify(await observe(owner), null, 2), contentType: 'application/json' })
    await test.info().attach('owner-dom.html', { body: await owner.content(), contentType: 'text/html' })
    await test.info().attach('owner-screen.png', { body: await owner.screenshot(), contentType: 'image/png' })
    for (const [index, context] of contexts.entries()) {
      const trace = test.info().outputPath(`seat-${index}-trace.zip`)
      await context.tracing.stop({ path: trace })
      await test.info().attach(`seat-${index}-trace`, { path: trace, contentType: 'application/zip' })
      await context.close()
    }
  }
  return { owner, other, meta, initial, wire, close }
}

function option(page: Page, label: string) {
  return page.getByRole('button', { name: `${label} (1)`, exact: true })
}
async function targets(page: Page, meta: Meta) {
  const s = await observe(page)
  return s.game?.cards[meta.stack]?.targets?.map((t: { entityId: string }) => t.entityId)
}
async function choose(page: Page, label: string) {
  await option(page, label).click()
  await page.getByRole('button', { name: 'Confirm', exact: true }).click()
}

for (const filtered of [false, true]) {
  test(`UI occurrence original index filtered=${filtered}`, async ({ browser }) => {
    const f = await openFixture(browser, 2)
    try {
      await expect(option(f.owner, f.meta.options[0])).toBeVisible()
      await expect(option(f.owner, f.meta.options[1])).toBeVisible()
      await expect(f.owner.locator('[class*=optionCardPreview] img[alt="Hill Giant"]')).toHaveCount(1)
      if (filtered) {
        await f.owner.getByPlaceholder('Search...', { exact: true }).fill('group 2')
        await expect(option(f.owner, f.meta.options[0])).toHaveCount(0)
      }
      await choose(f.owner, f.meta.options[1])
      await expect.poll(() => targets(f.owner, f.meta), { message: 'UI_ORIGINAL_INDEX_TARGET' })
        .toEqual([f.meta.old, f.meta.recipient])
      expect(f.wire[1].length, 'UI_ONE_SUBMISSION').toBe(1)
      expect(f.wire[1][0].action?.playerId).toBe(f.meta.chooser)
      expect(f.wire[1][0].action?.response).toEqual({
        type: 'OptionChosenResponse', decisionId: f.initial.decision.id, optionIndex: 1,
      })
      expect((await observe(f.owner)).decision).toBeNull()
    } finally { await f.close() }
  })
}

test('UI replacement resets filter and selection', async ({ browser }) => {
  const f = await openFixture(browser, 3)
  try {
    await f.owner.getByPlaceholder('Search...', { exact: true }).fill('group 3')
    await choose(f.owner, f.meta.options[2])
    await expect.poll(async () => (await observe(f.owner)).decision?.id).not.toBe(f.initial.decision.id)
    await expect(f.owner.getByPlaceholder('Search...', { exact: true }), 'UI_REPLACEMENT_FILTER_RESET').toHaveValue('')
    await expect(option(f.owner, f.meta.options[0]), 'UI_REPLACEMENT_SELECTION_RESET').toHaveClass(/optionItemSelected/)
    await expect(option(f.owner, f.meta.options[1])).toBeVisible()
    await choose(f.owner, f.meta.options[1])
    await expect.poll(() => targets(f.owner, f.meta)).toEqual([f.meta.old, f.meta.recipient, f.meta.recipient])
    expect(f.wire[1].map(m => m.action?.response.optionIndex)).toEqual([2, 1])
    expect(f.wire[1][1].action?.response.decisionId).not.toBe(f.initial.decision.id)
    const final = await observe(f.owner)
    expect(final.decision).toBeNull()
    expect(final.game.players.find((p: { playerId: string }) => p.playerId === f.meta.chooser).life).toBe(f.meta.initialLife + 4)
  } finally { await f.close() }
})

test('UI held callback cannot answer replacement', async ({ browser }) => {
  const f = await openFixture(browser, 3)
  try {
    await option(f.owner, f.meta.options[0]).click()
    await f.owner.getByRole('button', { name: 'Confirm', exact: true }).evaluate(element => {
      // Hold the actual rendered React callback, not a synthetic store action.
      const key = Object.keys(element).find(k => k.startsWith('__reactProps$'))
      if (!key) throw new Error('Rendered React button callback missing')
      const props = (element as unknown as Record<string, { onClick: () => void }>)[key]
      ;(window as unknown as { heldOccurrenceConfirm: () => void }).heldOccurrenceConfirm = props.onClick
    })
    await f.owner.getByRole('button', { name: 'Confirm', exact: true }).click()
    await expect.poll(async () => (await observe(f.owner)).decision?.id).not.toBe(f.initial.decision.id)
    const second = await observe(f.owner)
    expect(second.decision.options).toHaveLength(2)
    const sentBefore = f.wire[1].length
    await f.owner.evaluate(() => (window as unknown as { heldOccurrenceConfirm: () => void }).heldOccurrenceConfirm())
    // Browser task barrier allows any synchronously sent WebSocket frame to reach Playwright.
    await f.owner.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
    expect(f.wire[1].length, 'UI_STALE_CALLBACK_SENT_NOTHING').toBe(sentBefore)
    expect((await observe(f.owner)).decision.id).toBe(second.decision.id)
    await choose(f.owner, second.decision.options[1])
    await expect.poll(() => targets(f.owner, f.meta)).toEqual([f.meta.recipient, f.meta.old, f.meta.recipient])
    expect(f.wire[1].length).toBe(2)
  } finally { await f.close() }
})

test('UI wrong owner has no choice controls', async ({ browser }) => {
  const f = await openFixture(browser, 2)
  try {
    expect((await observe(f.other)).decision, 'UI_WRONG_OWNER_MASKED').toBeNull()
    await expect(f.other.getByRole('heading', { name: prompt, exact: true })).toHaveCount(0)
    await expect(f.other.getByPlaceholder('Search...', { exact: true })).toHaveCount(0)
    await expect(option(f.other, f.meta.options[0])).toHaveCount(0)
    await expect(option(f.other, f.meta.options[1])).toHaveCount(0)
    expect(f.wire[0]).toEqual([])
    await expect(option(f.owner, f.meta.options[1])).toBeVisible()
  } finally { await f.close() }
})
