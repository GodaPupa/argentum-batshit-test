import { defineConfig } from '@playwright/test'
import base from './playwright.config'

// A bounded, standalone browser gate. Do not inherit nightly retries or attach to an
// unrelated already-running server. The workflow records the exact checkout and binaries.
export default defineConfig({
  ...base,
  testMatch: [
    '**/general/trample-damage-assignment.spec.ts',
    '**/ferocity-recycling/combat-current-rules.spec.ts',
  ],
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  timeout: 60_000,
  // Includes cold server compilation/startup. Four test bodies themselves are each capped above.
  globalTimeout: 45 * 60_000,
  outputDir: '../ferocity-recycling/evidence/browser/combat/test-results',
  reporter: [
    ['list'],
    ['json', { outputFile: '../ferocity-recycling/evidence/browser/combat/results.json' }],
    ['junit', { outputFile: '../ferocity-recycling/evidence/browser/combat/results.xml' }],
    ['html', { outputFolder: '../ferocity-recycling/evidence/browser/combat/html', open: 'never' }],
  ],
  use: {
    ...base.use,
    baseURL: 'http://localhost:5173',
    browserName: 'chromium',
    trace: 'on',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  webServer: [
    {
      command: 'cd .. && just --working-directory . --justfile ferocity-recycling/combat-browser.just server',
      url: 'http://localhost:8080/api/dev/scenarios/cards',
      env: { GAME_DEV_ENDPOINTS_ENABLED: 'true' },
      reuseExistingServer: false,
      timeout: 35 * 60_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
    {
      command: 'cd ../web-client && npm run dev -- --host 127.0.0.1 --port 5173 --strictPort',
      url: 'http://localhost:5173',
      reuseExistingServer: false,
      timeout: 30_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
  ],
})
