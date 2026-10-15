import type {
  DailyDashboard,
  Food,
  RecentFood,
  FoodCalculation,
  FoodLog,
  MealType,
  AuthResponse,
  UserProfile,
  ActivityLevel,
  Gender,
  Goal,
  WeightLog,
  WeightTrend,
  AiNutritionEstimate,
  NutritionTrend,
  CopyFoodLogsResponse,
} from './types'

// By default the browser calls the same origin. During local Vite development
// /api is proxied to Spring Boot; in Docker, Nginx performs the same job.
// VITE_API_BASE_URL remains available when the API is intentionally hosted on
// another origin.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const TOKEN_STORAGE_KEY = 'fitpilot.accessToken'

export function getAccessToken() {
  return window.localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function saveAccessToken(token: string) {
  window.localStorage.setItem(TOKEN_STORAGE_KEY, token)
}

export function clearAccessToken() {
  window.localStorage.removeItem(TOKEN_STORAGE_KEY)
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getAccessToken()
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  })

  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    const message = problem?.detail ?? `请求失败（${response.status}）`
    throw new Error(message)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export function login(email: string, password: string) {
  return request<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function register(input: {
  email: string
  password: string
  nickname: string
  gender: Gender
  age: number
  heightCm: number
  weightKg: number
  activityLevel: ActivityLevel
  goal: Goal
  weeklyTrainingFrequency: number
}) {
  return request<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function getCurrentUser() {
  return request<UserProfile>('/api/auth/me')
}

export function updateNutritionPlan(goal: Goal) {
  return request<UserProfile>('/api/me/nutrition-plan', {
    method: 'PUT',
    body: JSON.stringify({ goal }),
  })
}

export function updateProfile(input: {
  age: number
  heightCm: number
  weightKg: number
  activityLevel: ActivityLevel
  weeklyTrainingFrequency: number
}) {
  return request<UserProfile>('/api/me/profile', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function getDashboard(userId: number, date: string) {
  return request<DailyDashboard>(`/api/users/${userId}/dashboard?date=${date}`)
}

export function getNutritionTrend(endDate: string, days = 7) {
  return request<NutritionTrend>(
    `/api/me/nutrition-trend?endDate=${endDate}&days=${days}`,
  )
}

export function upsertWeightLog(recordedDate: string, weightKg: number) {
  return request<WeightLog>(`/api/me/weight-logs/${recordedDate}`, {
    method: 'PUT',
    body: JSON.stringify({ weightKg }),
  })
}

export function getWeightTrend(endDate: string, days = 30) {
  return request<WeightTrend>(
    `/api/me/weight-trend?endDate=${endDate}&days=${days}`,
  )
}

export function searchFoods(keyword: string) {
  const query = keyword.trim()
    ? `?keyword=${encodeURIComponent(keyword.trim())}`
    : ''
  return request<Food[]>(`/api/foods${query}`)
}

export function getRecentFoods(limit = 6) {
  return request<RecentFood[]>(`/api/me/foods/recent?limit=${limit}`)
}

export function createFood(input: {
  name: string
  caloriesPer100g: number
  proteinPer100g: number
  carbsPer100g: number
  fatPer100g: number
}) {
  return request<Food>('/api/foods', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function getCustomFoods() {
  return request<Food[]>('/api/me/foods/custom')
}

export function updateCustomFood(foodId: number, input: {
  name: string
  caloriesPer100g: number
  proteinPer100g: number
  carbsPer100g: number
  fatPer100g: number
}) {
  return request<Food>(`/api/me/foods/custom/${foodId}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function archiveCustomFood(foodId: number) {
  return request<void>(`/api/me/foods/custom/${foodId}`, {
    method: 'DELETE',
  })
}

export function calculateFood(foodId: number, grams: number) {
  return request<FoodCalculation>('/api/foods/calculate', {
    method: 'POST',
    body: JSON.stringify({ foodId, grams }),
  })
}

export function estimateFoodNutrition(dishName: string, grams: number) {
  return request<AiNutritionEstimate>('/api/me/ai-nutrition/estimate', {
    method: 'POST',
    body: JSON.stringify({ dishName, grams }),
  })
}

export function createFoodLog(
  userId: number,
  input: { foodId: number; grams: number; mealType: MealType; eatenAt?: string },
) {
  return request<FoodLog>(`/api/users/${userId}/food-logs`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateFoodLog(
  userId: number,
  foodLogId: number,
  input: { foodId: number; grams: number; mealType: MealType; eatenAt: string },
) {
  return request<FoodLog>(`/api/users/${userId}/food-logs/${foodLogId}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function deleteFoodLog(userId: number, foodLogId: number) {
  return request<void>(`/api/users/${userId}/food-logs/${foodLogId}`, {
    method: 'DELETE',
  })
}

export function copyFoodLogs(sourceDate: string, targetDate: string) {
  return request<CopyFoodLogsResponse>('/api/me/food-logs/copy-day', {
    method: 'POST',
    body: JSON.stringify({ sourceDate, targetDate }),
  })
}
