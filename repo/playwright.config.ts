import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 10 * 60 * 1000,
  expect: {
    timeout: 15 * 1000
  },
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: process.env.BASE_URL || process.env.APP_BASE_URL || 'http://localhost:8081',
    // Default to headless so the suite runs cleanly in CI containers. Use the
    // npm `e2e:headed` script (which passes --headed) to watch a real browser.
    headless: true,
    video: 'on',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 20 * 1000,
    navigationTimeout: 30 * 1000,
    launchOptions: {
      slowMo: process.env.CI ? 0 : 450
    },
    viewport: { width: 1440, height: 900 }
  },
  outputDir: 'test-results'
})
