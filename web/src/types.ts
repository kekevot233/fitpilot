export type NutritionValues = {
  calories: number
  protein: number
  carbs: number
  fat: number
}

export type Food = {
  id: number
  name: string
  caloriesPer100g: number
  proteinPer100g: number
  carbsPer100g: number
  fatPer100g: number
}

export type RecentFood = Food & {
  lastGrams: number
  lastMealType: MealType
  lastEatenAt: string
}

export type MealType = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK'

export type FoodCalculation = {
  foodId: number
  foodName: string
  grams: number
  nutrition: NutritionValues
}

export type AiNutritionEstimate = {
  dishName: string
  grams: number
  nutritionPer100g: NutritionValues
  totalNutrition: NutritionValues
  assumptions: string
  confidence: 'LOW' | 'MEDIUM' | 'HIGH'
}

export type FoodLog = {
  id: number
  foodId: number
  foodName: string
  grams: number
  mealType: MealType
  eatenAt: string
  nutrition: NutritionValues
}

export type CopyFoodLogsResponse = {
  sourceDate: string
  targetDate: string
  copiedCount: number
}

export type DailyDashboard = {
  userId: number
  date: string
  target: NutritionValues | null
  consumed: NutritionValues
  remaining: NutritionValues | null
  foodLogs: FoodLog[]
}

export type NutritionTrendPoint = NutritionValues & {
  date: string
  targetCalories: number
  logCount: number
}

export type NutritionTrend = {
  startDate: string
  endDate: string
  daysLogged: number
  averageCalories: number
  averageTargetCalories: number
  points: NutritionTrendPoint[]
}

export type Gender = 'MALE' | 'FEMALE'
export type ActivityLevel = 'SEDENTARY' | 'LIGHT' | 'MODERATE' | 'VERY_ACTIVE' | 'EXTRA_ACTIVE'
export type Goal = 'FAT_LOSS' | 'MAINTAIN' | 'LEAN_BULK'

export type UserProfile = {
  id: number
  email: string
  nickname: string
  gender: Gender
  age: number
  heightCm: number
  weightKg: number
  activityLevel: ActivityLevel
  goal: Goal
  weeklyTrainingFrequency: number
  nutritionTarget: {
    bmr: number
    tdee: number
    targetCalories: number
    protein: number
    carbs: number
    fat: number
    effectiveDate: string
  }
}

export type AuthResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  user: UserProfile
}

export type WeightLog = {
  id: number
  recordedDate: string
  weightKg: number
}

export type WeightTrendPoint = {
  recordedDate: string
  weightKg: number
  sevenDayAverageKg: number
  averageSampleDays: number
}

export type WeightTrend = {
  startDate: string
  endDate: string
  firstWeightKg: number | null
  latestWeightKg: number | null
  changeKg: number | null
  points: WeightTrendPoint[]
}
