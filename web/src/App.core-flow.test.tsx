import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { NutritionTrendPanel } from './components/NutritionTrendPanel'
import type {
  DailyDashboard,
  Food,
  FoodLog,
  NutritionTrend,
  NutritionValues,
  UserProfile,
  WeightTrend,
} from './types'

type RecordedRequest = {
  path: string
  method: string
  authorization: string | null
  body: unknown
}

const rice: Food = {
  id: 11,
  name: '熟米饭',
  caloriesPer100g: 130,
  proteinPer100g: 2.7,
  carbsPer100g: 28.2,
  fatPer100g: 0.3,
}

let today: string
let currentUser: UserProfile
let foodLogs: FoodLog[]
let requests: RecordedRequest[]
let missingHistoricalTarget: boolean
let weightLogs: Map<string, number>

function nutritionTarget(goal: UserProfile['goal']) {
  if (goal === 'FAT_LOSS') {
    return {
      bmr: 1799,
      tdee: 2788,
      targetCalories: 2388,
      protein: 152,
      carbs: 309,
      fat: 68,
      effectiveDate: today,
    }
  }

  return {
    bmr: 1799,
    tdee: 2788,
    targetCalories: 2788,
    protein: 152,
    carbs: 409,
    fat: 68,
    effectiveDate: today,
  }
}

function newUser(goal: UserProfile['goal'] = 'MAINTAIN'): UserProfile {
  return {
    id: 1,
    email: 'zhengyi@example.com',
    nickname: 'Zhengyi',
    gender: 'MALE',
    age: 22,
    heightCm: 183,
    weightKg: 76,
    activityLevel: 'MODERATE',
    goal,
    weeklyTrainingFrequency: 4,
    nutritionTarget: nutritionTarget(goal),
  }
}

function consumedNutrition(date: string): NutritionValues {
  return foodLogs.filter((log) => log.eatenAt.startsWith(`${date}T`)).reduce((total, log) => ({
    calories: total.calories + log.nutrition.calories,
    protein: total.protein + log.nutrition.protein,
    carbs: total.carbs + log.nutrition.carbs,
    fat: total.fat + log.nutrition.fat,
  }), { calories: 0, protein: 0, carbs: 0, fat: 0 })
}

function offsetDate(value: string, days: number) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + days)
  return date.toLocaleDateString('sv-SE')
}

function dashboard(date = today): DailyDashboard {
  const consumed = consumedNutrition(date)
  const target: NutritionValues = {
    calories: currentUser.nutritionTarget.targetCalories,
    protein: currentUser.nutritionTarget.protein,
    carbs: currentUser.nutritionTarget.carbs,
    fat: currentUser.nutritionTarget.fat,
  }

  return {
    userId: currentUser.id,
    date,
    target: missingHistoricalTarget && date < today ? null : target,
    consumed,
    remaining: missingHistoricalTarget && date < today ? null : {
      calories: target.calories - consumed.calories,
      protein: target.protein - consumed.protein,
      carbs: target.carbs - consumed.carbs,
      fat: target.fat - consumed.fat,
    },
    foodLogs: foodLogs.filter((log) => log.eatenAt.startsWith(`${date}T`)),
  }
}

function nutritionTrend(endDate = today): NutritionTrend {
  const startDate = offsetDate(endDate, -6)
  const points = Array.from({ length: 7 }, (_, index) => {
    const date = offsetDate(startDate, index)
    return {
      date,
      ...consumedNutrition(date),
      targetCalories: missingHistoricalTarget && date < today ? 0 : currentUser.nutritionTarget.targetCalories,
      logCount: foodLogs.filter((log) => log.eatenAt.startsWith(`${date}T`)).length,
    }
  })
  const loggedDays = points.filter((point) => point.logCount > 0)
  const withTarget = loggedDays.filter((point) => point.targetCalories > 0)
  return {
    startDate,
    endDate,
    daysLogged: loggedDays.length,
    averageCalories: loggedDays.reduce((sum, point) => sum + point.calories, 0) / (loggedDays.length || 1),
    averageTargetCalories: withTarget.reduce((sum, point) => sum + point.targetCalories, 0) / (withTarget.length || 1),
    points,
  }
}

function weightTrend(endDate = today): WeightTrend {
  const startDate = offsetDate(endDate, -29)
  const points = [...weightLogs.entries()]
    .filter(([date]) => date >= startDate && date <= endDate)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([recordedDate, weightKg]) => ({ recordedDate, weightKg, sevenDayAverageKg: weightKg, averageSampleDays: 1 }))
  return {
    startDate,
    endDate,
    firstWeightKg: points[0]?.weightKg ?? null,
    latestWeightKg: points.at(-1)?.weightKg ?? null,
    changeKg: points.length > 1 ? points.at(-1)!.weightKg - points[0].weightKg : null,
    points,
  }
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function requestBody(init?: RequestInit) {
  return typeof init?.body === 'string' ? JSON.parse(init.body) as unknown : null
}

function installBackendStub() {
  const fetchStub = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const rawUrl = input instanceof Request ? input.url : input.toString()
    const url = new URL(rawUrl, window.location.origin)
    const method = init?.method ?? (input instanceof Request ? input.method : 'GET')
    const body = requestBody(init)
    const authorization = new Headers(init?.headers).get('Authorization')

    requests.push({
      path: `${url.pathname}${url.search}`,
      method,
      authorization,
      body,
    })

    if (method === 'POST' && (url.pathname === '/api/auth/login' || url.pathname === '/api/auth/register')) {
      return json({
        accessToken: 'test-access-token',
        tokenType: 'Bearer',
        expiresAt: `${today}T23:59:59`,
        user: currentUser,
      })
    }

    if (method === 'GET' && url.pathname === `/api/users/${currentUser.id}/dashboard`) {
      const date = url.searchParams.get('date') ?? today
      return json(dashboard(date))
    }

    if (method === 'GET' && url.pathname === '/api/me/nutrition-trend') {
      return json(nutritionTrend(url.searchParams.get('endDate') ?? today))
    }

    if (method === 'GET' && url.pathname === '/api/me/weight-trend') {
      return json(weightTrend(url.searchParams.get('endDate') ?? today))
    }

    if (method === 'PUT' && url.pathname.startsWith('/api/me/weight-logs/')) {
      const recordedDate = url.pathname.split('/').at(-1)!
      const { weightKg } = body as { weightKg: number }
      weightLogs.set(recordedDate, weightKg)
      return json({ id: 1, recordedDate, weightKg })
    }

    if (method === 'PUT' && url.pathname === '/api/me/profile') {
      currentUser = { ...currentUser, ...body as Partial<UserProfile> }
      return json(currentUser)
    }

    if (method === 'GET' && url.pathname === '/api/me/foods/recent') {
      return json(foodLogs.length > 0 ? [{
        ...rice,
        lastGrams: foodLogs[0].grams,
        lastMealType: foodLogs[0].mealType,
        lastEatenAt: foodLogs[0].eatenAt,
      }] : [])
    }

    if (method === 'GET' && url.pathname === '/api/foods') {
      return json(url.searchParams.get('keyword')?.includes('米饭') ? [rice] : [])
    }

    if (method === 'POST' && url.pathname === '/api/foods/calculate') {
      const calculation = body as { foodId: number; grams: number }
      const factor = calculation.grams / 100
      return json({
        foodId: rice.id,
        foodName: rice.name,
        grams: calculation.grams,
        nutrition: {
          calories: rice.caloriesPer100g * factor,
          protein: rice.proteinPer100g * factor,
          carbs: rice.carbsPer100g * factor,
          fat: rice.fatPer100g * factor,
        },
      })
    }

    if (method === 'POST' && url.pathname === `/api/users/${currentUser.id}/food-logs`) {
      const inputBody = body as {
        foodId: number
        grams: number
        mealType: FoodLog['mealType']
        eatenAt: string
      }
      const factor = inputBody.grams / 100
      const created: FoodLog = {
        id: 101,
        foodId: rice.id,
        foodName: rice.name,
        grams: inputBody.grams,
        mealType: inputBody.mealType,
        eatenAt: inputBody.eatenAt,
        nutrition: {
          calories: rice.caloriesPer100g * factor,
          protein: rice.proteinPer100g * factor,
          carbs: rice.carbsPer100g * factor,
          fat: rice.fatPer100g * factor,
        },
      }
      foodLogs = [created]
      return json(created, 201)
    }

    if (method === 'PUT' && url.pathname === '/api/me/nutrition-plan') {
      const inputBody = body as { goal: UserProfile['goal'] }
      currentUser = newUser(inputBody.goal)
      return json(currentUser)
    }

    return json({ detail: `Unhandled test request: ${method} ${url.pathname}${url.search}` }, 500)
  })

  vi.stubGlobal('fetch', fetchStub)
}

describe('FitPilot 核心饮食流程', () => {
  beforeEach(() => {
    today = new Date().toLocaleDateString('sv-SE')
    currentUser = newUser()
    foodLogs = []
    requests = []
    missingHistoricalTarget = false
    weightLogs = new Map()
    window.localStorage.clear()
    installBackendStub()
  })

  it('完成登录、记录食物并切换为减脂计划', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('邮箱'), 'zhengyi@example.com')
    await user.type(screen.getByLabelText('密码'), 'FitPilot123')
    await user.click(screen.getByRole('button', { name: '登录 FitPilot' }))

    expect(await screen.findByRole('heading', { name: '当前饮食计划 · 维持' })).toBeInTheDocument()
    expect(window.localStorage.getItem('fitpilot.accessToken')).toBe('test-access-token')

    const foodSearch = await screen.findByLabelText('食物名称')
    await user.type(foodSearch, '熟米饭')
    await user.click(await screen.findByRole('option', { name: /熟米饭/ }))
    await user.type(screen.getByLabelText('食用克数'), '200')
    await user.selectOptions(screen.getByLabelText('餐次'), 'LUNCH')
    await user.click(screen.getByRole('button', { name: '计算营养' }))

    expect(await screen.findByText('熟米饭 · 200g')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '加入今日饮食' }))

    expect(await screen.findByRole('button', { name: '修改熟米饭' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '午餐' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '调整饮食计划' }))
    await user.click(screen.getByRole('radio', { name: /减脂/ }))
    await user.click(screen.getByRole('button', { name: '保存并重新计算' }))

    expect(await screen.findByRole('heading', { name: '当前饮食计划 · 减脂' })).toBeInTheDocument()
    expect(await screen.findByRole('status')).toHaveTextContent('已切换为减脂')

    const loginRequest = requests.find((request) => request.path === '/api/auth/login')
    expect(loginRequest).toMatchObject({
      method: 'POST',
      authorization: null,
      body: { email: 'zhengyi@example.com', password: 'FitPilot123' },
    })

    const foodLogRequest = requests.find((request) => request.path === '/api/users/1/food-logs')
    expect(foodLogRequest).toMatchObject({
      method: 'POST',
      authorization: 'Bearer test-access-token',
      body: {
        foodId: rice.id,
        grams: 200,
        mealType: 'LUNCH',
      },
    })
    expect((foodLogRequest?.body as { eatenAt: string }).eatenAt).toMatch(new RegExp(`^${today}T`))

    const planRequest = requests.find((request) => request.path === '/api/me/nutrition-plan')
    expect(planRequest).toMatchObject({
      method: 'PUT',
      authorization: 'Bearer test-access-token',
      body: { goal: 'FAT_LOSS' },
    })
  })

  it('从最近记录带入上次份量和餐次', async () => {
    foodLogs = [{
      id: 100,
      foodId: rice.id,
      foodName: rice.name,
      grams: 280,
      mealType: 'DINNER',
      eatenAt: `${today}T18:30:00`,
      nutrition: { calories: 364, protein: 7.56, carbs: 78.96, fat: 0.84 },
    }]
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('邮箱'), 'zhengyi@example.com')
    await user.type(screen.getByLabelText('密码'), 'FitPilot123')
    await user.click(screen.getByRole('button', { name: '登录 FitPilot' }))

    const foodSearch = await screen.findByLabelText('食物名称')
    await user.click(foodSearch)
    expect(await screen.findByText('上次 280g · 晚餐')).toBeInTheDocument()
    await user.click(screen.getByRole('option', { name: /熟米饭/ }))

    expect(screen.getByLabelText('食用克数')).toHaveValue(280)
    expect(screen.getByLabelText('餐次')).toHaveValue('DINNER')
  })

  it('点击七日趋势日期后加载当天记录', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('邮箱'), 'zhengyi@example.com')
    await user.type(screen.getByLabelText('密码'), 'FitPilot123')
    await user.click(screen.getByRole('button', { name: '登录 FitPilot' }))

    const previousDate = offsetDate(today, -1)
    const emptyDays = await screen.findAllByRole('button', { name: /的饮食记录：未记录/ })
    await user.click(emptyDays.at(-2)!)

    expect(screen.getByLabelText('选择饮食记录日期')).toHaveValue(previousDate)
    await waitFor(() => {
      expect(requests).toContainEqual(expect.objectContaining({
        path: `/api/users/1/dashboard?date=${previousDate}`,
        method: 'GET',
        authorization: 'Bearer test-access-token',
      }))
    })
    expect(await screen.findByRole('button', { current: 'date' })).toHaveAccessibleName(/饮食记录/)
  })

  it('历史日期没有营养目标时仍可补录和修改体重', async () => {
    missingHistoricalTarget = true
    const user = userEvent.setup()
    render(<App />)
    await user.type(screen.getByLabelText('邮箱'), 'zhengyi@example.com')
    await user.type(screen.getByLabelText('密码'), 'FitPilot123')
    await user.click(screen.getByRole('button', { name: '登录 FitPilot' }))
    await screen.findByLabelText('体重记录日期')

    const previousDate = offsetDate(today, -1)
    fireEvent.change(screen.getByLabelText('选择饮食记录日期'), { target: { value: previousDate } })
    expect(await screen.findByText(/该日期尚未建立营养目标/)).toBeInTheDocument()
    expect(await screen.findByText('补录历史体重')).toBeInTheDocument()
    expect(screen.getByLabelText('体重记录日期')).toHaveValue(previousDate)
    await user.type(screen.getByLabelText('体重，单位 kg'), '75.2')
    await user.click(screen.getByRole('button', { name: '保存体重' }))
    expect(await screen.findByText('已保存，这一天的记录可以随时修改。')).toBeInTheDocument()
    expect(requests).toContainEqual(expect.objectContaining({
      path: `/api/me/weight-logs/${previousDate}`,
      method: 'PUT',
      authorization: 'Bearer test-access-token',
      body: { weightKg: 75.2 },
    }))

    await user.clear(screen.getByLabelText('体重，单位 kg'))
    await user.type(screen.getByLabelText('体重，单位 kg'), '75.4')
    await user.click(screen.getByRole('button', { name: '更新体重' }))
    await waitFor(() => expect(weightLogs.get(previousDate)).toBe(75.4))
    expect(weightLogs.size).toBe(1)

    fireEvent.change(screen.getByLabelText('体重记录日期'), { target: { value: today } })
    expect(await screen.findByText('记录今天体重')).toBeInTheDocument()
    expect(screen.getByLabelText('选择饮食记录日期')).toHaveValue(previousDate)
    fireEvent.change(screen.getByLabelText('体重记录日期'), { target: { value: previousDate } })
    await waitFor(() => expect(screen.getByLabelText('体重，单位 kg')).toHaveValue(75.4))
    expect(screen.getByLabelText('体重记录日期')).toHaveAttribute('max', today)
  })

  it('注册表单不再要求重复填写训练次数', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByRole('button', { name: '创建账户' }))
    expect(screen.queryByLabelText('每周训练次数')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('昵称'), 'Test')
    await user.type(screen.getByLabelText('邮箱'), 'test@example.com')
    await user.type(screen.getByLabelText('密码'), 'password123')
    await user.selectOptions(screen.getByLabelText('日常活动水平'), 'VERY_ACTIVE')
    await user.click(screen.getByRole('button', { name: '创建账户并计算目标' }))
    await screen.findByRole('heading', { name: '身体资料' })
    const registration = requests.find((request) => request.path === '/api/auth/register')!
    expect(registration.body).toMatchObject({ activityLevel: 'VERY_ACTIVE' })
    expect(registration.body).not.toHaveProperty('weeklyTrainingFrequency')
  })

  it('无目标历史日期可以补记饮食，返回今天后仍显示当前目标', async () => {
    missingHistoricalTarget = true
    const user = userEvent.setup()
    render(<App />)
    await user.type(screen.getByLabelText('邮箱'), 'zhengyi@example.com')
    await user.type(screen.getByLabelText('密码'), 'FitPilot123')
    await user.click(screen.getByRole('button', { name: '登录 FitPilot' }))
    await screen.findByLabelText('体重记录日期')

    const previousDate = offsetDate(today, -1)
    fireEvent.change(screen.getByLabelText('选择饮食记录日期'), { target: { value: previousDate } })
    expect(await screen.findByText(/该日期尚未建立营养目标/)).toBeInTheDocument()
    const progress = within(screen.getByRole('region', { name: '当日营养进度' }))
    expect(progress.getByText('当天无目标')).toBeInTheDocument()
    expect(progress.queryByLabelText(/已完成/)).not.toBeInTheDocument()

    await user.type(await screen.findByLabelText('食物名称'), '熟米饭')
    await user.click(await screen.findByRole('option', { name: /熟米饭/ }))
    await user.type(screen.getByLabelText('食用克数'), '200')
    await user.click(screen.getByRole('button', { name: '计算营养' }))
    await user.click(await screen.findByRole('button', { name: '加入当日饮食' }))
    expect(await screen.findByRole('button', { name: '修改熟米饭' })).toBeInTheDocument()
    await waitFor(() => expect(progress.getByText('260')).toBeInTheDocument())
    expect(progress.queryByText(/剩余/)).not.toBeInTheDocument()
    expect((requests.find((request) => request.path === '/api/users/1/food-logs')?.body as { eatenAt: string }).eatenAt)
      .toMatch(new RegExp(`^${previousDate}T`))
    expect(screen.getByRole('button', { current: 'date' })).toHaveAccessibleName(/当天无目标/)

    fireEvent.change(screen.getByLabelText('选择饮食记录日期'), { target: { value: today } })
    expect(await screen.findByText('目标 2788 kcal')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: '当日营养进度' })).not.toHaveTextContent('260')
  })

  it('有无目标日期混合时只比较有目标日期的摄入差值', () => {
    const trend = nutritionTrend()
    trend.daysLogged = 2
    trend.averageCalories = 200
    trend.averageTargetCalories = 2400
    trend.points[0] = { ...trend.points[0], calories: 100, logCount: 1, targetCalories: 0 }
    trend.points[6] = { ...trend.points[6], calories: 300, logCount: 1, targetCalories: 2400 }
    render(<NutritionTrendPanel trend={trend} selectedDate={today} onSelectDate={() => {}} />)
    const difference = screen.getByText('平均相差').parentElement!
    expect(difference).toHaveTextContent('-2100')
    expect(screen.getByRole('button', { name: /摄入 100 千卡，当天无目标/ })).toBeInTheDocument()
  })

  it('编辑身体资料只提交营养计算所需字段', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.type(screen.getByLabelText('邮箱'), 'zhengyi@example.com')
    await user.type(screen.getByLabelText('密码'), 'FitPilot123')
    await user.click(screen.getByRole('button', { name: '登录 FitPilot' }))
    await user.click(await screen.findByRole('button', { name: '编辑身体资料' }))
    const panel = within(screen.getByRole('region', { name: '身体资料' }))
    expect(panel.queryByLabelText('每周训练频率')).not.toBeInTheDocument()
    expect(panel.queryByText(/每周训练/)).not.toBeInTheDocument()
    await user.selectOptions(panel.getByLabelText('活动水平'), 'VERY_ACTIVE')
    await user.click(panel.getByRole('button', { name: '保存并重新计算' }))
    expect(await screen.findByRole('status')).toHaveTextContent('身体资料已保存')
    expect(requests.find((request) => request.path === '/api/me/profile')?.body).toEqual({
      age: 22, heightCm: 183, weightKg: 76, activityLevel: 'VERY_ACTIVE',
    })
  })
})
