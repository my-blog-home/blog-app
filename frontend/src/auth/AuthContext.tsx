import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { ApiError, get, post } from '../api/client'
import type { Me } from '../api/types'

export interface AuthState {
  /** 화면에 쓰는 로그인 회원. 방문자 화면 미리보기 중에는 비어 있다 (FR-083) */
  me: Me | null
  /** 미리보기와 관계없이 실제로 로그인한 회원 */
  sessionMe: Me | null
  newCommentCount: number
  refreshNewComments: () => Promise<void>
  loading: boolean
  refresh: () => Promise<void>
  login: (email: string, password: string) => Promise<Me>
  logout: () => Promise<void>
  /** 로그인 창을 띄우고, 로그인하면 then을 실행한다 (CF-16-1) */
  requireLogin: (then?: () => void) => boolean
  loginPrompt: { open: boolean; then?: () => void }
  closeLoginPrompt: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)
  const [newCommentCount, setNewCommentCount] = useState(0)
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

  // 새 댓글 수는 사용자 메뉴와 관리 메뉴에 보여 준다 (BM-05-5)
  const refreshNewComments = useCallback(async () => {
    if (!me) {
      setNewCommentCount(0)
      return
    }
    const result = await get<{ count: number }>('/api/manage/new-comments')
    setNewCommentCount(result.count)
  }, [me])

  useEffect(() => {
    refreshNewComments()
  }, [refreshNewComments])

  const login = async (email: string, password: string) => {
    const result = await post<Me>('/api/auth/login', { email, password })
    setMe(result)
    return result
  }

  const logout = async () => {
    await post('/api/auth/logout')
    setMe(null)
  }

  // 화면의 effect가 기대므로 me가 바뀔 때만 새로 만든다
  const requireLogin = useCallback(
    (then?: () => void) => {
      if (me) return true
      setLoginPrompt({ open: true, then })
      return false
    },
    [me],
  )

  return (
    <AuthContext.Provider
      value={{
        me,
        sessionMe: me,
        newCommentCount,
        refreshNewComments,
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
