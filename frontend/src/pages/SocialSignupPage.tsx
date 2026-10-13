import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError, get, post } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useDocumentMeta } from '../meta'

interface Pending {
  provider: string
  /** 카카오처럼 이메일을 주지 않는 제공자면 null */
  email: string | null
  suggestedNickname: string
}

const PROVIDER_NAME: Record<string, string> = { github: 'GitHub', kakao: '카카오' }
const NICKNAME = /^[가-힣A-Za-z0-9]{2,10}$/

/**
 * 소셜 로그인으로 처음 온 사람의 가입 마무리: 닉네임과 약관 동의만 받는다(이메일은 제공자가 인증한 것을 쓰고 비밀번호는 없다).
 * 마치면 이메일 가입과 같이 내 블로그가 만들어지고 로그인된다.
 */
export default function SocialSignupPage() {
  const { me, refresh } = useAuth()
  const navigate = useNavigate()
  useDocumentMeta('소셜 계정으로 가입')
  const [pending, setPending] = useState<Pending | null>(null)
  const [missing, setMissing] = useState(false)
  const [nickname, setNickname] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    get<Pending>('/api/auth/social/pending')
      .then((p) => {
        setPending(p)
        setNickname(p.suggestedNickname)
      })
      .catch(() => setMissing(true))
  }, [])

  if (me) return <Navigate to="/" replace />
  if (missing)
    return (
      <section className="narrow">
        <h1>소셜 계정으로 가입</h1>
        <p className="empty">가입 정보가 없거나 시간이 지났어요. 로그인 화면에서 소셜 로그인을 다시 해 주세요.</p>
        <p className="auth-foot">
          <Link to="/login">로그인으로</Link>
        </p>
      </section>
    )
  if (!pending) return null

  const nicknameOk = NICKNAME.test(nickname)
  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!nicknameOk || !agreed) return
    setBusy(true)
    setError(null)
    try {
      await post('/api/auth/social/complete', { nickname, agreed })
      await refresh()
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.fieldErrors[0]?.message ?? err.message : '잠시 뒤 다시 시도해 주세요')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="narrow">
      <h1>소셜 계정으로 가입</h1>
      <p className="muted" style={{ textAlign: 'center', marginTop: -12 }}>
        {PROVIDER_NAME[pending.provider] ?? pending.provider} 계정으로 처음 오셨어요. 닉네임만 정하면 가입이 끝나요.
      </p>
      <form className="form" onSubmit={submit}>
        {pending.email ? (
          <label>
            이메일
            <input value={pending.email} disabled />
          </label>
        ) : (
          <p className="muted small">이메일 없이 {PROVIDER_NAME[pending.provider] ?? pending.provider} 계정으로 가입해요. 다음부터 같은 계정으로 로그인하면 돼요.</p>
        )}
        <label>
          닉네임
          <input value={nickname} onChange={(e) => setNickname(e.target.value)} placeholder="한글·영문·숫자 2~10자" autoFocus />
          {nickname && !nicknameOk && <span className="hint error">한글·영문·숫자 2~10자로 정해 주세요</span>}
        </label>
        <label className="check">
          <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />
          <span>
            <Link to="/policies/terms" target="_blank">
              이용약관
            </Link>
            과{' '}
            <Link to="/policies/privacy" target="_blank">
              개인정보처리방침
            </Link>
            에 동의합니다 (필수)
          </span>
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" className="primary" disabled={busy || !nicknameOk || !agreed}>
          가입하기
        </button>
        {pending.email && <p className="auth-foot">비밀번호 없이 가입돼요. 나중에 이메일로도 로그인하려면 비밀번호 찾기에서 정할 수 있어요.</p>}
      </form>
    </section>
  )
}
