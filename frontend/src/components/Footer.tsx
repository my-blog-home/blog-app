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
