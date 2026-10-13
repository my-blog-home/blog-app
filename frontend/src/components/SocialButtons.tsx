import { useEffect, useState } from 'react'
import { get } from '../api/client'

const LABEL: Record<string, string> = { github: 'GitHub로 계속하기', kakao: '카카오로 계속하기' }

/**
 * 소셜 로그인 버튼. 서버에 설정된 제공자만 보이고, 없으면 아무것도 그리지 않는다.
 * 누르면 서버의 /oauth2/authorization/{제공자}로 가서 제공자 로그인 화면으로 넘어간다
 */
export default function SocialButtons() {
  const [providers, setProviders] = useState<string[]>([])

  useEffect(() => {
    get<{ providers: string[] }>('/api/auth/social/providers')
      .then((r) => setProviders(r.providers))
      .catch(() => setProviders([]))
  }, [])

  if (providers.length === 0) return null
  return (
    <div className="social-login">
      <p className="social-divider">
        <span>또는</span>
      </p>
      {providers.map((p) => (
        <a key={p} href={`/oauth2/authorization/${p}`} className={`button social-button social-${p}`}>
          {LABEL[p] ?? p}
        </a>
      ))}
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
