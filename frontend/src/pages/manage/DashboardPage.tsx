import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../../api/client'
import LineChart, { SERIES_COLORS } from '../../components/LineChart'
import { formatDate } from '../../format'
import { useManage } from './ManageLayout'

interface Counts {
  today: number
  yesterday: number
  total: number
}

export interface Daily {
  date: string
  views: number
  visitors: number
  comments: number
}

interface Dashboard {
  views: Counts
  visitors: Counts
  newCommentCount: number
  daily30: Daily[]
  popular7: { postId: number; title: string; views: number }[]
  recent: { postId: number; title: string; createdAt: string; visibility: string }[]
}

export const shortDate = (iso: string) => iso.slice(5).replace('-', '.')

/** 대시보드: 숫자, 30일 그래프, 인기 글, 최근 글 (BM-02) */
export default function DashboardPage() {
  const { blog } = useManage()
  const [data, setData] = useState<Dashboard | null>(null)

  useEffect(() => {
    get<Dashboard>(`/api/manage/blogs/${blog.id}/dashboard`).then(setData)
  }, [blog.id])

  if (!data) return null
  return (
    <>
      <h1>대시보드</h1>
      <div className="stat-tiles">
        <StatTile label="조회수" counts={data.views} />
        <StatTile label="방문자" counts={data.visitors} />
        <div className={`tile ${data.newCommentCount > 0 ? 'highlight' : ''}`}>
          <span className="muted small">새 댓글</span>
          <strong className="big">{data.newCommentCount}</strong>
          <Link to="/manage/comments" className="small">
            댓글 보기
          </Link>
        </div>
      </div>
      <h2>최근 30일</h2>
      <LineChart
        labels={data.daily30.map((d) => shortDate(d.date))}
        series={[
          { name: '조회수', color: SERIES_COLORS[0], values: data.daily30.map((d) => d.views) },
          { name: '방문자', color: SERIES_COLORS[1], values: data.daily30.map((d) => d.visitors) },
        ]}
      />
      <p className="small">
        <Link to="/manage/stats">통계 더 보기</Link>
      </p>
      <div className="two-col">
        <div>
          <h2>인기 글 (최근 7일)</h2>
          {data.popular7.length === 0 ? (
            <p className="muted">아직 조회된 글이 없습니다</p>
          ) : (
            <ol className="simple-list">
              {data.popular7.map((p) => (
                <li key={p.postId}>
                  <Link to={`/posts/${p.postId}`}>{p.title}</Link>
                  <span className="muted">{p.views.toLocaleString()}회</span>
                </li>
              ))}
            </ol>
          )}
        </div>
        <div>
          <h2>최근 글</h2>
          {data.recent.length === 0 ? (
            <p className="muted">아직 쓴 글이 없습니다</p>
          ) : (
            <ul className="simple-list">
              {data.recent.map((p) => (
                <li key={p.postId}>
                  <Link to={`/posts/${p.postId}`}>{p.title}</Link>
                  {p.visibility === 'PRIVATE' && <span className="badge">비공개</span>}
                  <span className="muted">{formatDate(p.createdAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </>
  )
}

function StatTile({ label, counts }: { label: string; counts: Counts }) {
  return (
    <div className="tile">
      <span className="muted small">오늘 {label}</span>
      <strong className="big">{counts.today.toLocaleString()}</strong>
      <span className="muted small">
        어제 {counts.yesterday.toLocaleString()} · 누적 {counts.total.toLocaleString()}
      </span>
    </div>
  )
}
