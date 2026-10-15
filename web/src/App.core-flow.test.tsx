import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
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

function consumedNutrition(): NutritionValues {
  return foodLogs.reduce((total, log) => ({
    calories: total.calories + log.nutrition.calories,
    protein: total.protein + log.nutrition.protein,
    carbs: total.carbs + log.nutrition.carbs,
    fat: total.fat + log.nutrition.fat,
  }), { calories: 0, protein: 0, carbs: 0, fat: 0 })
}

function dashboard(): DailyDashboard {
  const consumed = consumedNutrition()
  const target: NutritionValues = {
    calories: currentUser.nutritionTarget.targetCalories,
    protein: currentUser.nutritionTarget.protein,
    carbs: currentUser.nutritionTarget.carbs,
    fat: currentUser.nutritionTarget.fat,
  }

  return {
    userId: currentUser.id,
    date: today,
    target,
    consumed,
    remaining: {
      calories: target.calories - consumed.calories,
      protein: target.protein - consumed.protein,
      carbs: target.carbs - consumed.carbs,
      fat: target.fat - consumed.fat,
    },
    foodLogs: [...foodLogs],
  }
}

function nutritionTrend(): NutritionTrend {
  const consumed = consumedNutrition()
  const hasLogs = foodLogs.length > 0
  return {
    startDate: today,
    endDate: today,
    daysLogged: hasLogs ? 1 : 0,
    averageCalories: hasLogs ? consumed.calories : 0,
    averageTargetCalories: currentUser.nutritionTarget.targetCalories,
    points: [{
      date: today,
      ...consumed,
      targetCalories: currentUser.nutritionTarget.targetCalories,
      logCount: foodLogs.length,
    }],
  }
}

function weightTrend(): WeightTrend {
  return {
    startDate: today,
    endDate: today,
    firstWeightKg: null,
    latestWeightKg: null,
    changeKg: null,
    points: [],
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

    if (method === 'POST' && url.pathname === '/api/auth/login') {
      return json({
        accessToken: 'test-access-token',
        tokenType: 'Bearer',
        expiresAt: `${today}T23:59:59`,
        user: currentUser,
      })
    }

    if (method === 'GET' && url.pathname === `/api/users/${currentUser.id}/dashboard`) {
      return json(dashboard())
    }

    if (method === 'GET' && url.pathname === '/api/me/nutrition-trend') {
      return json(nutritionTrend())
    }

    if (method === 'GET' && url.pathname === '/api/me/weight-trend') {
      return json(weightTrend())
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
})
