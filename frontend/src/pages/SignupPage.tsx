import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, post } from '../api/client'
import { M } from '../messages'

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const NICKNAME = /^[가-힣A-Za-z0-9]{2,10}$/
const SPECIALS = /[!@#$%^&*()_+\-=]/
const ALLOWED = /^[A-Za-z\d!@#$%^&*()_+\-=]*$/

/** 비밀번호 규칙을 입력하는 동안 바로 보여 준다 (CF-01-6) */
export function passwordChecks(pw: string) {
  return [
    { label: '8자 이상', ok: pw.length >= 8 && pw.length <= 64 && ALLOWED.test(pw) },
    { label: '영문', ok: /[A-Za-z]/.test(pw) },
    { label: '숫자', ok: /\d/.test(pw) },
    { label: '특수문자', ok: SPECIALS.test(pw) },
  ]
}

type Field = 'nickname' | 'email' | 'code' | 'password' | 'passwordConfirm'

/** 가입 화면 한 곳에서 이메일 인증을 먼저 마치고 가입한다 (CF-01) */
export default function SignupPage() {
  const navigate = useNavigate()
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [verified, setVerified] = useState(false)
  const [cooldown, setCooldown] = useState(0)
  const [errors, setErrors] = useState<Partial<Record<Field | 'form', string>>>({})
  const [notice, setNotice] = useState<string | null>(null)
  // 메일 계정을 정하기 전 시험 모드에서만 서버가 인증번호를 돌려준다
  const [testCode, setTestCode] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const refs = {
    nickname: useRef<HTMLInputElement>(null),
    email: useRef<HTMLInputElement>(null),
    code: useRef<HTMLInputElement>(null),
    password: useRef<HTMLInputElement>(null),
    passwordConfirm: useRef<HTMLInputElement>(null),
  }

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown(cooldown - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  const nicknameOk = NICKNAME.test(nickname)
  const emailOk = EMAIL.test(email.trim())
  const checks = passwordChecks(password)
  const passwordOk = checks.every((c) => c.ok)
  const confirmOk = password === passwordConfirm && passwordConfirm.length > 0
  const canSubmit = verified && nicknameOk && passwordOk && confirmOk && !busy

  /** 어긴 칸마다 이유를 보여 주고 맨 위 칸으로 커서를 옮긴다. 비밀번호 칸만 비운다 (CF-01-7) */
  const showError = (err: unknown, fallbackField: Field | 'form' = 'form') => {
    if (!(err instanceof ApiError)) {
      setErrors({ form: '잠시 뒤 다시 시도해 주세요' })
      return
    }
    const next: Partial<Record<Field | 'form', string>> = {}
    if (err.fieldErrors.length > 0) {
      err.fieldErrors.forEach((f) => (next[f.field as Field] = f.message))
    } else {
      next[fallbackField] = err.message
    }
    setErrors(next)
    const order: Field[] = ['nickname', 'email', 'code', 'password', 'passwordConfirm']
    const first = order.find((f) => next[f])
    if (first) refs[first].current?.focus()
    if (next.password || next.passwordConfirm) {
      setPassword('')
      setPasswordConfirm('')
    }
  }

  const requestCode = async () => {
    setBusy(true)
    setErrors({})
    setNotice(null)
    try {
      const res = await post<{ testCode?: string } | undefined>('/api/auth/signup/verification', { nickname, email })
      setCodeSent(true)
      setCode('')
      setCooldown(60)
      setNotice(M.codeSent)
      setTestCode(res?.testCode ?? null)
      refs.code.current?.focus()
    } catch (err) {
      showError(err, 'email')
    } finally {
      setBusy(false)
    }
  }

  const confirmCode = async () => {
    setBusy(true)
    setErrors({})
    try {
      await post('/api/auth/signup/verification/confirm', { email, code })
      setVerified(true)
      setNotice(M.verified)
    } catch (err) {
      showError(err, 'code')
    } finally {
      setBusy(false)
    }
  }

  /** 인증 후 이메일을 바꾸면 인증이 취소된다 (CF-01-19) */
  const changeEmail = () => {
    setVerified(false)
    setCodeSent(false)
    setCode('')
    setNotice(null)
    refs.email.current?.focus()
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!canSubmit) return
    setBusy(true)
    setErrors({})
    try {
      await post('/api/auth/signup', { nickname, email, password, passwordConfirm })
      navigate('/login', { replace: true, state: { notice: M.signupDone } })
    } catch (err) {
      if (err instanceof ApiError && err.code === 'NOT_VERIFIED') {
        setVerified(false)
        setCodeSent(false)
      }
      showError(err, 'email')
      setBusy(false)
    }
  }

  return (
    <section className="narrow">
      <h1>회원가입</h1>
      <form className="form" onSubmit={submit} noValidate>
        <label>
          닉네임
          <input ref={refs.nickname} value={nickname} onChange={(e) => setNickname(e.target.value)} maxLength={10} />
          {nickname && !nicknameOk && <span className="hint error">{M.nicknameRule}</span>}
          {errors.nickname && <span className="hint error">{errors.nickname}</span>}
        </label>

        <label>
          이메일
          <div className="row">
            <input
              ref={refs.email}
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              readOnly={verified}
              autoComplete="email"
            />
            {verified ? (
              <button type="button" onClick={changeEmail}>
                이메일 변경
              </button>
            ) : (
              <button type="button" onClick={requestCode} disabled={!nicknameOk || !emailOk || cooldown > 0 || busy}>
                {codeSent ? (cooldown > 0 ? `다시 받기 (${cooldown}초)` : '인증번호 다시 받기') : '인증번호 받기'}
              </button>
            )}
          </div>
          {email && !emailOk && <span className="hint error">{M.emailFormat}</span>}
          {errors.email && <span className="hint error">{errors.email}</span>}
        </label>

        {codeSent && !verified && (
          <label>
            인증번호
            <div className="row">
              <input ref={refs.code} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} maxLength={6} autoComplete="one-time-code" />
              <button type="button" onClick={confirmCode} disabled={code.length !== 6 || busy}>
                확인
              </button>
            </div>
            {errors.code && <span className="hint error">{errors.code}</span>}
          </label>
        )}
        {notice && <p className="notice">{notice}</p>}
        {testCode && !verified && (
          <p className="test-code">
            테스트 모드: 인증번호 <strong>{testCode}</strong> <span className="faint">(메일 대신 화면에 보여 줍니다)</span>
          </p>
        )}

        <label>
          비밀번호
          <input ref={refs.password} type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
          <span className="checks">
            {checks.map((c) => (
              <span key={c.label} className={c.ok ? 'ok' : ''}>
                {c.ok ? '✓' : '·'} {c.label}
              </span>
            ))}
          </span>
          {errors.password && <span className="hint error">{errors.password}</span>}
        </label>

        <label>
          비밀번호 확인
          <input
            ref={refs.passwordConfirm}
            type="password"
            value={passwordConfirm}
            onChange={(e) => setPasswordConfirm(e.target.value)}
            autoComplete="new-password"
          />
          {passwordConfirm && !confirmOk && <span className="hint error">{M.passwordMismatch}</span>}
          {errors.passwordConfirm && <span className="hint error">{errors.passwordConfirm}</span>}
        </label>

        {errors.form && <p className="error">{errors.form}</p>}
        <button type="submit" className="primary" disabled={!canSubmit}>
          가입하기
        </button>
      </form>
    </section>
  )
}
