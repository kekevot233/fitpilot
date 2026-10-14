import { useState, type FormEvent } from 'react'
import { updateNutritionPlan } from '../api'
import type { Goal, UserProfile } from '../types'

const plans: { goal: Goal; label: string; rule: string }[] = [
  { goal: 'FAT_LOSS', label: '减脂', rule: '每日消耗（TDEE）− 400 kcal' },
  { goal: 'MAINTAIN', label: '维持', rule: '与每日消耗（TDEE）持平' },
  { goal: 'LEAN_BULK', label: '轻盈增肌', rule: '每日消耗（TDEE）+ 250 kcal' },
]

type Props = {
  user: UserProfile
  viewingHistory: boolean
  onSaved: (user: UserProfile) => void
}

export function NutritionPlanPanel({ user, viewingHistory, onSaved }: Props) {
  const [expanded, setExpanded] = useState(false)
  const [goal, setGoal] = useState(user.goal)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const currentPlan = plans.find((plan) => plan.goal === user.goal)!

  function toggleEditor() {
    setGoal(user.goal)
    setError('')
    setMessage('')
    setExpanded(!expanded)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (saving || goal === user.goal) return
    setSaving(true)
    setError('')
    setMessage('')
    try {
      const updatedUser = await updateNutritionPlan(goal)
      onSaved(updatedUser)
      setExpanded(false)
      setMessage(`已切换为${plans.find((plan) => plan.goal === updatedUser.goal)!.label}，每日目标 ${updatedUser.nutritionTarget.targetCalories} kcal，从 ${updatedUser.nutritionTarget.effectiveDate} 起生效。`)
    } catch (requestError) {
      setError((requestError as Error).message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="panel nutrition-plan-panel" aria-labelledby="nutrition-plan-title">
      <div className="nutrition-plan-heading">
        <div>
          <p className="eyebrow">NUTRITION PLAN</p>
          <h2 id="nutrition-plan-title">当前饮食计划 · {currentPlan.label}</h2>
          <p className="nutrition-plan-description">
            每日目标 <strong>{user.nutritionTarget.targetCalories} kcal</strong>
            {' · '}蛋白质 {user.nutritionTarget.protein}g
            {' · '}碳水 {user.nutritionTarget.carbs}g
            {' · '}脂肪 {user.nutritionTarget.fat}g
          </p>
        </div>
        <button
          type="button"
          className="secondary-button"
          aria-expanded={expanded}
          aria-controls="nutrition-plan-editor"
          disabled={saving}
          onClick={toggleEditor}
        >
          {expanded ? '取消调整' : '调整饮食计划'}
        </button>
      </div>
      {viewingHistory && (
        <p className="nutrition-plan-description">你正在查看历史饮食进度，上方进度使用当时的目标；这里显示当前计划。</p>
      )}
      <div id="nutrition-plan-editor" hidden={!expanded}>
        <form className="nutrition-plan-form" onSubmit={handleSubmit}>
          <fieldset disabled={saving}>
            <legend>选择接下来的饮食目标</legend>
            <div className="nutrition-plan-options">
              {plans.map((plan) => (
                <label className={`nutrition-plan-option${goal === plan.goal ? ' selected' : ''}`} key={plan.goal}>
                  <input
                    type="radio"
                    name="nutrition-goal"
                    value={plan.goal}
                    checked={goal === plan.goal}
                    onChange={() => { setGoal(plan.goal); setError('') }}
                  />
                  <span><strong>{plan.label}</strong><small>{plan.rule}</small></span>
                </label>
              ))}
            </div>
          </fieldset>
          <p className="nutrition-plan-description">
            根据已保存的身体资料和活动水平重新计算热量与三大营养素。保存后从今天生效，并返回今日进度；历史日期的目标保持不变。
          </p>
          {error && <p className="form-error" role="alert">保存失败：{error}，请重试。</p>}
          <button className="primary-button" type="submit" disabled={saving || goal === user.goal}>
            {saving ? '正在保存…' : '保存并重新计算'}
          </button>
        </form>
      </div>
      {message && <p className="nutrition-plan-saved" role="status">{message}</p>}
    </section>
  )
}
