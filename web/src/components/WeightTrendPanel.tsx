import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { upsertWeightLog } from '../api'
import type { WeightTrend } from '../types'

type Props = {
  selectedDate: string
  trend: WeightTrend
  onSaved: () => Promise<void>
}

type ChartPoint = {
  x: number
  y: number
}

const CHART_WIDTH = 620
const CHART_HEIGHT = 176
const CHART_PADDING_X = 18
const CHART_PADDING_Y = 18

function formatSignedWeight(value: number | null) {
  if (value === null) return '—'
  return `${value > 0 ? '+' : ''}${value.toFixed(2)} kg`
}

function formatShortDate(value: string) {
  const [, month, day] = value.split('-')
  return `${Number(month)}/${Number(day)}`
}

function linePath(points: ChartPoint[]) {
  return points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ')
}

export function WeightTrendPanel({ selectedDate, trend, onSaved }: Props) {
  const [weightKg, setWeightKg] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)

  const selectedPoint = trend.points.find((point) => point.recordedDate === selectedDate)

  useEffect(() => {
    setWeightKg(selectedPoint ? String(selectedPoint.weightKg) : '')
    setError('')
    setSaved(false)
  }, [selectedDate, selectedPoint])

  const chart = useMemo(() => {
    if (trend.points.length < 2) return null
    const values = trend.points.flatMap((point) => [point.weightKg, point.sevenDayAverageKg])
    const minimum = Math.min(...values)
    const maximum = Math.max(...values)
    const range = Math.max(maximum - minimum, 0.2)
    const usableWidth = CHART_WIDTH - CHART_PADDING_X * 2
    const usableHeight = CHART_HEIGHT - CHART_PADDING_Y * 2
    const toPoint = (value: number, index: number): ChartPoint => ({
      x: CHART_PADDING_X + (index / (trend.points.length - 1)) * usableWidth,
      y: CHART_PADDING_Y + ((maximum - value) / range) * usableHeight,
    })

    return {
      rawPoints: trend.points.map((point, index) => toPoint(point.weightKg, index)),
      averagePoints: trend.points.map((point, index) => toPoint(point.sevenDayAverageKg, index)),
      minimum,
      maximum,
    }
  }, [trend.points])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const value = Number(weightKg)
    if (!Number.isFinite(value) || value < 30 || value > 350) {
      setError('请输入 30 到 350 kg 之间的体重。')
      return
    }

    setSaving(true)
    setError('')
    setSaved(false)
    try {
      await upsertWeightLog(selectedDate, value)
      await onSaved()
      setSaved(true)
    } catch (requestError) {
      setError((requestError as Error).message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="weight-panel" id="weight-trend" aria-label="体重趋势">
      <div className="panel-heading weight-heading">
        <div>
          <p className="eyebrow">WEIGHT TREND</p>
          <h2>体重趋势</h2>
          <p>建议记录晨起、如厕后、进食前的体重；趋势比单日数字更重要。</p>
        </div>
        <div className="weight-summary">
          <span>近 30 天变化</span>
          <strong className={trend.changeKg !== null && trend.changeKg > 0 ? 'weight-up' : ''}>
            {formatSignedWeight(trend.changeKg)}
          </strong>
        </div>
      </div>

      <div className="weight-panel-content">
        <form className="weight-form" onSubmit={handleSubmit}>
          <div>
            <span>{selectedDate === trend.endDate ? '记录今天体重' : '记录该日期体重'}</span>
            <strong>{selectedDate}</strong>
          </div>
          <label className="input-with-unit">
            <input
              type="number"
              min="30"
              max="350"
              step="0.01"
              required
              value={weightKg}
              onChange={(event) => setWeightKg(event.target.value)}
              placeholder="例如 75.80"
              aria-label="体重，单位 kg"
            />
            <span>kg</span>
          </label>
          <button className="primary-button" disabled={saving} type="submit">
            {saving ? '保存中…' : selectedPoint ? '更新体重' : '保存体重'}
          </button>
          {saved && <p className="weight-saved">已保存，这一天的记录可以随时修改。</p>}
          {error && <p className="form-error">{error}</p>}
        </form>

        <div className="weight-chart-area">
          {trend.points.length === 0 ? (
            <div className="weight-empty">
              <strong>还没有体重记录</strong>
              <span>先保存一次体重，FitPilot 会开始计算你的 7 日趋势。</span>
            </div>
          ) : (
            <>
              <div className="weight-metrics">
                <div><span>最新体重</span><strong>{trend.latestWeightKg?.toFixed(2) ?? '—'} kg</strong></div>
                <div><span>起始体重</span><strong>{trend.firstWeightKg?.toFixed(2) ?? '—'} kg</strong></div>
                <div><span>最近 7 日均值</span><strong>{trend.points.at(-1)?.sevenDayAverageKg.toFixed(2) ?? '—'} kg</strong></div>
              </div>
              {chart ? (
                <div className="weight-chart-wrap">
                  <div className="chart-scale"><span>{chart.maximum.toFixed(1)} kg</span><span>{chart.minimum.toFixed(1)} kg</span></div>
                  <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} role="img" aria-label="体重与七日移动平均趋势图">
                    <path className="chart-grid" d={`M ${CHART_PADDING_X} ${CHART_HEIGHT / 2} H ${CHART_WIDTH - CHART_PADDING_X}`} />
                    <path className="chart-line raw-line" d={linePath(chart.rawPoints)} />
                    <path className="chart-line average-line" d={linePath(chart.averagePoints)} />
                    {chart.rawPoints.map((point, index) => <circle className="chart-dot" key={trend.points[index].recordedDate} cx={point.x} cy={point.y} r="3.5" />)}
                  </svg>
                  <div className="chart-labels"><span>{formatShortDate(trend.points[0].recordedDate)}</span><span>{formatShortDate(trend.points.at(-1)!.recordedDate)}</span></div>
                  <div className="chart-legend"><span><i className="raw-key" />实际体重</span><span><i className="average-key" />7 日平均</span></div>
                </div>
              ) : (
                <div className="weight-single-point">再记录一次体重后，将显示折线趋势。</div>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  )
}
