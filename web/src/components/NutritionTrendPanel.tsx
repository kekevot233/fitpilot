import { useMemo } from 'react'
import type { NutritionTrend } from '../types'

type Props = {
  trend: NutritionTrend
}

function shortDate(value: string) {
  const [, month, day] = value.split('-')
  return `${Number(month)}/${Number(day)}`
}

export function NutritionTrendPanel({ trend }: Props) {
  const chartMaximum = useMemo(() => Math.max(
    1,
    ...trend.points.flatMap((point) => [point.calories, point.targetCalories]),
  ), [trend.points])
  const difference = trend.averageCalories - trend.averageTargetCalories

  return (
    <section className="panel nutrition-trend-panel" id="nutrition-trend" aria-label="七日饮食趋势">
      <div className="nutrition-trend-heading">
        <div>
          <p className="eyebrow">7-DAY NUTRITION</p>
          <h2>七日热量趋势</h2>
          <p>平均值只统计有饮食记录的日期，空白日期不会被当成零摄入。</p>
        </div>
        <span className="trend-period">{shortDate(trend.startDate)} – {shortDate(trend.endDate)}</span>
      </div>

      <div className="nutrition-trend-content">
        <div className="nutrition-trend-metrics">
          <div>
            <span>平均摄入</span>
            <strong>{trend.daysLogged > 0 ? trend.averageCalories.toFixed(0) : '—'} <small>kcal</small></strong>
          </div>
          <div>
            <span>对应平均目标</span>
            <strong>{trend.averageTargetCalories > 0 ? trend.averageTargetCalories.toFixed(0) : '—'} <small>kcal</small></strong>
          </div>
          <div>
            <span>平均相差</span>
            <strong className={difference > 0 ? 'trend-over' : ''}>
              {trend.daysLogged > 0 && trend.averageTargetCalories > 0
                ? `${difference > 0 ? '+' : ''}${difference.toFixed(0)}`
                : '—'} <small>kcal</small>
            </strong>
          </div>
          <div>
            <span>完成记录</span>
            <strong>{trend.daysLogged}<small> / {trend.points.length} 天</small></strong>
          </div>
        </div>

        <div className="nutrition-bar-chart" role="img" aria-label="每日实际摄入与目标热量柱状图">
          {trend.points.map((point) => {
            const consumedHeight = (point.calories / chartMaximum) * 100
            const targetPosition = (point.targetCalories / chartMaximum) * 100
            return (
              <div className={`nutrition-bar-day${point.logCount === 0 ? ' empty' : ''}`} key={point.date}>
                <div
                  className="nutrition-bar-track"
                  title={`${point.date}：${point.logCount > 0 ? `${point.calories.toFixed(0)} kcal` : '未记录'}，目标 ${point.targetCalories} kcal`}
                >
                  {point.targetCalories > 0 && (
                    <span className="nutrition-target-marker" style={{ bottom: `${targetPosition}%` }} />
                  )}
                  <span className="nutrition-consumed-bar" style={{ height: `${consumedHeight}%` }} />
                </div>
                <span>{shortDate(point.date)}</span>
              </div>
            )
          })}
        </div>
      </div>

      <div className="nutrition-trend-legend">
        <span><i className="consumed-key" />实际摄入</span>
        <span><i className="target-key" />当日目标</span>
      </div>
    </section>
  )
}
