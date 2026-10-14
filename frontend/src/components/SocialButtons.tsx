import { useEffect, useState } from 'react'
import { get } from '../api/client'

/** 늘 보이는 소셜 로그인 버튼 */
const ALL = [
  { id: 'github', label: 'GitHub로 계속하기' },
  { id: 'kakao', label: '카카오로 계속하기' },
]

const PREPARING = '준비 중입니다'

/**
 * 소셜 로그인 버튼. 서버에 설정된 제공자는 누르면 서버의 /oauth2/authorization/{제공자}로 가서 제공자 로그인 화면으로 넘어가고,
 * 아직 설정되지 않은 제공자는 버튼만 보이고 마우스를 올리거나(휴대폰은 누르면) "준비 중입니다"를 띄운다.
 */
export default function SocialButtons() {
  const [enabled, setEnabled] = useState<string[]>([])
  const [tapped, setTapped] = useState<string | null>(null)

  useEffect(() => {
    get<{ providers: string[] }>('/api/auth/social/providers')
      .then((r) => setEnabled(r.providers))
      .catch(() => setEnabled([]))
  }, [])

  useEffect(() => {
    if (!tapped) return
    const timer = window.setTimeout(() => setTapped(null), 2000)
    return () => window.clearTimeout(timer)
  }, [tapped])

  return (
    <div className="social-login">
      <p className="social-divider">
        <span>또는</span>
      </p>
      {ALL.map((p) =>
        enabled.includes(p.id) ? (
          <a key={p.id} href={`/oauth2/authorization/${p.id}`} className={`button social-button social-${p.id}`}>
            {p.label}
          </a>
        ) : (
          <span key={p.id} className="social-preparing">
            <button
              type="button"
              className={`social-button social-${p.id} is-preparing`}
              aria-disabled="true"
              aria-describedby={`social-tip-${p.id}`}
              onClick={() => setTapped(p.id)}
            >
              {p.label}
            </button>
            <span id={`social-tip-${p.id}`} role="tooltip" className={`social-tip ${tapped === p.id ? 'show' : ''}`}>
              {PREPARING}
            </span>
          </span>
        ),
      )}
    </div>
  )
}

/** 소셜 로그인이 거절됐을 때(주소의 socialError) 보여 줄 문구 */
export const SOCIAL_ERRORS: Record<string, string> = {
  email: '소셜 계정에서 인증된 이메일을 받지 못했어요. 이메일 제공에 동의했는지 확인하거나 이메일로 가입해 주세요.',
  suspended: '이용이 정지된 계정이에요. 이메일로 로그인하면 정지 안내를 볼 수 있어요.',
  rejoin: '탈퇴한 지 30일이 지나지 않은 이메일이에요. 30일이 지난 뒤 다시 가입할 수 있어요.',
  admin: '관리자 계정은 소셜 로그인으로 연결할 수 없어요. 이메일로 로그인해 주세요.',
  failed: '소셜 로그인 중 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요.',
}
