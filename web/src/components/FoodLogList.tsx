import { useMemo, useState } from 'react'
import { copyFoodLogs, deleteFoodLog, updateFoodLog } from '../api'
import type { FoodLog, MealType } from '../types'

type Props = {
  userId: number
  date: string
  logs: FoodLog[]
  onChanged: () => Promise<void>
}

const mealLabels: Record<MealType, string> = {
  BREAKFAST: '早餐',
  LUNCH: '午餐',
  DINNER: '晚餐',
  SNACK: '加餐',
}

const mealOrder: MealType[] = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK']

const mealMarks: Record<MealType, string> = {
  BREAKFAST: '早',
  LUNCH: '午',
  DINNER: '晚',
  SNACK: '加',
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

export function FoodLogList({ userId, date, logs, onChanged }: Props) {
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editGrams, setEditGrams] = useState('')
  const [editMealType, setEditMealType] = useState<MealType>('LUNCH')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [copying, setCopying] = useState(false)
  const [message, setMessage] = useState('')

  const today = new Date().toLocaleDateString('sv-SE')
  const sourceDate = previousDate(date)
  const mealGroups = useMemo(() => mealOrder
    .map((mealType) => {
      const mealLogs = logs.filter((log) => log.mealType === mealType)
      const subtotal = mealLogs.reduce((sum, log) => ({
        calories: sum.calories + log.nutrition.calories,
        protein: sum.protein + log.nutrition.protein,
        carbs: sum.carbs + log.nutrition.carbs,
        fat: sum.fat + log.nutrition.fat,
      }), { calories: 0, protein: 0, carbs: 0, fat: 0 })
      return { mealType, logs: mealLogs, subtotal }
    })
    .filter((group) => group.logs.length > 0), [logs])

  async function handleCopyPreviousDay() {
    setCopying(true)
    setError('')
    setMessage('')
    try {
      const result = await copyFoodLogs(sourceDate, date)
      setMessage(`已复制 ${result.copiedCount} 条饮食记录。`)
      await onChanged()
    } catch (requestError) {
      const detail = (requestError as Error).message
      setError(detail === 'The source date does not contain food logs.'
        ? '前一天没有饮食记录可复制。'
        : detail)
    } finally {
      setCopying(false)
    }
  }

  async function handleDelete(logId: number) {
    setDeletingId(logId)
    setError('')
    try {
      await deleteFoodLog(userId, logId)
      await onChanged()
    } catch (requestError) {
      setError((requestError as Error).message)
    } finally {
      setDeletingId(null)
    }
  }

  function startEdit(log: FoodLog) {
    setEditingId(log.id)
    setEditGrams(String(log.grams))
    setEditMealType(log.mealType)
    setError('')
  }

  async function saveEdit(log: FoodLog) {
    const grams = Number(editGrams)
    if (!Number.isFinite(grams) || grams <= 0) {
      setError('克数必须大于 0。')
      return
    }

    setSaving(true)
    setError('')
    try {
      await updateFoodLog(userId, log.id, {
        foodId: log.foodId,
        grams,
        mealType: editMealType,
        eatenAt: log.eatenAt,
      })
      setEditingId(null)
      await onChanged()
    } catch (requestError) {
      setError((requestError as Error).message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="panel log-panel">
      <div className="section-heading">
        <div>
          <p className="eyebrow">饮食日记</p>
          <h2>{date === today ? '今日记录' : '当日记录'}</h2>
        </div>
        <span className="log-count">{logs.length} 项</span>
      </div>

      {error && <p className="form-error">{error}</p>}
      {message && <p className="nutrition-plan-saved" role="status">{message}</p>}

      {logs.length === 0 ? (
        <div className="empty-state">
          <span>＋</span>
          <strong>{date === today ? '今天' : '这一天'}还没有饮食记录</strong>
          <p>从左侧选择一种食物开始记录。</p>
          <button
            className="copy-day-button"
            type="button"
            disabled={copying}
            onClick={handleCopyPreviousDay}
          >
            {copying ? '正在复制…' : `复制 ${readableShortDate(sourceDate)} 的记录`}
          </button>
        </div>
      ) : (
        <div className="log-list">
          {mealGroups.map((group) => (
            <section className="meal-group" aria-labelledby={`meal-${group.mealType}`} key={group.mealType}>
              <header className="meal-group-heading">
                <div className="meal-group-title">
                  <span aria-hidden="true">{mealMarks[group.mealType]}</span>
                  <div>
                    <h3 id={`meal-${group.mealType}`}>{mealLabels[group.mealType]}</h3>
                    <small>{group.logs.length} 项记录</small>
                  </div>
                </div>
                <div className="meal-subtotal">
                  <strong>{group.subtotal.calories.toFixed(0)} kcal</strong>
                  <span>
                    蛋白质 {group.subtotal.protein.toFixed(1)}g
                    {' · '}碳水 {group.subtotal.carbs.toFixed(1)}g
                    {' · '}脂肪 {group.subtotal.fat.toFixed(1)}g
                  </span>
                </div>
              </header>
              <div className="meal-log-list">
                {group.logs.map((log) => (
                  <article className={`log-item ${editingId === log.id ? 'editing' : ''}`} key={log.id}>
                    {editingId === log.id ? (
                      <div className="edit-log-form">
                        <div>
                          <strong>{log.foodName}</strong>
                          <small>{formatTime(log.eatenAt)}</small>
                        </div>
                        <div className="edit-fields">
                          <div className="input-with-unit">
                            <input
                              type="number"
                              min="0.1"
                              max="5000"
                              step="0.1"
                              value={editGrams}
                              onChange={(event) => setEditGrams(event.target.value)}
                              aria-label="修改克数"
                            />
                            <span>g</span>
                          </div>
                          <select
                            value={editMealType}
                            onChange={(event) => setEditMealType(event.target.value as MealType)}
                            aria-label="修改餐次"
                          >
                            {Object.entries(mealLabels).map(([value, label]) => (
                              <option value={value} key={value}>{label}</option>
                            ))}
                          </select>
                        </div>
                        <div className="edit-actions">
                          <button type="button" onClick={() => setEditingId(null)}>取消</button>
                          <button type="button" disabled={saving} onClick={() => saveEdit(log)}>
                            {saving ? '保存中…' : '保存'}
                          </button>
                        </div>
                      </div>
                    ) : (
                      <>
                        <div className="meal-time">
                          <time dateTime={log.eatenAt}>{formatTime(log.eatenAt)}</time>
                        </div>
                        <div className="log-food">
                          <strong>{log.foodName}</strong>
                          <span>{log.grams}g · 蛋白质 {log.nutrition.protein.toFixed(1)}g</span>
                        </div>
                        <div className="log-calories">
                          <strong>{log.nutrition.calories.toFixed(0)}</strong>
                          <span>kcal</span>
                        </div>
                        <div className="log-actions">
                          <button
                            className="edit-button"
                            onClick={() => startEdit(log)}
                            aria-label={`修改${log.foodName}`}
                            title="修改记录"
                          >
                            编辑
                          </button>
                          <button
                            className="delete-button"
                            disabled={deletingId === log.id}
                            onClick={() => handleDelete(log.id)}
                            aria-label={`删除${log.foodName}`}
                            title="删除记录"
                          >
                            {deletingId === log.id ? '…' : '×'}
                          </button>
                        </div>
                      </>
                    )}
                  </article>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </section>
  )
}

function previousDate(value: string) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() - 1)
  return date.toLocaleDateString('sv-SE')
}

function readableShortDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' })
    .format(new Date(`${value}T00:00:00`))
}
