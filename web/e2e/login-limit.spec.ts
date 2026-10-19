import { expect, test } from '@playwright/test'

test('连续五次失败触发共享限流，伪造转发地址不能绕过', async ({ page, request }, testInfo) => {
  const email = `limit-${testInfo.project.name}-${Date.now()}@local.test`
  for (let index = 0; index < 4; index++) {
    const response = await request.post('/api/auth/login', {
      data: { email, password: 'Incorrect123' },
      headers: { 'X-Forwarded-For': `198.51.100.${index + 1}` },
    })
    expect(response.status()).toBe(401)
  }

  await page.goto('/')
  await page.getByLabel('邮箱').fill(email)
  await page.getByLabel('密码').fill('Incorrect123')
  const limitedResponse = page.waitForResponse((response) =>
    response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/auth/login',
  )
  await page.getByRole('button', { name: '登录 FitPilot', exact: true }).click()
  const response = await limitedResponse
  expect(response.status()).toBe(429)
  expect(Number(response.headers()['retry-after'])).toBeGreaterThan(0)
  await expect(page.getByRole('button', { name: /秒后重试/ })).toBeDisabled()
  await expect(page.getByRole('status')).toContainText('秒后重试')
  expect(await page.evaluate(() => localStorage.getItem('fitpilot.accessToken'))).toBeNull()

  const blockedAgain = await request.post('/api/auth/login', {
    data: { email: email.toUpperCase(), password: 'Incorrect123' },
    headers: { 'X-Forwarded-For': '192.0.2.88', Forwarded: 'for=192.0.2.88' },
  })
  expect(blockedAgain.status()).toBe(429)
  const other = await request.post('/api/auth/login', {
    data: { email: `other-${email}`, password: 'Incorrect123' },
  })
  expect(other.status()).toBe(401)
  await page.getByLabel('邮箱').fill(`other-${email}`)
  await expect(page.getByRole('button', { name: '登录 FitPilot', exact: true })).toBeEnabled()
})
