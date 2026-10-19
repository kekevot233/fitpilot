import { expect, test } from '@playwright/test'

test('错误密码不会登录或保存 access token', async ({ page }, testInfo) => {
  await page.goto('/')
  await page.getByLabel('邮箱').fill(`missing-${testInfo.project.name}-${Date.now()}@local.test`)
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

test('注册后可在无目标历史日期补记饮食和体重', async ({ page }, testInfo) => {
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
  await expect(page.getByLabel('每周训练次数')).toHaveCount(0)

  const registrationResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/auth/register',
  )
  await page.getByRole('button', { name: '创建账户并计算目标' }).click()
  const registrationResponse = await registrationResponsePromise

  expect(registrationResponse.status()).toBe(201)
  const registration = await registrationResponse.json() as { accessToken: string; user: { id: number; email: string } }
  expect(registration.user.email).toBe(email)
  await expect(page.getByRole('heading', { name: '当前饮食计划 · 轻盈增肌' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('fitpilot.accessToken'))).toBeTruthy()

  const yesterday = await page.evaluate(() => {
    const date = new Date()
    date.setDate(date.getDate() - 1)
    return date.toLocaleDateString('sv-SE')
  })
  await page.getByLabel('选择饮食记录日期').fill(yesterday)
  await expect(page.getByText(/该日期尚未建立营养目标/)).toBeVisible()
  await expect(page.getByLabel('体重记录日期')).toHaveValue(yesterday)
  await page.getByLabel('体重，单位 kg').fill('58.2')
  const weightResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'PUT'
    && new URL(response.url()).pathname === `/api/me/weight-logs/${yesterday}`,
  )
  await page.getByRole('button', { name: '保存体重' }).click()
  expect((await weightResponsePromise).status()).toBe(200)
  await expect(page.getByText('已保存，这一天的记录可以随时修改。')).toBeVisible()
  await expect(page.getByRole('button', { name: '更新体重' })).toBeVisible()

  let foodLogId: number | undefined
  try {
    await page.getByLabel('食物名称').fill('熟米饭')
    await page.getByRole('option', { name: /熟米饭/ }).click()
    await page.getByLabel('食用克数').fill('200')
    await page.getByRole('button', { name: '计算营养' }).click()
    const foodResponsePromise = page.waitForResponse((response) =>
      response.request().method() === 'POST'
      && new URL(response.url()).pathname === `/api/users/${registration.user.id}/food-logs`,
    )
    await page.getByRole('button', { name: '加入当日饮食' }).click()
    const foodResponse = await foodResponsePromise
    const food = await foodResponse.json() as { id: number; eatenAt: string }
    foodLogId = food.id
    expect(foodResponse.status()).toBe(201)
    expect(food.eatenAt).toMatch(new RegExp(`^${yesterday}T`))
    await expect(page.getByRole('button', { name: '修改熟米饭' })).toBeVisible()
    await page.reload()
    await page.getByLabel('选择饮食记录日期').fill(yesterday)
    await expect(page.getByRole('button', { name: '修改熟米饭' })).toBeVisible()
    const progress = page.getByRole('region', { name: '当日营养进度' })
    await expect(progress.getByText('当天无目标', { exact: true })).toBeVisible()
    await expect(progress.locator('.calorie-value')).toContainText('260')
    await expect(progress.getByText('剩余 kcal', { exact: true })).toHaveCount(0)
    await expect(page.locator('button[aria-current="date"]')).toHaveAccessibleName(/当天无目标/)
  } finally {
    if (foodLogId !== undefined) {
      const deletion = await page.request.delete(`/api/users/${registration.user.id}/food-logs/${foodLogId}`, {
        headers: { Authorization: `Bearer ${registration.accessToken}` },
      })
      expect(deletion.status()).toBe(204)
    }
  }

  if (testInfo.project.name === 'mobile-chrome') {
    const layout = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
    }))
    expect(layout.documentWidth).toBeLessThanOrEqual(layout.viewportWidth + 1)
  }

  await page.getByText('体重记录明细（1 条）', { exact: true }).click()
  if (testInfo.project.name === 'mobile-chrome') {
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      await page.evaluate(() => window.innerWidth + 1),
    )
  }
  await page.getByRole('button', { name: `编辑 ${yesterday} 的体重`, exact: true }).click()
  await expect(page.getByLabel('体重，单位 kg')).toHaveValue('58.2')
  await page.getByLabel('体重，单位 kg').fill('58.4')
  const weightUpdatePromise = page.waitForResponse((response) =>
    response.request().method() === 'PUT'
    && new URL(response.url()).pathname === `/api/me/weight-logs/${yesterday}`,
  )
  await page.getByRole('button', { name: '更新体重', exact: true }).click()
  expect((await weightUpdatePromise).status()).toBe(200)
  await expect(page.getByText('已保存，这一天的记录可以随时修改。')).toBeVisible()
  await page.getByRole('button', { name: `删除 ${yesterday} 的体重`, exact: true }).click()
  await page.getByRole('group', { name: '确认删除体重记录' }).getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('button', { name: `删除 ${yesterday} 的体重`, exact: true })).toBeVisible()
  await page.getByRole('button', { name: `删除 ${yesterday} 的体重`, exact: true }).click()
  const weightDeletionPromise = page.waitForResponse((response) =>
    response.request().method() === 'DELETE'
    && new URL(response.url()).pathname === `/api/me/weight-logs/${yesterday}`,
  )
  await page.getByRole('button', { name: '确认删除', exact: true }).click()
  expect((await weightDeletionPromise).status()).toBe(204)
  await expect(page.getByText('还没有体重记录', { exact: true })).toBeVisible()
  await page.reload()
  await page.getByLabel('选择饮食记录日期').fill(yesterday)
  await expect(page.getByText('还没有体重记录', { exact: true })).toBeVisible()
  if (testInfo.project.name === 'mobile-chrome') {
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      await page.evaluate(() => window.innerWidth + 1),
    )
  }
})
