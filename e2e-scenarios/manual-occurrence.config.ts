import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests/general',
  testMatch: 'public-occurrence.spec.ts',
  fullyParallel: false, workers: 1, retries: 0, forbidOnly: true, timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['json', { outputFile: process.env.MANUAL_UI_REPORT! }]],
  outputDir: process.env.MANUAL_UI_RESULTS!,
  use: { baseURL: process.env.E2E_BASE_URL!, trace: 'off', screenshot: 'on', video: 'off' },
  // Servers are started and stopped by the frozen locked qualification runner.
})
