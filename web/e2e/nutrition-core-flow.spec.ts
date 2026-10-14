import { expect, test, type APIRequestContext } from '@playwright/test'

const email = process.env.E2E_EMAIL ?? 'fitpilot-e2e@local.test'
const password = process.env.E2E_PASSWORD ?? 'FitPilotE2E123'

type TestUser = {
  accessToken: string
  user: {
    id: number
    goal: 'FAT_LOSS' | 'MAINTAIN' | 'LEAN_BULK'
  }
}

async function prepareTestUser(request: APIRequestContext) {
  const registration = await request.post('/api/auth/register', {
    data: {
      email,
      password,
      nickname: 'E2E Test User',
      gender: 'MALE',
      age: 22,
      heightCm: 183,
      weightKg: 76,
      activityLevel: 'MODERATE',
      goal: 'MAINTAIN',
      weeklyTrainingFrequency: 4,
    },
  })

  // 409 means a previous run already created this isolated test account.
  expect([201, 409]).toContain(registration.status())

  const login = await request.post('/api/auth/login', {
    data: { email, password },
  })
  expect(login.ok()).toBeTruthy()
  return await login.json() as TestUser
}

test('登录后记录食物并切换饮食计划', async ({ page, request }) => {
  const auth = await prepareTestUser(request)
  const authorization = { Authorization: `Bearer ${auth.accessToken}` }
  const currentPlanLabel = auth.user.goal === 'FAT_LOSS'
    ? '减脂'
    : auth.user.goal === 'LEAN_BULK' ? '轻盈增肌' : '维持'
  const targetPlan = auth.user.goal === 'FAT_LOSS'
    ? { goal: 'MAINTAIN', label: '维持' }
    : { goal: 'FAT_LOSS', label: '减脂' }
  let createdFoodLogId: number | undefined

  try {
    await page.goto('/')
    await page.getByLabel('邮箱').fill(email)
    await page.getByLabel('密码').fill(password)
    await page.getByRole('button', { name: '登录 FitPilot' }).click()

    await expect(page.getByRole('heading', { name: `当前饮食计划 · ${currentPlanLabel}` })).toBeVisible()

    await page.getByLabel('食物名称').fill('熟米饭')
    await page.getByRole('option', { name: /熟米饭/ }).click()
    await page.getByLabel('食用克数').fill('137')
    await page.getByLabel('餐次').selectOption('LUNCH')
    await page.getByRole('button', { name: '计算营养' }).click()

    await expect(page.getByText('熟米饭 · 137g')).toBeVisible()
    const createResponsePromise = page.waitForResponse((response) =>
      response.request().method() === 'POST'
      && /\/api\/users\/\d+\/food-logs$/.test(new URL(response.url()).pathname),
    )
    await page.getByRole('button', { name: '加入今日饮食' }).click()
    const createResponse = await createResponsePromise
    expect(createResponse.status()).toBe(201)
    createdFoodLogId = (await createResponse.json() as { id: number }).id

    await expect(page.getByRole('button', { name: '修改熟米饭' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '午餐' })).toBeVisible()

    await page.getByRole('button', { name: '调整饮食计划' }).click()
    await page.getByRole('radio', { name: new RegExp(targetPlan.label) }).check()
    await page.getByRole('button', { name: '保存并重新计算' }).click()

    await expect(page.getByRole('heading', { name: `当前饮食计划 · ${targetPlan.label}` })).toBeVisible()
    await expect(page.getByRole('status')).toContainText(`已切换为${targetPlan.label}`)
  } finally {
    if (createdFoodLogId !== undefined) {
      const deletion = await request.delete(
        `/api/users/${auth.user.id}/food-logs/${createdFoodLogId}`,
        { headers: authorization },
      )
      expect(deletion.status()).toBe(204)
    }

  }
})
