import { useEffect, useState } from 'react'
import { get } from '../../api/client'
import LineChart, { SERIES_COLORS } from '../../components/LineChart'
import { shortDate, type Daily } from './DashboardPage'
import { useManage } from './ManageLayout'

/** 통계: 7일/30일, 조회수·방문자 그래프와 댓글 수 그래프 (BM-06) */
export default function StatsPage() {
  const { blog } = useManage()
  const [days, setDays] = useState(30)
  const [daily, setDaily] = useState<Daily[] | null>(null)

  useEffect(() => {
    get<Daily[]>(`/api/manage/blogs/${blog.id}/stats?days=${days}`).then(setDaily)
  }, [blog.id, days])

  return (
    <>
      <h1>통계</h1>
      <div className="tabs">
        {[7, 30].map((d) => (
          <button key={d} className={days === d ? 'current' : ''} onClick={() => setDays(d)}>
            {d}일
          </button>
        ))}
      </div>
      {daily && (
        <>
          <div className="panel-card">
          <h2>조회수·방문자</h2>
          <LineChart
            labels={daily.map((d) => shortDate(d.date))}
            series={[
              { name: '조회수', color: SERIES_COLORS[0], values: daily.map((d) => d.views) },
              { name: '방문자', color: SERIES_COLORS[1], values: daily.map((d) => d.visitors) },
            ]}
          />
          </div>
          <div className="panel-card">
          <h2>댓글 수</h2>
          <LineChart labels={daily.map((d) => shortDate(d.date))} series={[{ name: '댓글 수', color: SERIES_COLORS[0], values: daily.map((d) => d.comments) }]} height={160} />
          </div>
          <p className="muted small">같은 사람이 같은 글을 30분 안에 다시 열면 조회수에 넣지 않고, 방문자는 하루에 한 번만 셉니다. 내가 내 글을 연 것은 세지 않습니다.</p>
        </>
      )}
    </>
  )
}
