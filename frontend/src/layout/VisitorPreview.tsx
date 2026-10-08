import { useEffect, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { AuthContext, useAuth } from '../auth/AuthContext'
import { leavePreview, PREVIEW_PARAM, PREVIEW_VALUE, previewActive, withoutPreview } from '../preview'

/** 지금 화면이 방문자 화면 미리보기인지 (로그인한 회원만, FR-083) */
export function usePreview(): boolean {
  const { sessionMe } = useAuth()
  const location = useLocation()
  return sessionMe != null && previewActive(location.pathname, location.search)
}

/**
 * 방문자 화면 미리보기 범위 (FR-083, BR-44). 미리보기 중에는 화면 전체에 "로그인하지 않은 방문자"로 보이도록
 * me를 비워서 내려 준다(글쓰기·관리·댓글 쓰기 같은 주인·회원 버튼이 숨는다). 실제 로그인 회원은 sessionMe에 남는다.
 * 블로그·글 사이를 옮겨도 미리보기를 이어 가도록 주소에 view=visitor를 다시 붙인다.
 */
export function VisitorPreviewScope({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const preview = usePreview()

  useEffect(() => {
    if (!preview) return
    const params = new URLSearchParams(location.search)
    if (params.get(PREVIEW_PARAM) !== PREVIEW_VALUE) {
      params.set(PREVIEW_PARAM, PREVIEW_VALUE)
      navigate({ pathname: location.pathname, search: `?${params}`, hash: location.hash }, { replace: true })
    }
  }, [preview, location.pathname, location.search, location.hash, navigate])

  if (!preview) return <>{children}</>
  return <AuthContext.Provider value={{ ...auth, me: null, newCommentCount: 0 }}>{children}</AuthContext.Provider>
}

/** 미리보기 안내 띠: [내 화면으로 보기] [블로그 관리로 돌아가기] */
export function PreviewBanner() {
  const preview = usePreview()
  const { sessionMe } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  if (!preview) return null

  const exit = (to: string) => {
    leavePreview()
    navigate(to)
  }

  return (
    <div className="preview-banner" role="status">
      <div className="preview-inner">
        <span>
          <strong>방문자 화면 미리보기</strong> · 로그인하지 않은 방문자에게 보이는 모습입니다
        </span>
        <span className="preview-links">
          <button type="button" onClick={() => exit(location.pathname + withoutPreview(location.search) + location.hash)}>
            내 화면으로 보기
          </button>
          {sessionMe?.blogId && (
            <button type="button" onClick={() => exit('/manage')}>
              블로그 관리로 돌아가기
            </button>
          )}
        </span>
      </div>
    </div>
  )
}
