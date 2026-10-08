import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

/** 로그인 화면과 로그인 창이 함께 쓰는 입력 폼 (CF-02) */
export default function LoginForm({ onSuccess }: { onSuccess: () => void }) {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!email.trim() || !password) return
    setSubmitting(true)
    setError(null)
    try {
      await login(email, password)
      onSuccess()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '잠시 뒤 다시 시도해 주세요')
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>
        이메일
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
      </label>
      <label>
        비밀번호
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" className="primary" disabled={submitting || !email.trim() || !password}>
        로그인
      </button>
      <p className="muted small">
        <Link to="/password-reset">비밀번호를 잊으셨나요?</Link> · <Link to="/signup">회원가입</Link>
      </p>
    </form>
  )
}
