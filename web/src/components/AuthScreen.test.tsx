import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthScreen } from './AuthScreen'

afterEach(() => { vi.useRealTimers() })

describe('登录重试倒计时', () => {
  it('使用真实 API 错误解析显示 429 倒计时，到期允许重新提交', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ detail: '登录尝试过于频繁' }), {
      status: 429, headers: { 'Content-Type': 'application/json', 'Retry-After': '2' },
    }))
    vi.stubGlobal('fetch', fetch)
    render(<AuthScreen onAuthenticated={() => {}} />)
    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByRole('button', { name: '登录 FitPilot' }))
    expect(await screen.findByRole('status')).toHaveTextContent('2 秒')
    expect(screen.getByRole('button', { name: '2 秒后重试' })).toBeDisabled()
    expect(window.localStorage.getItem('fitpilot.accessToken')).toBeNull()

    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 2200)) })
    expect(screen.getByRole('button', { name: '登录 FitPilot' })).toBeEnabled()
    fireEvent.click(screen.getByRole('button', { name: '登录 FitPilot' }))
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2))
  })

  it('一个账号的冷却不阻止切换其他账号或注册', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      detail: '请稍后重试', retryAfterSeconds: 60,
    }), { status: 429, headers: { 'Content-Type': 'application/json' } })))
    render(<AuthScreen onAuthenticated={() => {}} />)
    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByRole('button', { name: '登录 FitPilot' }))
    await screen.findByRole('status')
    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'other@example.com' } })
    expect(screen.getByRole('button', { name: '登录 FitPilot' })).toBeEnabled()
    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'TEST@example.com' } })
    expect(screen.getByRole('button', { name: /秒后重试/ })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '创建账户' }))
    expect(screen.getByRole('button', { name: '创建账户并计算目标' })).toBeEnabled()
  })

  it('登录保护不可用时提示 503 并按 Retry-After 重试', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      detail: '登录服务暂时不可用，请稍后重试。',
    }), { status: 503, headers: { 'Content-Type': 'application/json', 'Retry-After': '5' } })))
    render(<AuthScreen onAuthenticated={() => {}} />)
    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByRole('button', { name: '登录 FitPilot' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('暂时不可用')
    expect(screen.getByRole('button', { name: '5 秒后重试' })).toBeDisabled()
  })
})
