import { useEffect, useState, type FocusEvent, type FormEvent } from 'react'
import {
  calculateFood,
  createFood,
  createFoodLog,
  estimateFoodNutrition,
  getRecentFoods,
  searchFoods,
} from '../api'
import type { AiNutritionEstimate, Food, FoodCalculation, MealType, RecentFood } from '../types'

type Props = {
  userId: number
  date: string
  onSaved: () => Promise<void>
}

const mealLabels: Record<MealType, string> = {
  BREAKFAST: '早餐',
  LUNCH: '午餐',
  DINNER: '晚餐',
  SNACK: '加餐',
}

const confidenceLabels: Record<AiNutritionEstimate['confidence'], string> = {
  LOW: '参考性较低',
  MEDIUM: '中等可信度',
  HIGH: '较高可信度',
}

type FoodOption = Food | RecentFood

function isRecentFood(food: FoodOption): food is RecentFood {
  return 'lastGrams' in food
}

function eatenAtFor(date: string) {
  const now = new Date()
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  return `${date}T${hours}:${minutes}:00`
}

export function FoodEntryForm({ userId, date, onSaved }: Props) {
  const [keyword, setKeyword] = useState('')
  const [foods, setFoods] = useState<FoodOption[]>([])
  const [selectedFood, setSelectedFood] = useState<Food | null>(null)
  const [grams, setGrams] = useState('')
  const [mealType, setMealType] = useState<MealType>('LUNCH')
  const [preview, setPreview] = useState<FoodCalculation | null>(null)
  const [aiEstimate, setAiEstimate] = useState<AiNutritionEstimate | null>(null)
  const [showCustomFood, setShowCustomFood] = useState(false)
  const [customNutrition, setCustomNutrition] = useState({
    calories: '', protein: '', carbs: '', fat: '',
  })
  const [loadingFoods, setLoadingFoods] = useState(true)
  const [foodListVersion, setFoodListVersion] = useState(0)
  const [isFoodMenuOpen, setIsFoodMenuOpen] = useState(false)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    const timer = window.setTimeout(() => {
      setLoadingFoods(true)
      const request = keyword.trim() ? searchFoods(keyword) : getRecentFoods()
      request
        .then((result) => {
          if (active) setFoods(result)
        })
        .catch((requestError: Error) => {
          if (active) setError(requestError.message)
        })
        .finally(() => {
          if (active) setLoadingFoods(false)
        })
    }, 250)

    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [keyword, foodListVersion])

  function chooseFood(food: FoodOption) {
    setSelectedFood(food)
    setKeyword(food.name)
    if (isRecentFood(food)) {
      setGrams(String(food.lastGrams))
      setMealType(food.lastMealType)
    }
    setPreview(null)
    setAiEstimate(null)
    setError('')
    setShowCustomFood(false)
    setIsFoodMenuOpen(false)
  }

  function closeFoodMenuWhenFocusLeaves(event: FocusEvent<HTMLDivElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      setIsFoodMenuOpen(false)
    }
  }

  async function handleCreateCustomFood() {
    const values = Object.values(customNutrition).map(Number)
    if (!keyword.trim() || values.some((value) => !Number.isFinite(value) || value < 0)) {
      setError('请填写食物名称以及每 100g 的完整营养数据。')
      return
    }

    setWorking(true)
    setError('')
    try {
      const food = await createFood({
        name: keyword.trim(),
        caloriesPer100g: Number(customNutrition.calories),
        proteinPer100g: Number(customNutrition.protein),
        carbsPer100g: Number(customNutrition.carbs),
        fatPer100g: Number(customNutrition.fat),
      })
      setFoods((current) => [food, ...current])
      setCustomNutrition({ calories: '', protein: '', carbs: '', fat: '' })
      chooseFood(food)
    } catch (requestError) {
      setError((requestError as Error).message)
    } finally {
      setWorking(false)
    }
  }

  async function handleCalculate(event: FormEvent) {
    event.preventDefault()
    const parsedGrams = Number(grams)
    if (!selectedFood || !Number.isFinite(parsedGrams) || parsedGrams <= 0) {
      setError('请选择食物，并输入大于 0 的克数。')
      return
    }

    setWorking(true)
    setError('')
    try {
      setPreview(await calculateFood(selectedFood.id, parsedGrams))
    } catch (requestError) {
      setError((requestError as Error).message)
    } finally {
      setWorking(false)
    }
  }

  async function handleAiEstimate() {
    const parsedGrams = Number(grams)
    if (!keyword.trim() || !Number.isFinite(parsedGrams) || parsedGrams <= 0) {
      setError('请先输入菜品名称和大于 0 的克数。')
      return
    }

    setWorking(true)
    setError('')
    setPreview(null)
    setShowCustomFood(false)
    setIsFoodMenuOpen(false)
    try {
      setAiEstimate(await estimateFoodNutrition(keyword.trim(), parsedGrams))
    } catch (requestError) {
      setError((requestError as Error).message)
    } finally {
      setWorking(false)
    }
  }

  function handleEditAiEstimate() {
    if (!aiEstimate) return
    setCustomNutrition({
      calories: String(aiEstimate.nutritionPer100g.calories),
      protein: String(aiEstimate.nutritionPer100g.protein),
      carbs: String(aiEstimate.nutritionPer100g.carbs),
      fat: String(aiEstimate.nutritionPer100g.fat),
    })
    setAiEstimate(null)
    setShowCustomFood(true)
  }

  async function handleSaveAiEstimate() {
    if (!aiEstimate) return
    setWorking(true)
    setError('')
    let createdFood: Food | null = null
    try {
      createdFood = await createFood({
        name: aiEstimate.dishName,
        caloriesPer100g: aiEstimate.nutritionPer100g.calories,
        proteinPer100g: aiEstimate.nutritionPer100g.protein,
        carbsPer100g: aiEstimate.nutritionPer100g.carbs,
        fatPer100g: aiEstimate.nutritionPer100g.fat,
      })
      await createFoodLog(userId, {
        foodId: createdFood.id,
        grams: aiEstimate.grams,
        mealType,
        eatenAt: eatenAtFor(date),
      })
      setAiEstimate(null)
      setSelectedFood(null)
      setKeyword('')
      setGrams('')
      await onSaved()
      setFoodListVersion((version) => version + 1)
    } catch (requestError) {
      if (createdFood) {
        setSelectedFood(createdFood)
        setAiEstimate(null)
        setPreview({
          foodId: createdFood.id,
          foodName: createdFood.name,
          grams: aiEstimate.grams,
          nutrition: aiEstimate.totalNutrition,
        })
        setError(`食物已创建，但饮食记录保存失败：${(requestError as Error).message}。请再次点击“加入今日饮食”。`)
      } else {
        setError((requestError as Error).message)
      }
    } finally {
      setWorking(false)
    }
  }

  async function handleSave() {
    if (!preview) return
    setWorking(true)
    setError('')
    try {
      await createFoodLog(userId, {
        foodId: preview.foodId,
        grams: preview.grams,
        mealType,
        eatenAt: eatenAtFor(date),
      })
      setPreview(null)
      setSelectedFood(null)
      setKeyword('')
      setGrams('')
      await onSaved()
      setFoodListVersion((version) => version + 1)
    } catch (requestError) {
      setError((requestError as Error).message)
    } finally {
      setWorking(false)
    }
  }

  return (
    <section className="panel entry-panel">
      <div className="section-heading">
        <div>
          <p className="eyebrow">快速记录</p>
          <h2>{date === new Date().toLocaleDateString('sv-SE') ? '今天' : '这天'}吃了什么？</h2>
        </div>
        <span className="step-badge">食物库 + AI 估算</span>
      </div>

      <form onSubmit={handleCalculate}>
        <label className="field-label" htmlFor="food-search">食物名称</label>
        <div className="food-search-control" onBlur={closeFoodMenuWhenFocusLeaves}>
          <div className="search-wrap">
            <span className="search-icon">⌕</span>
            <input
              id="food-search"
              value={keyword}
              onFocus={() => setIsFoodMenuOpen(true)}
              onClick={() => setIsFoodMenuOpen(true)}
              onChange={(event) => {
                setKeyword(event.target.value)
                setSelectedFood(null)
                setPreview(null)
                setAiEstimate(null)
                setShowCustomFood(false)
                setIsFoodMenuOpen(true)
              }}
              placeholder="搜索鱼香肉丝、鸡腿饭、包子…"
              autoComplete="off"
              role="combobox"
              aria-expanded={isFoodMenuOpen && !selectedFood}
              aria-controls="food-options"
            />
            <button
              className="search-toggle"
              type="button"
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => setIsFoodMenuOpen((open) => !open)}
              aria-label={isFoodMenuOpen ? '收起食物列表' : '展开食物列表'}
            >
              {isFoodMenuOpen ? '⌃' : '⌄'}
            </button>
          </div>

          {!selectedFood && isFoodMenuOpen && (
            <div className="food-options" id="food-options" role="listbox">
            {loadingFoods ? (
              <p className="muted">正在查找食物…</p>
            ) : foods.length > 0 ? (
              <>
                {!keyword.trim() && <p className="food-options-title">最近记录 · 点击带入上次份量</p>}
                {foods.slice(0, 6).map((food) => (
                  <button type="button" role="option" key={food.id} onClick={() => chooseFood(food)}>
                    <span>{food.name}</span>
                    <small>
                      {isRecentFood(food)
                        ? `上次 ${food.lastGrams}g · ${mealLabels[food.lastMealType]}`
                        : `${food.caloriesPer100g} kcal / 100g`}
                    </small>
                  </button>
                ))}
              </>
            ) : (
              <div className="no-food-result">
                {keyword.trim() ? (
                  <>
                    <p className="muted">没有找到“{keyword}”</p>
                    <button type="button" onClick={() => setShowCustomFood(true)}>
                      ＋ 创建自定义食物
                    </button>
                  </>
                ) : (
                  <p className="muted">还没有记录过食物，输入名称即可搜索食物库。</p>
                )}
              </div>
            )}
            </div>
          )}
        </div>

        {showCustomFood && !selectedFood && (
          <div className="custom-food-form">
            <div className="custom-form-title">
              <div>
                <strong>新建“{keyword}”</strong>
                <small>请按照包装标签填写每 100g 数据 · 仅自己可见</small>
              </div>
              <button type="button" onClick={() => setShowCustomFood(false)}>×</button>
            </div>
            <div className="custom-nutrition-grid">
              {([
                ['calories', '热量', 'kcal'],
                ['protein', '蛋白质', 'g'],
                ['carbs', '碳水', 'g'],
                ['fat', '脂肪', 'g'],
              ] as const).map(([key, label, unit]) => (
                <label key={key}>
                  <span>{label}</span>
                  <div className="input-with-unit">
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={customNutrition[key]}
                      onChange={(event) => setCustomNutrition({
                        ...customNutrition,
                        [key]: event.target.value,
                      })}
                    />
                    <span>{unit}</span>
                  </div>
                </label>
              ))}
            </div>
            <button
              className="secondary-button"
              disabled={working}
              type="button"
              onClick={handleCreateCustomFood}
            >
              {working ? '创建中…' : '创建并选择'}
            </button>
          </div>
        )}

        {selectedFood && (
          <div className="selected-food">
            <span className="food-avatar">{selectedFood.name.slice(0, 1)}</span>
            <div>
              <strong>{selectedFood.name}</strong>
              <small>{selectedFood.caloriesPer100g} kcal / 100g</small>
            </div>
            <button type="button" onClick={() => {
              setSelectedFood(null)
              setIsFoodMenuOpen(true)
            }}>更换</button>
          </div>
        )}

        <div className="form-row">
          <div>
            <label className="field-label" htmlFor="grams">食用克数</label>
            <div className="input-with-unit">
              <input
                id="grams"
                type="number"
                min="0.1"
                max="5000"
                step="0.1"
                value={grams}
                onChange={(event) => {
                  setGrams(event.target.value)
                  setPreview(null)
                  setAiEstimate(null)
                }}
                placeholder="例如 350"
              />
              <span>g</span>
            </div>
          </div>
          <div>
            <label className="field-label" htmlFor="meal-type">餐次</label>
            <select
              id="meal-type"
              value={mealType}
              onChange={(event) => setMealType(event.target.value as MealType)}
            >
              {Object.entries(mealLabels).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
        </div>

        {!selectedFood && keyword.trim() && (
          <button
            className="ai-estimate-button"
            disabled={working || !grams}
            type="button"
            onClick={handleAiEstimate}
          >
            <span>✦</span>
            {working ? 'AI 正在估算…' : '用 AI 按菜名和克数估算'}
          </button>
        )}

        {error && <p className="form-error">{error}</p>}

        {!preview && !aiEstimate && selectedFood && (
          <button className="primary-button" disabled={working} type="submit">
            {working ? '计算中…' : '计算营养'}
          </button>
        )}
      </form>

      {aiEstimate && (
        <div className="preview-card ai-preview-card">
          <div className="preview-title">
            <div>
              <span>AI 营养估算 · {confidenceLabels[aiEstimate.confidence]}</span>
              <strong>{aiEstimate.dishName} · {aiEstimate.grams}g</strong>
            </div>
            <b>{aiEstimate.totalNutrition.calories.toFixed(0)} <small>kcal</small></b>
          </div>
          <div className="preview-macros">
            <span>蛋白质 <strong>{aiEstimate.totalNutrition.protein.toFixed(1)}g</strong></span>
            <span>碳水 <strong>{aiEstimate.totalNutrition.carbs.toFixed(1)}g</strong></span>
            <span>脂肪 <strong>{aiEstimate.totalNutrition.fat.toFixed(1)}g</strong></span>
          </div>
          <p className="ai-assumptions">估算依据：{aiEstimate.assumptions}</p>
          <p className="ai-estimate-notice">复合菜会因用油、配料和做法产生较大差异，请确认后再记录。</p>
          <div className="ai-preview-actions">
            <button className="secondary-button" disabled={working} type="button" onClick={handleEditAiEstimate}>
              修改估算值
            </button>
            <button className="primary-button" disabled={working} type="button" onClick={handleSaveAiEstimate}>
              {working ? '保存中…' : '确认并加入饮食'}
            </button>
          </div>
        </div>
      )}

      {preview && (
        <div className="preview-card">
          <div className="preview-title">
            <div>
              <span>本次摄入</span>
              <strong>{preview.foodName} · {preview.grams}g</strong>
            </div>
            <b>{preview.nutrition.calories.toFixed(0)} <small>kcal</small></b>
          </div>
          <div className="preview-macros">
            <span>蛋白质 <strong>{preview.nutrition.protein.toFixed(1)}g</strong></span>
            <span>碳水 <strong>{preview.nutrition.carbs.toFixed(1)}g</strong></span>
            <span>脂肪 <strong>{preview.nutrition.fat.toFixed(1)}g</strong></span>
          </div>
          <button className="primary-button" disabled={working} onClick={handleSave}>
            {working ? '保存中…' : '加入今日饮食'}
          </button>
        </div>
      )}
    </section>
  )
}
