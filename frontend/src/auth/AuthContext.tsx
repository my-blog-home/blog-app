import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { ApiError, get, post } from '../api/client'
import type { Me } from '../api/types'

interface AuthState {
  me: Me | null
  loading: boolean
  refresh: () => Promise<void>
  login: (email: string, password: string) => Promise<Me>
  logout: () => Promise<void>
  /** 로그인 창을 띄우고, 로그인하면 then을 실행한다 (CF-16-1) */
  requireLogin: (then?: () => void) => boolean
  loginPrompt: { open: boolean; then?: () => void }
  closeLoginPrompt: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)
  const [loginPrompt, setLoginPrompt] = useState<{ open: boolean; then?: () => void }>({ open: false })

  const refresh = useCallback(async () => {
    try {
      setMe(await get<Me>('/api/auth/me'))
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setMe(null)
      else throw e
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const login = async (email: string, password: string) => {
    const result = await post<Me>('/api/auth/login', { email, password })
    setMe(result)
    return result
  }

  const logout = async () => {
    await post('/api/auth/logout')
    setMe(null)
  }

  const requireLogin = (then?: () => void) => {
    if (me) return true
    setLoginPrompt({ open: true, then })
    return false
  }

  return (
    <AuthContext.Provider
      value={{
        me,
        loading,
        refresh,
        login,
        logout,
        requireLogin,
        loginPrompt,
        closeLoginPrompt: () => setLoginPrompt({ open: false }),
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const value = useContext(AuthContext)
  if (!value) throw new Error('AuthProvider 안에서 써야 합니다')
  return value
}
