import { useEffect, useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { ApiError, post } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { M } from '../messages'
import { passwordChecks } from './SignupPage'
import { useDocumentMeta } from '../meta'

const RESET_NOTICE = '입력하신 이메일로 안내를 보냈습니다. 10분 안에 인증번호를 입력해 주세요'

/** 비밀번호 찾기. 가입 여부와 관계없이 같은 안내를 보여 준다 (CF-25) */
export default function PasswordResetPage() {
  const { me } = useAuth()
  const navigate = useNavigate()
  useDocumentMeta('비밀번호 찾기')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [step, setStep] = useState<'email' | 'code' | 'password'>('email')
  const [cooldown, setCooldown] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [testCode, setTestCode] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown(cooldown - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  // 로그인한 상태에서는 마이페이지의 비밀번호 변경을 쓴다 (CF-25-1)
  if (me) return <Navigate to="/" replace />

  const run = async (action: () => Promise<void>) => {
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(e instanceof ApiError ? e.fieldErrors[0]?.message ?? e.message : '잠시 뒤 다시 시도해 주세요')
    }
  }

  const requestCode = () =>
    run(async () => {
      const res = await post<{ testCode?: string } | undefined>('/api/auth/password-reset/verification', { email })
      setStep('code')
      setCode('')
      setCooldown(60)
      setNotice(RESET_NOTICE)
      setTestCode(res?.testCode ?? null)
    })

  const confirmCode = () =>
    run(async () => {
      await post('/api/auth/password-reset/verification/confirm', { email, code })
      setStep('password')
      setNotice(M.verified)
    })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    run(async () => {
      await post('/api/auth/password-reset', { email, newPassword: password, newPasswordConfirm: confirm })
      navigate('/login', { replace: true, state: { notice: '비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요' } })
    })
  }

  const passwordOk = passwordChecks(password).every((c) => c.ok) && password === confirm

  return (
    <section className="narrow">
      <h1>비밀번호 찾기</h1>
      <form className="form" onSubmit={submit}>
        <label>
          가입한 이메일
          <div className="row">
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} readOnly={step === 'password'} />
            {step !== 'password' && (
              <button type="button" onClick={requestCode} disabled={!email.trim() || cooldown > 0}>
                {step === 'code' ? (cooldown > 0 ? `다시 받기 (${cooldown}초)` : '다시 받기') : '인증번호 받기'}
              </button>
            )}
          </div>
        </label>
        {step === 'code' && (
          <label>
            인증번호
            <div className="row">
              <input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} maxLength={6} autoComplete="one-time-code" />
              <button type="button" onClick={confirmCode} disabled={code.length !== 6}>
                확인
              </button>
            </div>
          </label>
        )}
        {notice && <p className="notice">{notice}</p>}
        {testCode && step === 'code' && (
          <p className="test-code">
            테스트 모드: 인증번호 <strong>{testCode}</strong> <span className="faint">(메일 대신 화면에 보여 줍니다)</span>
          </p>
        )}
        {step === 'password' && (
          <>
            <label>
              새 비밀번호
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
              <span className="checks">
                {passwordChecks(password).map((c) => (
                  <span key={c.label} className={c.ok ? 'ok' : ''}>
                    {c.ok ? '✓' : '·'} {c.label}
                  </span>
                ))}
              </span>
            </label>
            <label>
              새 비밀번호 확인
              <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" />
              {confirm && password !== confirm && <span className="hint error">{M.passwordMismatch}</span>}
            </label>
            <button className="primary" disabled={!passwordOk}>
              변경하기
            </button>
          </>
        )}
        {error && <p className="error">{error}</p>}
      </form>
    </section>
  )
}
