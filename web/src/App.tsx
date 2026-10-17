import { useCallback, useEffect, useRef, useState } from 'react'
import {
  clearAccessToken,
  getAccessToken,
  getCurrentUser,
  getDashboard,
  getNutritionTrend,
  getWeightTrend,
} from './api'
import { AuthScreen } from './components/AuthScreen'
import { BodyProfilePanel } from './components/BodyProfilePanel'
import { CustomFoodManager } from './components/CustomFoodManager'
import { FoodEntryForm } from './components/FoodEntryForm'
import { FoodLogList } from './components/FoodLogList'
import { NutritionProgress } from './components/NutritionProgress'
import { NutritionPlanPanel } from './components/NutritionPlanPanel'
import { NutritionTrendPanel } from './components/NutritionTrendPanel'
import { WeightTrendPanel } from './components/WeightTrendPanel'
import type { AuthResponse, DailyDashboard, NutritionTrend, UserProfile, WeightTrend } from './types'

function localDateString() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function readableDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(new Date(`${value}T00:00:00`))
}

export default function App() {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [checkingAuth, setCheckingAuth] = useState(true)
  const [dashboard, setDashboard] = useState<DailyDashboard | null>(null)
  const [nutritionTrend, setNutritionTrend] = useState<NutritionTrend | null>(null)
  const [weightTrend, setWeightTrend] = useState<WeightTrend | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [nutritionTrendError, setNutritionTrendError] = useState('')
  const [weightError, setWeightError] = useState('')
  const today = localDateString()
  const [selectedDate, setSelectedDate] = useState(today)
  const [weightDate, setWeightDate] = useState(today)
  const dataVersion = useRef(0)
  const weightVersion = useRef(0)
  const activeWeightQuery = useRef('')

  const refreshDashboard = useCallback(async () => {
    if (!user) return
    const version = dataVersion.current
    setError('')
    try {
      const result = await getDashboard(user.id, selectedDate)
      if (version === dataVersion.current) setDashboard(result)
    } catch (requestError) {
      if (version === dataVersion.current) {
        setError((requestError as Error).message)
      }
    } finally {
      if (version === dataVersion.current) setLoading(false)
    }
  }, [selectedDate, user])

  const refreshWeightTrend = useCallback(async () => {
    if (!user) return
    if (activeWeightQuery.current !== `${user.id}:${weightDate}`) return
    const version = weightVersion.current
    setWeightError('')
    try {
      const result = await getWeightTrend(weightDate)
      if (version === weightVersion.current) setWeightTrend(result)
    } catch (requestError) {
      if (version === weightVersion.current) setWeightError((requestError as Error).message)
    }
  }, [weightDate, user])

  const refreshNutritionTrend = useCallback(async () => {
    if (!user) return
    const version = dataVersion.current
    setNutritionTrendError('')
    try {
      const result = await getNutritionTrend(selectedDate)
      if (version === dataVersion.current) setNutritionTrend(result)
    } catch (requestError) {
      if (version === dataVersion.current) setNutritionTrendError((requestError as Error).message)
    }
  }, [selectedDate, user])

  useEffect(() => {
    if (!getAccessToken()) {
      setCheckingAuth(false)
      return
    }
    getCurrentUser()
      .then(setUser)
      .catch(() => clearAccessToken())
      .finally(() => setCheckingAuth(false))
  }, [])

  useEffect(() => {
    if (!user) return
    setLoading(true)
    setDashboard(null)
    setNutritionTrend(null)
    void refreshDashboard()
    void refreshNutritionTrend()
    return () => { dataVersion.current += 1 }
  }, [refreshDashboard, refreshNutritionTrend, user])

  useEffect(() => {
    if (!user) return
    activeWeightQuery.current = `${user.id}:${weightDate}`
    setWeightTrend(null)
    void refreshWeightTrend()
    return () => {
      weightVersion.current += 1
      activeWeightQuery.current = ''
    }
  }, [refreshWeightTrend, user, weightDate])

  function handleDateChanged(date: string) {
    if (!date || date > today) return
    setSelectedDate(date)
    setWeightDate(date)
  }

  function handleAuthenticated(auth: AuthResponse) {
    setUser(auth.user)
    setLoading(true)
  }

  function handleLogout() {
    dataVersion.current += 1
    weightVersion.current += 1
    activeWeightQuery.current = ''
    clearAccessToken()
    setUser(null)
    setDashboard(null)
    setNutritionTrend(null)
    setWeightTrend(null)
    setError('')
    setNutritionTrendError('')
    setWeightError('')
  }

  function handleProfileChanged(updatedUser: UserProfile) {
    dataVersion.current += 1
    setDashboard(null)
    setLoading(true)
    setSelectedDate(localDateString())
    setWeightDate(localDateString())
    setUser(updatedUser)
  }

  if (checkingAuth) {
    return <div className="auth-loading">正在检查登录状态…</div>
  }

  if (!user) {
    return <AuthScreen onAuthenticated={handleAuthenticated} />
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="FitPilot 首页">
          <span className="brand-mark">F</span>
          <span>FitPilot</span>
        </a>
        <nav aria-label="主要导航">
          <a className="active" href="#nutrition">今日饮食</a>
          <a href="#nutrition-trend">七日趋势</a>
          <a href="#weight-trend">体重趋势</a>
        </nav>
        <div className="user-chip">
          <span>{user.nickname.slice(0, 1).toUpperCase()}</span>
          <div>
            <strong>{user.nickname}</strong>
            <small>{user.email}</small>
          </div>
          <button className="logout-button" onClick={handleLogout}>退出</button>
        </div>
      </header>

      <main id="top">
        <section className="page-intro">
          <div>
            <p className="eyebrow">NUTRITION DASHBOARD</p>
            <h1>你好，今天也吃得明白一点。</h1>
            <p>记录真实克数，FitPilot 帮你计算每一餐。</p>
          </div>
          <div className="date-chip">
            <span>{selectedDate === today ? '今天' : '查看日期'}</span>
            <strong>{readableDate(selectedDate)}</strong>
            <input
              type="date"
              value={selectedDate}
              max={today}
              onChange={(event) => handleDateChanged(event.target.value)}
              aria-label="选择饮食记录日期"
            />
          </div>
        </section>

        {loading && <div className="status-card">正在读取今日数据…</div>}

        {error && (
          <div className="status-card error-state">
            <strong>饮食进度暂不可用</strong>
            <span>{error}</span>
            <button onClick={refreshDashboard}>重新加载</button>
          </div>
        )}

        {dashboard && (
          <div id="nutrition">
            {!dashboard.target && (
              <p className="status-card">
                该日期尚未建立营养目标，可补记饮食和体重；摄入记录不会套用当前计划。
              </p>
            )}
            <NutritionProgress dashboard={dashboard} />
          </div>
        )}
        <BodyProfilePanel
          key={`body-${user.id}`}
          user={user}
          onSaved={handleProfileChanged}
        />
        <NutritionPlanPanel
          key={`plan-${user.id}`}
          user={user}
          viewingHistory={selectedDate !== today}
          onSaved={handleProfileChanged}
        />
        <CustomFoodManager />
        {nutritionTrendError && (
          <div className="status-card error-state" role="alert">
            <strong>七日饮食趋势暂不可用</strong>
            <span>{nutritionTrendError}</span>
            <button onClick={refreshNutritionTrend}>重新加载饮食趋势</button>
          </div>
        )}
        {nutritionTrend && (
          <NutritionTrendPanel
            trend={nutritionTrend}
            selectedDate={selectedDate}
            onSelectDate={handleDateChanged}
          />
        )}
        {weightError && (
          <div className="status-card error-state" role="alert">
            <strong>体重趋势暂不可用</strong>
            <span>{weightError}</span>
            <button onClick={refreshWeightTrend}>重新加载体重趋势</button>
          </div>
        )}
        {weightTrend && (
          <WeightTrendPanel
            key={`${user.id}-${weightDate}`}
            selectedDate={weightDate}
            today={today}
            onSelectDate={setWeightDate}
            trend={weightTrend}
            onSaved={refreshWeightTrend}
          />
        )}
        {dashboard && (
          <div className="content-grid">
            <FoodEntryForm
              userId={user.id}
              date={selectedDate}
              onSaved={async () => {
                await Promise.all([refreshDashboard(), refreshNutritionTrend()])
              }}
            />
            <FoodLogList
              userId={user.id}
              date={selectedDate}
              logs={dashboard.foodLogs}
              onChanged={async () => {
                await Promise.all([refreshDashboard(), refreshNutritionTrend()])
              }}
            />
          </div>
        )}
      </main>

      <footer>
        <span>FitPilot MVP</span>
        <span>数据来自你的食物记录 · 结果仅供日常营养管理参考</span>
      </footer>
    </div>
  )
}
