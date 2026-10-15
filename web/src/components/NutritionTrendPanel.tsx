import { useMemo } from 'react'
import type { NutritionTrend } from '../types'

type Props = {
  trend: NutritionTrend
  selectedDate: string
  onSelectDate: (date: string) => void
}

function shortDate(value: string) {
  const [, month, day] = value.split('-')
  return `${Number(month)}/${Number(day)}`
}

function dayLabel(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(new Date(`${value}T00:00:00`))
}

export function NutritionTrendPanel({ trend, selectedDate, onSelectDate }: Props) {
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

        <div className="nutrition-bar-chart" role="group" aria-label="选择日期查看饮食记录">
          {trend.points.map((point) => {
            const consumedHeight = (point.calories / chartMaximum) * 100
            const targetPosition = (point.targetCalories / chartMaximum) * 100
            const summary = point.logCount > 0
              ? `摄入 ${point.calories.toFixed(0)} 千卡，目标 ${point.targetCalories} 千卡`
              : `未记录，目标 ${point.targetCalories} 千卡`
            return (
              <button
                className={`nutrition-bar-day${point.logCount === 0 ? ' empty' : ''}${point.date === selectedDate ? ' selected' : ''}`}
                type="button"
                aria-current={point.date === selectedDate ? 'date' : undefined}
                aria-label={`查看${dayLabel(point.date)}的饮食记录：${summary}`}
                title={`${point.date}：${summary}`}
                onClick={() => onSelectDate(point.date)}
                key={point.date}
              >
                <div
                  className="nutrition-bar-track"
                  aria-hidden="true"
                >
                  {point.targetCalories > 0 && (
                    <span className="nutrition-target-marker" style={{ bottom: `${targetPosition}%` }} />
                  )}
                  <span className="nutrition-consumed-bar" style={{ height: `${consumedHeight}%` }} />
                </div>
                <span>{shortDate(point.date)}</span>
              </button>
            )
          })}
        </div>
      </div>

      <div className="nutrition-trend-legend">
        <span><i className="consumed-key" />实际摄入</span>
        <span><i className="target-key" />当日目标</span>
        <span>点击日期查看明细</span>
      </div>
    </section>
  )
}
