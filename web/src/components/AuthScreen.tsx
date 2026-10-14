import { useState, type FormEvent } from 'react'
import { login, register, saveAccessToken } from '../api'
import type { ActivityLevel, AuthResponse, Gender, Goal } from '../types'

type Props = {
  onAuthenticated: (auth: AuthResponse) => void
}

type RegisterForm = {
  email: string
  password: string
  nickname: string
  gender: Gender
  age: string
  heightCm: string
  weightKg: string
  activityLevel: ActivityLevel
  goal: Goal
  weeklyTrainingFrequency: string
}

const initialRegisterForm: RegisterForm = {
  email: '',
  password: '',
  nickname: '',
  gender: 'MALE',
  age: '22',
  heightCm: '175',
  weightKg: '70',
  activityLevel: 'MODERATE',
  goal: 'MAINTAIN',
  weeklyTrainingFrequency: '3',
}

export function AuthScreen({ onAuthenticated }: Props) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [loginForm, setLoginForm] = useState({ email: '', password: '' })
  const [registerForm, setRegisterForm] = useState(initialRegisterForm)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState('')

  function updateRegister<Key extends keyof RegisterForm>(key: Key, value: RegisterForm[Key]) {
    setRegisterForm((current) => ({ ...current, [key]: value }))
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setWorking(true)
    setError('')
    try {
      const auth = mode === 'login'
        ? await login(loginForm.email, loginForm.password)
        : await register({
            email: registerForm.email,
            password: registerForm.password,
            nickname: registerForm.nickname,
            gender: registerForm.gender,
            age: Number(registerForm.age),
            heightCm: Number(registerForm.heightCm),
            weightKg: Number(registerForm.weightKg),
            activityLevel: registerForm.activityLevel,
            goal: registerForm.goal,
            weeklyTrainingFrequency: Number(registerForm.weeklyTrainingFrequency),
          })
      saveAccessToken(auth.accessToken)
      onAuthenticated(auth)
    } catch (requestError) {
      setError((requestError as Error).message)
    } finally {
      setWorking(false)
    }
  }

  function switchMode(nextMode: 'login' | 'register') {
    setMode(nextMode)
    setError('')
  }

  return (
    <main className="auth-page">
      <section className="auth-story">
        <a className="brand auth-brand" href="#auth">
          <span className="brand-mark">F</span>
          <span>FitPilot</span>
        </a>
        <div>
          <p className="eyebrow light">YOUR DAILY NUTRITION COPILOT</p>
          <h1>把每一餐，变成看得懂的进度。</h1>
          <p>从真实克数开始，记录热量与三大营养素，让饮食目标不再靠猜。</p>
        </div>
        <div className="auth-feature-row">
          <span>✓ 个性化目标</span>
          <span>✓ 47 项基础食物</span>
          <span>✓ 每日营养进度</span>
        </div>
      </section>

      <section className="auth-form-panel" id="auth">
        <div className="auth-tabs">
          <button className={mode === 'login' ? 'active' : ''} onClick={() => switchMode('login')}>
            登录
          </button>
          <button className={mode === 'register' ? 'active' : ''} onClick={() => switchMode('register')}>
            创建账户
          </button>
        </div>

        <div className="auth-form-heading">
          <p className="eyebrow">{mode === 'login' ? 'WELCOME BACK' : 'GET STARTED'}</p>
          <h2>{mode === 'login' ? '继续记录今天' : '建立你的营养目标'}</h2>
          <p>{mode === 'login' ? '登录后查看只属于你的饮食数据。' : '只需一分钟，系统会自动计算每日目标。'}</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          {mode === 'login' ? (
            <>
              <label>
                <span>邮箱</span>
                <input
                  type="email"
                  required
                  value={loginForm.email}
                  onChange={(event) => setLoginForm({ ...loginForm, email: event.target.value })}
                  placeholder="name@example.com"
                />
              </label>
              <label>
                <span>密码</span>
                <input
                  type="password"
                  required
                  value={loginForm.password}
                  onChange={(event) => setLoginForm({ ...loginForm, password: event.target.value })}
                  placeholder="输入密码"
                />
              </label>
            </>
          ) : (
            <>
              <div className="auth-grid two-columns">
                <label>
                  <span>昵称</span>
                  <input required maxLength={40} value={registerForm.nickname} onChange={(e) => updateRegister('nickname', e.target.value)} />
                </label>
                <label>
                  <span>性别</span>
                  <select value={registerForm.gender} onChange={(e) => updateRegister('gender', e.target.value as Gender)}>
                    <option value="MALE">男</option>
                    <option value="FEMALE">女</option>
                  </select>
                </label>
              </div>
              <label>
                <span>邮箱</span>
                <input type="email" required value={registerForm.email} onChange={(e) => updateRegister('email', e.target.value)} placeholder="name@example.com" />
              </label>
              <label>
                <span>密码</span>
                <input type="password" required minLength={8} maxLength={72} value={registerForm.password} onChange={(e) => updateRegister('password', e.target.value)} placeholder="至少 8 个字符" />
              </label>
              <div className="auth-grid three-columns">
                <label><span>年龄</span><input type="number" min="14" max="100" required value={registerForm.age} onChange={(e) => updateRegister('age', e.target.value)} /></label>
                <label><span>身高 cm</span><input type="number" min="100" max="250" step="0.1" required value={registerForm.heightCm} onChange={(e) => updateRegister('heightCm', e.target.value)} /></label>
                <label><span>体重 kg</span><input type="number" min="30" max="350" step="0.1" required value={registerForm.weightKg} onChange={(e) => updateRegister('weightKg', e.target.value)} /></label>
              </div>
              <label>
                <span>日常活动水平</span>
                <select value={registerForm.activityLevel} onChange={(e) => updateRegister('activityLevel', e.target.value as ActivityLevel)}>
                  <option value="SEDENTARY">久坐，几乎不运动</option>
                  <option value="LIGHT">轻度活动，每周 1–3 次</option>
                  <option value="MODERATE">中度活动，每周 3–5 次</option>
                  <option value="VERY_ACTIVE">高活动，每周 6–7 次</option>
                  <option value="EXTRA_ACTIVE">极高活动或体力工作</option>
                </select>
              </label>
              <div className="auth-grid two-columns">
                <label>
                  <span>目标</span>
                  <select value={registerForm.goal} onChange={(e) => updateRegister('goal', e.target.value as Goal)}>
                    <option value="FAT_LOSS">减脂</option>
                    <option value="MAINTAIN">维持</option>
                    <option value="LEAN_BULK">轻盈增肌</option>
                  </select>
                </label>
                <label>
                  <span>每周训练次数</span>
                  <input type="number" min="1" max="7" required value={registerForm.weeklyTrainingFrequency} onChange={(e) => updateRegister('weeklyTrainingFrequency', e.target.value)} />
                </label>
              </div>
            </>
          )}

          {error && <p className="form-error">{error}</p>}
          <button className="primary-button auth-submit" disabled={working} type="submit">
            {working ? '请稍候…' : mode === 'login' ? '登录 FitPilot' : '创建账户并计算目标'}
          </button>
        </form>
      </section>
    </main>
  )
}
