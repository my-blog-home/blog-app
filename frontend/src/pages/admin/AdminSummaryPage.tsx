import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { AdminSummary } from '../../api/adminTypes'
import { get } from '../../api/client'
import { formatDateTime } from '../../format'
import { errorOf, short, targetKind } from './adminShared'

/** 관리자 요약: 상태별 신고 수, 공지 수, 처리 대기 신고 5줄 (FR-079) */
export default function AdminSummaryPage() {
  const [data, setData] = useState<AdminSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    get<AdminSummary>('/api/admin/summary').then(setData).catch((e) => setError(errorOf(e)))
  }, [])

  if (error) return <p className="empty">{error}</p>
  if (!data) return null
  return (
    <>
      <h1>요약</h1>
      <div className="stat-tiles admin-tiles">
        <Link to="/admin/reports?status=PENDING" className={`tile ${data.counts.PENDING > 0 ? 'highlight' : ''}`}>
          <span className="small">처리 대기 신고</span>
          <strong className="big">{data.counts.PENDING.toLocaleString()}</strong>
        </Link>
        <Link to="/admin/reports?status=RESOLVED" className="tile">
          <span className="small">처리 완료 신고</span>
          <strong className="big">{data.counts.RESOLVED.toLocaleString()}</strong>
        </Link>
        <Link to="/admin/reports?status=REJECTED" className="tile">
          <span className="small">반려한 신고</span>
          <strong className="big">{data.counts.REJECTED.toLocaleString()}</strong>
        </Link>
        <Link to="/admin/notices" className="tile">
          <span className="small">공지 · 이용 안내</span>
          <strong className="big">{data.noticeCount.toLocaleString()}</strong>
        </Link>
      </div>
      <div className="panel-card">
        <h2>
          처리 대기 신고 <Link to="/admin/reports">신고 처리 →</Link>
        </h2>
        {data.latestPending.length === 0 ? (
          <p className="muted small">처리 대기 중인 신고가 없습니다</p>
        ) : (
          <ul className="simple-list">
            {data.latestPending.map((g) => (
              <li key={g.key}>
                <span className="badge">{targetKind(g)}</span>
                <Link to="/admin/reports">
                  {g.reasonSummary}
                  {g.reportCount > 1 ? ` · ${g.reportCount}건` : ''} · {short(g.targetText, 40)}
                </Link>
                <span className="muted">{formatDateTime(g.latestAt)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  )
}
