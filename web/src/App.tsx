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
  const today = localDateString()
  const [selectedDate, setSelectedDate] = useState(today)
  const dataVersion = useRef(0)

  const refreshDashboard = useCallback(async () => {
    if (!user) return
    const version = dataVersion.current
    setError('')
    try {
      const result = await getDashboard(user.id, selectedDate)
      if (version === dataVersion.current) setDashboard(result)
    } catch (requestError) {
      if (version === dataVersion.current) setError((requestError as Error).message)
    } finally {
      if (version === dataVersion.current) setLoading(false)
    }
  }, [selectedDate, user])

  const refreshWeightTrend = useCallback(async () => {
    if (!user) return
    const version = dataVersion.current
    try {
      const result = await getWeightTrend(selectedDate)
      if (version === dataVersion.current) setWeightTrend(result)
    } catch (requestError) {
      if (version === dataVersion.current) setError((requestError as Error).message)
    }
  }, [selectedDate, user])

  const refreshNutritionTrend = useCallback(async () => {
    if (!user) return
    const version = dataVersion.current
    try {
      const result = await getNutritionTrend(selectedDate)
      if (version === dataVersion.current) setNutritionTrend(result)
    } catch (requestError) {
      if (version === dataVersion.current) setError((requestError as Error).message)
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
    setWeightTrend(null)
    void refreshDashboard()
    void refreshNutritionTrend()
    void refreshWeightTrend()
    return () => { dataVersion.current += 1 }
  }, [refreshDashboard, refreshNutritionTrend, refreshWeightTrend, user])

  function handleAuthenticated(auth: AuthResponse) {
    setUser(auth.user)
    setLoading(true)
  }

  function handleLogout() {
    dataVersion.current += 1
    clearAccessToken()
    setUser(null)
    setDashboard(null)
    setNutritionTrend(null)
    setWeightTrend(null)
    setError('')
  }

  function handleProfileChanged(updatedUser: UserProfile) {
    dataVersion.current += 1
    setDashboard(null)
    setLoading(true)
    setSelectedDate(localDateString())
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
              onChange={(event) => setSelectedDate(event.target.value)}
              aria-label="选择饮食记录日期"
            />
          </div>
        </section>

        {loading && <div className="status-card">正在读取今日数据…</div>}

        {error && (
          <div className="status-card error-state">
            <strong>暂时无法连接后端</strong>
            <span>{error}</span>
            <button onClick={refreshDashboard}>重新加载</button>
          </div>
        )}

        {dashboard && (
          <div id="nutrition">
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
        {nutritionTrend && (
          <NutritionTrendPanel
            trend={nutritionTrend}
            selectedDate={selectedDate}
            onSelectDate={setSelectedDate}
          />
        )}
        {dashboard && (
          <>
            {weightTrend && (
              <WeightTrendPanel
                selectedDate={selectedDate}
                trend={weightTrend}
                onSaved={refreshWeightTrend}
              />
            )}
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
          </>
        )}
      </main>

      <footer>
        <span>FitPilot MVP</span>
        <span>数据来自你的食物记录 · 结果仅供日常营养管理参考</span>
      </footer>
    </div>
  )
}
