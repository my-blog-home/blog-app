import { useEffect, useState, type FormEvent } from 'react'
import { Link, useBlocker, useNavigate } from 'react-router-dom'
import { ApiError, del, get, patch, put } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { PROFILE_COLORS } from '../colors'
import { formatDate } from '../format'
import { M } from '../messages'
import { passwordChecks } from './SignupPage'

interface MyInfo {
  email: string
  nickname: string
  bio: string | null
  profileColor: string
  joinedAt: string
  blogId: number | null
}

type Tab = 'info' | 'password' | 'withdraw'

const errorOf = (e: unknown) => (e instanceof ApiError ? e.fieldErrors[0]?.message ?? e.message : '잠시 뒤 다시 시도해 주세요')

/** 마이페이지: 내 정보, 비밀번호 변경, 회원 탈퇴, 내 블로그 바로가기 (CF-15) */
export default function MyPage() {
  const { me, loading, requireLogin, refresh } = useAuth()
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('info')
  const [info, setInfo] = useState<MyInfo | null>(null)

  useEffect(() => {
    if (loading) return
    if (!me) {
      requireLogin()
      return
    }
    get<MyInfo>('/api/me').then(setInfo)
  }, [loading, me, requireLogin])

  if (!me) return <p className="empty">로그인이 필요합니다</p>
  if (!info) return null

  return (
    <section className="narrow">
      <h1>마이페이지</h1>
      <div className="tabs">
        <button className={tab === 'info' ? 'current' : ''} onClick={() => setTab('info')}>
          내 정보
        </button>
        <button className={tab === 'password' ? 'current' : ''} onClick={() => setTab('password')}>
          비밀번호 변경
        </button>
        <button className={tab === 'withdraw' ? 'current' : ''} onClick={() => setTab('withdraw')}>
          회원 탈퇴
        </button>
      </div>
      {tab === 'info' && <InfoForm info={info} onSaved={(next) => { setInfo(next); refresh() }} />}
      {tab === 'password' && <PasswordForm />}
      {tab === 'withdraw' && (
        <WithdrawForm
          onDone={async () => {
            await refresh()
            navigate('/', { replace: true, state: { notice: '탈퇴가 완료되었습니다' } })
          }}
        />
      )}
      {info.blogId && (
        <p className="muted small">
          <Link to={`/blogs/${info.blogId}`}>내 블로그 바로가기</Link>
        </p>
      )}
    </section>
  )
}

function InfoForm({ info, onSaved }: { info: MyInfo; onSaved: (info: MyInfo) => void }) {
  const [nickname, setNickname] = useState(info.nickname)
  const [bio, setBio] = useState(info.bio ?? '')
  const [profileColor, setProfileColor] = useState(info.profileColor)
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const changed = nickname !== info.nickname || bio !== (info.bio ?? '') || profileColor !== info.profileColor

  // 수정하던 중 나가려 하면 묻는다 (CF-15-7)
  const blocker = useBlocker(({ currentLocation, nextLocation }) => changed && currentLocation.pathname !== nextLocation.pathname)
  useEffect(() => {
    if (blocker.state === 'blocked') {
      if (confirm(M.leaveConfirm)) blocker.proceed()
      else blocker.reset()
    }
  }, [blocker])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    try {
      onSaved(await patch<MyInfo>('/api/me', { nickname, bio, profileColor }))
      setMessage({ ok: true, text: '저장했습니다' })
    } catch (err) {
      setMessage({ ok: false, text: errorOf(err) })
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      {/* 프로필 색: 6가지 중 하나. 아바타 바탕색으로 쓴다 (FR-05) */}
      <fieldset className="color-field">
        <legend>프로필 색상</legend>
        <span className="avatar sm" style={{ background: profileColor }} aria-hidden="true">
          {[...(nickname || info.nickname)][0]}
        </span>
        <span className="swatches" role="radiogroup" aria-label="프로필 색상">
          {PROFILE_COLORS.map((color, i) => (
            <button
              key={color}
              type="button"
              role="radio"
              aria-checked={profileColor === color}
              aria-label={`색 ${i + 1}`}
              className={profileColor === color ? 'swatch current' : 'swatch'}
              style={{ background: color }}
              onClick={() => setProfileColor(color)}
            />
          ))}
        </span>
      </fieldset>
      <label>
        이메일
        <input value={info.email} readOnly />
      </label>
      <label>
        닉네임
        <input value={nickname} onChange={(e) => setNickname(e.target.value)} maxLength={10} />
      </label>
      <label>
        소개
        <textarea value={bio} onChange={(e) => setBio(e.target.value)} maxLength={100} rows={3} />
        <span className="hint muted">{[...bio].length}/100</span>
      </label>
      <p className="muted small">가입일 {formatDate(info.joinedAt)}</p>
      {message && <p className={message.ok ? 'notice' : 'error'}>{message.text}</p>}
      <button className="primary" disabled={!changed}>
        저장
      </button>
    </form>
  )
}

function PasswordForm() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const ok = passwordChecks(next).every((c) => c.ok) && next === confirm && current.length > 0

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    try {
      await put('/api/me/password', { currentPassword: current, newPassword: next, newPasswordConfirm: confirm })
      setMessage({ ok: true, text: '비밀번호를 변경했습니다' })
    } catch (err) {
      setMessage({ ok: false, text: errorOf(err) })
    }
    setCurrent('')
    setNext('')
    setConfirm('')
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>
        현재 비밀번호
        <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" />
      </label>
      <label>
        새 비밀번호
        <input type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" />
        <span className="checks">
          {passwordChecks(next).map((c) => (
            <span key={c.label} className={c.ok ? 'ok' : ''}>
              {c.ok ? '✓' : '·'} {c.label}
            </span>
          ))}
        </span>
      </label>
      <label>
        새 비밀번호 확인
        <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" />
        {confirm && next !== confirm && <span className="hint error">{M.passwordMismatch}</span>}
      </label>
      {message && <p className={message.ok ? 'notice' : 'error'}>{message.text}</p>}
      <button className="primary" disabled={!ok}>
        변경하기
      </button>
    </form>
  )
}

function WithdrawForm({ onDone }: { onDone: () => void }) {
  const [password, setPassword] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!confirm('정말 탈퇴하시겠습니까? 이 작업은 되돌릴 수 없습니다')) return
    try {
      await del('/api/me', { password, agreed })
      onDone()
    } catch (err) {
      setError(errorOf(err))
      setPassword('')
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <div className="warning">
        <p>탈퇴하면 되돌릴 수 없습니다.</p>
        <p>
          <strong>삭제되는 것</strong>: 내 블로그, 글, 분류, 내 블로그에 달린 댓글, 내가 누른 좋아요
        </p>
        <p>
          <strong>남는 것</strong>: 다른 사람 글에 단 댓글 (작성자는 "탈퇴한 사용자"로 표시)
        </p>
      </div>
      <label>
        비밀번호
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
      </label>
      <label className="check">
        <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} /> 안내를 읽었고 동의합니다
      </label>
      {error && <p className="error">{error}</p>}
      <button className="primary danger-bg" disabled={!agreed || !password}>
        탈퇴하기
      </button>
    </form>
  )
}
