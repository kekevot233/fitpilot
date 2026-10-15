import { expect, test } from '@playwright/test'

test('错误密码不会登录或保存 access token', async ({ page }, testInfo) => {
  await page.goto('/')
  await page.getByLabel('邮箱').fill(`missing-${testInfo.project.name}@local.test`)
  await page.getByLabel('密码').fill('WrongPassword123')

  const loginResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/auth/login',
  )
  await page.getByRole('button', { name: '登录 FitPilot' }).click()
  const loginResponse = await loginResponsePromise

  expect(loginResponse.status()).toBe(401)
  await expect(page.locator('.form-error')).toHaveText('Email or password is incorrect.')
  await expect(page.getByRole('button', { name: '登录 FitPilot' })).toBeEnabled()
  expect(await page.evaluate(() => localStorage.getItem('fitpilot.accessToken'))).toBeNull()
})

test('从注册表单创建账户并生成营养目标', async ({ page }, testInfo) => {
  const uniquePart = `${testInfo.project.name}-${Date.now()}`
  const email = `fitpilot-register-${uniquePart}@local.test`

  await page.goto('/')
  await page.getByRole('button', { name: '创建账户', exact: true }).click()
  await page.getByLabel('昵称').fill('注册流程测试')
  await page.getByLabel('性别').selectOption('FEMALE')
  await page.getByLabel('邮箱').fill(email)
  await page.getByLabel('密码').fill('FitPilotRegister123')
  await page.getByLabel('年龄').fill('24')
  await page.getByLabel('身高 cm').fill('165')
  await page.getByLabel('体重 kg').fill('58')
  await page.getByLabel('日常活动水平').selectOption('LIGHT')
  await page.getByLabel('目标').selectOption('LEAN_BULK')
  await page.getByLabel('每周训练次数').fill('3')

  const registrationResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/auth/register',
  )
  await page.getByRole('button', { name: '创建账户并计算目标' }).click()
  const registrationResponse = await registrationResponsePromise

  expect(registrationResponse.status()).toBe(201)
  const registration = await registrationResponse.json() as { user: { email: string } }
  expect(registration.user.email).toBe(email)
  await expect(page.getByRole('heading', { name: '当前饮食计划 · 轻盈增肌' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('fitpilot.accessToken'))).toBeTruthy()

  if (testInfo.project.name === 'mobile-chrome') {
    const layout = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
    }))
    expect(layout.documentWidth).toBeLessThanOrEqual(layout.viewportWidth + 1)
  }
})