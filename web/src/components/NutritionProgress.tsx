import type { DailyDashboard, NutritionValues } from '../types'

type Props = {
  dashboard: DailyDashboard
}

type MacroKey = Exclude<keyof NutritionValues, 'calories'>

const macroConfig: Array<{ key: MacroKey; label: string; color: string }> = [
  { key: 'protein', label: '蛋白质', color: '#eb7655' },
  { key: 'carbs', label: '碳水', color: '#d7a82c' },
  { key: 'fat', label: '脂肪', color: '#638cce' },
]

function percent(current: number, target: number) {
  if (target <= 0) return 0
  return Math.min(100, Math.max(0, (current / target) * 100))
}

export function NutritionProgress({ dashboard }: Props) {
  const caloriePercent = percent(
    dashboard.consumed.calories,
    dashboard.target?.calories ?? 0,
  )

  return (
    <section className="nutrition-overview" aria-label="当日营养进度">
      <div className="calorie-card">
        <div>
          <p className="eyebrow light">当日能量</p>
          <div className="calorie-value">
            {dashboard.consumed.calories.toFixed(0)}
            <span> kcal</span>
          </div>
          <p className="calorie-target">
            {dashboard.target ? `目标 ${dashboard.target.calories.toFixed(0)} kcal` : '当天无目标'}
          </p>
        </div>

        {dashboard.remaining && <div
          className="calorie-ring"
          style={{ '--progress': `${caloriePercent * 3.6}deg` } as React.CSSProperties}
          aria-label={`已完成 ${caloriePercent.toFixed(0)}%`}
        >
          <div>
            <strong>{Math.max(0, dashboard.remaining.calories).toFixed(0)}</strong>
            <span>剩余 kcal</span>
          </div>
        </div>}
      </div>

      <div className="macro-grid">
        {macroConfig.map(({ key, label, color }) => {
          const current = dashboard.consumed[key]
          const target = dashboard.target?.[key]
          return (
            <article className="macro-card" key={key}>
              <div className="macro-heading">
                <span className="macro-dot" style={{ backgroundColor: color }} />
                <span>{label}</span>
                <strong>{dashboard.remaining ? `${Math.max(0, dashboard.remaining[key]).toFixed(1)}g 剩余` : '已摄入'}</strong>
              </div>
              <div className="macro-numbers">
                <strong>{current.toFixed(1)}</strong>
                <span>{target === undefined ? 'g' : `/ ${target.toFixed(0)}g`}</span>
              </div>
              {target !== undefined && <div className="progress-track">
                <div
                  className="progress-fill"
                  style={{ width: `${percent(current, target)}%`, backgroundColor: color }}
                />
              </div>}
            </article>
          )
        })}
      </div>
    </section>
  )
}
