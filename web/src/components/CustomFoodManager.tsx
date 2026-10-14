import { useState, type FormEvent } from 'react'
import { archiveCustomFood, getCustomFoods, updateCustomFood } from '../api'
import type { Food } from '../types'

type FoodDraft = {
  name: string
  calories: string
  protein: string
  carbs: string
  fat: string
}

export function CustomFoodManager() {
  const [expanded, setExpanded] = useState(false)
  const [foods, setFoods] = useState<Food[]>([])
  const [loading, setLoading] = useState(false)
  const [workingId, setWorkingId] = useState<number | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [draft, setDraft] = useState<FoodDraft | null>(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  async function loadFoods() {
    setLoading(true)
    setError('')
    try {
      setFoods(await getCustomFoods())
    } catch (requestError) {
      setError((requestError as Error).message)
    } finally {
      setLoading(false)
    }
  }

  function toggleExpanded() {
    const nextExpanded = !expanded
    setExpanded(nextExpanded)
    setError('')
    setMessage('')
    setEditingId(null)
    setDraft(null)
    if (nextExpanded) void loadFoods()
  }

  function startEditing(food: Food) {
    setEditingId(food.id)
    setDraft({
      name: food.name,
      calories: String(food.caloriesPer100g),
      protein: String(food.proteinPer100g),
      carbs: String(food.carbsPer100g),
      fat: String(food.fatPer100g),
    })
    setError('')
    setMessage('')
  }

  async function handleUpdate(event: FormEvent, foodId: number) {
    event.preventDefault()
    if (!draft) return
    const values = [draft.calories, draft.protein, draft.carbs, draft.fat].map(Number)
    if (!draft.name.trim() || values.some((value) => !Number.isFinite(value) || value < 0)) {
      setError('请填写食物名称和完整的非负营养数据。')
      return
    }

    setWorkingId(foodId)
    setError('')
    setMessage('')
    try {
      const updated = await updateCustomFood(foodId, {
        name: draft.name.trim(),
        caloriesPer100g: Number(draft.calories),
        proteinPer100g: Number(draft.protein),
        carbsPer100g: Number(draft.carbs),
        fatPer100g: Number(draft.fat),
      })
      setFoods((current) => current.map((food) => food.id === foodId ? updated : food))
      setEditingId(null)
      setDraft(null)
      setMessage(`“${updated.name}”已更新。`)
    } catch (requestError) {
      setError(readableError((requestError as Error).message))
    } finally {
      setWorkingId(null)
    }
  }

  async function handleArchive(food: Food) {
    if (!window.confirm(`确定归档“${food.name}”吗？已有饮食记录会继续保留。`)) return
    setWorkingId(food.id)
    setError('')
    setMessage('')
    try {
      await archiveCustomFood(food.id)
      setFoods((current) => current.filter((item) => item.id !== food.id))
      setMessage(`“${food.name}”已归档，历史饮食记录不受影响。`)
    } catch (requestError) {
      setError(readableError((requestError as Error).message))
    } finally {
      setWorkingId(null)
    }
  }

  return (
    <section className="panel custom-food-manager" aria-labelledby="custom-food-title">
      <div className="custom-food-heading">
        <div>
          <p className="eyebrow">MY FOOD LIBRARY</p>
          <h2 id="custom-food-title">我的自定义食物</h2>
          <p>管理通过营养标签或 AI 估算创建的私人食物。</p>
        </div>
        <button
          type="button"
          className="secondary-button"
          aria-expanded={expanded}
          aria-controls="custom-food-content"
          onClick={toggleExpanded}
        >
          {expanded ? '收起管理' : '管理自定义食物'}
        </button>
      </div>

      <div id="custom-food-content" hidden={!expanded} className="custom-food-content">
        {loading && <p className="muted">正在读取自定义食物…</p>}
        {error && <p className="form-error" role="alert">{error}</p>}
        {message && <p className="nutrition-plan-saved" role="status">{message}</p>}

        {!loading && foods.length === 0 && (
          <div className="custom-food-empty">
            <strong>还没有自定义食物</strong>
            <span>在饮食记录中搜索不到食物时，可以按包装标签创建。</span>
          </div>
        )}

        <div className="custom-food-list">
          {foods.map((food) => editingId === food.id && draft ? (
            <form className="custom-food-edit" key={food.id} onSubmit={(event) => handleUpdate(event, food.id)}>
              <label className="custom-food-name-field">
                <span>名称</span>
                <input value={draft.name} maxLength={100} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
              </label>
              <div className="custom-food-edit-nutrition">
                {([
                  ['calories', '热量', 'kcal'],
                  ['protein', '蛋白质', 'g'],
                  ['carbs', '碳水', 'g'],
                  ['fat', '脂肪', 'g'],
                ] as const).map(([key, label, unit]) => (
                  <label key={key}>
                    <span>{label}</span>
                    <div className="input-with-unit">
                      <input type="number" min="0" step="0.01" value={draft[key]} onChange={(event) => setDraft({ ...draft, [key]: event.target.value })} />
                      <span>{unit}</span>
                    </div>
                  </label>
                ))}
              </div>
              <p>修改只影响以后新增的记录，已有记录会保留录入时的名称和营养数据。</p>
              <div className="custom-food-edit-actions">
                <button type="button" onClick={() => { setEditingId(null); setDraft(null) }}>取消</button>
                <button type="submit" disabled={workingId === food.id}>
                  {workingId === food.id ? '保存中…' : '保存修改'}
                </button>
              </div>
            </form>
          ) : (
            <article className="custom-food-item" key={food.id}>
              <div>
                <strong>{food.name}</strong>
                <span>每 100g · {food.caloriesPer100g} kcal</span>
              </div>
              <div className="custom-food-macros">
                <span>蛋白质 {food.proteinPer100g}g</span>
                <span>碳水 {food.carbsPer100g}g</span>
                <span>脂肪 {food.fatPer100g}g</span>
              </div>
              <div className="custom-food-actions">
                <button type="button" onClick={() => startEditing(food)}>编辑</button>
                <button type="button" disabled={workingId === food.id} onClick={() => handleArchive(food)}>
                  {workingId === food.id ? '处理中…' : '归档'}
                </button>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}

function readableError(message: string) {
  if (message.includes('built-in food')) return '食物库中已有同名的内置食物。'
  if (message.includes('already have a custom food')) return '你已经有一个同名的自定义食物。'
  return message
}
