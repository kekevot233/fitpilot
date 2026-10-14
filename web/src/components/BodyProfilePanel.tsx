import { useState, type FormEvent } from 'react'
import { updateProfile } from '../api'
import type { ActivityLevel, UserProfile } from '../types'

const activityOptions: { value: ActivityLevel; label: string; detail: string }[] = [
  { value: 'SEDENTARY', label: '久坐', detail: '很少运动' },
  { value: 'LIGHT', label: '轻度活动', detail: '每周运动 1–3 天' },
  { value: 'MODERATE', label: '中度活动', detail: '每周运动 3–5 天' },
  { value: 'VERY_ACTIVE', label: '高度活动', detail: '每周运动 6–7 天' },
  { value: 'EXTRA_ACTIVE', label: '极高活动', detail: '高强度训练或体力工作' },
]

type Props = {
  user: UserProfile
  onSaved: (user: UserProfile) => void
}

export function BodyProfilePanel({ user, onSaved }: Props) {
  const [expanded, setExpanded] = useState(false)
  const [age, setAge] = useState(String(user.age))
  const [heightCm, setHeightCm] = useState(String(user.heightCm))
  const [weightKg, setWeightKg] = useState(String(user.weightKg))
  const [activityLevel, setActivityLevel] = useState(user.activityLevel)
  const [weeklyTrainingFrequency, setWeeklyTrainingFrequency] = useState(String(user.weeklyTrainingFrequency))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const currentActivity = activityOptions.find((option) => option.value === user.activityLevel)!

  function resetForm() {
    setAge(String(user.age))
    setHeightCm(String(user.heightCm))
    setWeightKg(String(user.weightKg))
    setActivityLevel(user.activityLevel)
    setWeeklyTrainingFrequency(String(user.weeklyTrainingFrequency))
    setError('')
  }

  function toggleEditor() {
    if (!expanded) resetForm()
    setMessage('')
    setExpanded(!expanded)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (saving) return
    setSaving(true)
    setError('')
    setMessage('')
    try {
      const updatedUser = await updateProfile({
        age: Number(age),
        heightCm: Number(heightCm),
        weightKg: Number(weightKg),
        activityLevel,
        weeklyTrainingFrequency: Number(weeklyTrainingFrequency),
      })
      onSaved(updatedUser)
      setExpanded(false)
      setMessage(`身体资料已保存，营养目标已按当前的${goalLabel(updatedUser.goal)}计划重新计算，并从 ${updatedUser.nutritionTarget.effectiveDate} 起生效。`)
    } catch (requestError) {
      setError((requestError as Error).message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="panel body-profile-panel" aria-labelledby="body-profile-title">
      <div className="body-profile-heading">
        <div>
          <p className="eyebrow">BODY PROFILE</p>
          <h2 id="body-profile-title">身体资料</h2>
          <div className="body-profile-summary" aria-label="当前身体资料">
            <span><strong>{user.age}</strong> 岁</span>
            <span><strong>{user.heightCm}</strong> cm</span>
            <span><strong>{user.weightKg}</strong> kg</span>
            <span><strong>{currentActivity.label}</strong></span>
            <span>每周训练 <strong>{user.weeklyTrainingFrequency}</strong> 天</span>
          </div>
        </div>
        <button
          type="button"
          className="secondary-button"
          aria-expanded={expanded}
          aria-controls="body-profile-editor"
          disabled={saving}
          onClick={toggleEditor}
        >
          {expanded ? '取消编辑' : '编辑身体资料'}
        </button>
      </div>

      <div id="body-profile-editor" hidden={!expanded}>
        <form className="body-profile-form" onSubmit={handleSubmit}>
          <div className="body-profile-fields">
            <label>
              <span>年龄</span>
              <input type="number" min="14" max="100" required value={age} onChange={(event) => setAge(event.target.value)} />
            </label>
            <label>
              <span>身高</span>
              <div className="input-with-unit">
                <input type="number" min="100" max="250" step="0.1" required value={heightCm} onChange={(event) => setHeightCm(event.target.value)} />
                <span>cm</span>
              </div>
            </label>
            <label>
              <span>计划体重基准</span>
              <div className="input-with-unit">
                <input type="number" min="30" max="350" step="0.1" required value={weightKg} onChange={(event) => setWeightKg(event.target.value)} />
                <span>kg</span>
              </div>
            </label>
            <label>
              <span>活动水平</span>
              <select value={activityLevel} onChange={(event) => setActivityLevel(event.target.value as ActivityLevel)}>
                {activityOptions.map((option) => (
                  <option value={option.value} key={option.value}>{option.label} · {option.detail}</option>
                ))}
              </select>
            </label>
            <label>
              <span>每周训练频率</span>
              <div className="input-with-unit">
                <input type="number" min="1" max="7" required value={weeklyTrainingFrequency} onChange={(event) => setWeeklyTrainingFrequency(event.target.value)} />
                <span>天</span>
              </div>
            </label>
          </div>
          <p className="nutrition-plan-description">
            保存后会使用当前饮食目标重新计算 BMR、TDEE 和三大营养素。每日体重记录用于观察趋势，不会自动修改这里的计划体重基准。
          </p>
          {error && <p className="form-error" role="alert">保存失败：{error}，请检查输入后重试。</p>}
          <button className="primary-button" type="submit" disabled={saving}>
            {saving ? '正在保存…' : '保存并重新计算'}
          </button>
        </form>
      </div>
      {message && <p className="nutrition-plan-saved" role="status">{message}</p>}
    </section>
  )
}

function goalLabel(goal: UserProfile['goal']) {
  if (goal === 'FAT_LOSS') return '减脂'
  if (goal === 'MAINTAIN') return '维持'
  return '轻盈增肌'
}
