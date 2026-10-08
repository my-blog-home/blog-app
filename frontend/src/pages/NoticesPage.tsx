import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { get } from '../api/client'
import type { NoticePage, NoticeType } from '../api/types'
import BackButton from '../components/BackButton'
import NoticeBadge from '../components/NoticeBadge'
import Pagination from '../components/Pagination'
import { formatDate } from '../format'
import { M } from '../messages'
import { useDocumentMeta } from '../meta'

const TABS: { type: NoticeType | null; label: string }[] = [
  { type: null, label: '전체' },
  { type: 'NOTICE', label: '공지' },
  { type: 'GUIDE', label: '이용법' },
]

/** 공지 · 이용 안내 목록: 종류 탭(전체·공지·이용법), 고정 글이 위로 그다음 최신순, 10개씩 (FR-077, BR-30) */
export default function NoticesPage() {
  const [params, setParams] = useSearchParams()
  const rawType = params.get('type')
  const type: NoticeType | null = rawType === 'NOTICE' || rawType === 'GUIDE' ? rawType : null
  const page = Number(params.get('page') ?? '1')
  const [result, setResult] = useState<NoticePage | null>(null)
  useDocumentMeta('공지사항')

  useEffect(() => {
    const q = new URLSearchParams({ page: String(page) })
    if (type) q.set('type', type)
    get<NoticePage>(`/api/notices?${q}`).then(setResult)
  }, [type, page])

  const go = (next: { type?: NoticeType | null; page?: number }) => {
    const q = new URLSearchParams()
    const t = next.type !== undefined ? next.type : type
    if (t) q.set('type', t)
    if (next.page && next.page > 1) q.set('page', String(next.page))
    setParams(q)
  }

  return (
    <section className="notice-page">
      <BackButton fallback="/" />
      <h1>공지 · 이용 안내</h1>
      <div className="tabs" role="tablist" aria-label="안내 종류">
        {TABS.map((t) => (
          <button
            key={t.label}
            type="button"
            role="tab"
            aria-selected={type === t.type}
            className={type === t.type ? 'current' : ''}
            onClick={() => go({ type: t.type, page: 1 })}
          >
            {t.label}
          </button>
        ))}
      </div>
      {result &&
        (result.items.length === 0 ? (
          <p className="empty">{M.noNotices}</p>
        ) : (
          <ul className="notice-rows">
            {result.items.map((n) => (
              <li key={n.id}>
                <Link className="notice-row" to={`/notices/${n.id}`}>
                  <NoticeBadge notice={n} />
                  {n.pinned && <span className="pin" aria-label="고정">📌</span>}
                  <span className="notice-row-title">{n.title}</span>
                  <span className="faint notice-row-date">{formatDate(n.createdAt)}</span>
                </Link>
              </li>
            ))}
          </ul>
        ))}
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => go({ page: p })} />}
    </section>
  )
}
