import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, get } from '../api/client'
import type { NoticeDetail } from '../api/types'
import BackButton from '../components/BackButton'
import { NOTICE_TYPE_LABEL } from '../components/home/HomeSidePanels'
import PlainText from '../components/PlainText'
import { formatDate } from '../format'
import { M } from '../messages'
import NotFoundPage from './NotFoundPage'

/** 공지 · 이용 안내 한 건과 같은 종류의 다른 안내 (FR-077) */
export default function NoticeDetailPage() {
  const { noticeId } = useParams()
  const [notice, setNotice] = useState<NoticeDetail | null>(null)
  const [missing, setMissing] = useState(false)

  useEffect(() => {
    setNotice(null)
    setMissing(false)
    get<NoticeDetail>(`/api/notices/${noticeId}`)
      .then(setNotice)
      .catch((e) => e instanceof ApiError && e.status === 404 && setMissing(true))
  }, [noticeId])

  if (missing) return <NotFoundPage message={M.noticeNotFound} />
  if (!notice) return null
  const label = NOTICE_TYPE_LABEL[notice.type]

  return (
    <article className="reading notice-detail">
      <BackButton fallback="/notices" />
      <header className="article-head">
        <span className={notice.type === 'NOTICE' ? 'badge notice-badge' : 'badge guide-badge'}>{label}</span>
        <h1>{notice.title}</h1>
        <div className="meta">
          <span>{formatDate(notice.createdAt)}</span>
          {notice.updatedAt && <span>수정 {formatDate(notice.updatedAt)}</span>}
        </div>
      </header>
      <div className="markdown">
        <PlainText text={notice.content} />
      </div>
      {notice.others.length > 0 && (
        <section className="notice-others">
          <h2>다른 {label}</h2>
          <ul className="notice-rows">
            {notice.others.map((n) => (
              <li key={n.id}>
                <Link className="notice-row" to={`/notices/${n.id}`}>
                  <span className="notice-row-title">{n.title}</span>
                  <span className="faint notice-row-date">{formatDate(n.createdAt)}</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
      <p className="to-list">
        <Link to="/notices" className="button">
          목록으로
        </Link>
      </p>
    </article>
  )
}
