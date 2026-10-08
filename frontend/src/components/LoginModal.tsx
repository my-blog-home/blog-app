import { useAuth } from '../auth/AuthContext'
import LoginForm from './LoginForm'
import { useDialog } from './useDialog'

/** 회원 전용 기능을 누른 비회원에게 띄우는 로그인 창. 로그인하면 하려던 일을 이어서 한다 (CF-16-1). Esc로 닫는다 (NFR-08) */
export default function LoginModal() {
  const { loginPrompt } = useAuth()
  if (!loginPrompt.open) return null
  return <LoginDialog />
}

function LoginDialog() {
  const { loginPrompt, closeLoginPrompt } = useAuth()
  const ref = useDialog<HTMLDivElement>(closeLoginPrompt)
  const then = loginPrompt.then
  return (
    <div className="modal-backdrop" onClick={closeLoginPrompt}>
      <div ref={ref} className="modal" role="dialog" aria-modal="true" aria-label="로그인" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h2>로그인</h2>
          <button type="button" className="link" onClick={closeLoginPrompt} aria-label="닫기">
            ✕
          </button>
        </div>
        <LoginForm
          onSuccess={() => {
            closeLoginPrompt()
            then?.()
          }}
        />
      </div>
    </div>
  )
}
