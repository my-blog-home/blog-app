import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../api/client'

interface VersionInfo {
  version: string
  releaseNoticeId: number | null
}

/** 바닥글: 지금 서비스 버전과 그 버전의 릴리스 노트 */
export default function Footer() {
  const [info, setInfo] = useState<VersionInfo | null>(null)

  useEffect(() => {
    get<VersionInfo>('/api/config/version').then(setInfo).catch(() => setInfo(null))
  }, [])

  return (
    <footer className="footer">
      <div className="footer-inner">
        <span>Ylog · 내 블로그</span>
        <nav className="footer-links" aria-label="정책">
          <Link to="/policies/terms">이용약관</Link>
          <Link to="/policies/privacy">
            <strong>개인정보처리방침</strong>
          </Link>
          <Link to="/policies/operation">운영정책</Link>
        </nav>
        {info && (
          <span className="footer-version">
            v{info.version}
            {info.releaseNoticeId && (
              <>
                {' · '}
                <Link to={`/notices/${info.releaseNoticeId}`}>릴리스 노트</Link>
              </>
            )}
          </span>
        )}
      </div>
    </footer>
  )
}
