import NoticeBadge, { NOTICE_TYPE_LABEL } from '../NoticeBadge'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../../api/client'
import type { HotBlogger, NoticePage, PopularKeywords } from '../../api/types'
import { formatDate } from '../../format'
import { M } from '../../messages'

const REFRESH_MS = 60 * 1000

/** 순위 변동: 오르면 ▲n, 내리면 ▼n, 같으면 -, 새로 들어오면 NEW (BR-31) */
export function ChangeMark({ change, isNew }: { change: number; isNew: boolean }) {
  if (isNew) return <span className="chg new" aria-label="새로 순위에 들어옴">NEW</span>
  if (change > 0) return <span className="chg up" aria-label={`${change}단계 상승`}>▲{change}</span>
  if (change < 0) return <span className="chg down" aria-label={`${-change}단계 하락`}>▼{-change}</span>
  return <span className="chg same" aria-label="순위 변동 없음">-</span>
}

/** 실시간 인기 검색어: 최근 24시간 상위 10개, 열어 둔 동안 1분마다 다시 읽는다 (FR-073, BR-29) */
export function PopularKeywordsPanel() {
  const [data, setData] = useState<PopularKeywords | null>(null)

  useEffect(() => {
    const load = () => get<PopularKeywords>('/api/search/popular-keywords').then(setData).catch(() => undefined)
    load()
    const timer = window.setInterval(load, REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <section className="side-panel" aria-labelledby="keyword-title">
      <div className="side-head">
        <h3 id="keyword-title">실시간 인기 검색어</h3>
        {data && <span className="side-time">{data.asOf} 기준</span>}
      </div>
      <ol className="hot-list">
        {data && data.items.length === 0 && <li className="side-empty">{M.noPopularKeywords}</li>}
        {data?.items.map((k) => (
          <li key={k.keyword}>
            <Link to={`/search?q=${encodeURIComponent(k.keyword)}`}>
              <span className={k.rank <= 3 ? 'rank top' : 'rank'}>{k.rank}</span>
              <span className="kw">{k.keyword}</span>
              <ChangeMark change={k.change} isNew={k.isNew} />
            </Link>
          </li>
        ))}
      </ol>
      <p className="hot-note">최근 24시간 검색 기준</p>
    </section>
  )
}

/** 이번 주 인기 블로거: 최근 7일 글의 좋아요·댓글 점수를 블로그마다 더한 상위 5개 (FR-074, BR-45) */
export function HotBloggersPanel({ topicId }: { topicId: string | null }) {
  const [items, setItems] = useState<HotBlogger[] | null>(null)

  useEffect(() => {
    const q = new URLSearchParams()
    if (topicId) q.set('topicId', topicId)
    get<HotBlogger[]>(`/api/home/hot-bloggers?${q}`)
      .then(setItems)
      .catch(() => setItems([]))
  }, [topicId])

  return (
    <section className="side-panel" aria-labelledby="blogger-title">
      <div className="side-head">
        <h3 id="blogger-title">이번 주 인기 블로거</h3>
        <span className="side-time">최근 7일</span>
      </div>
      <ol className="blogger-list">
        {items && items.length === 0 && <li className="side-empty">{M.noHotBloggers}</li>}
        {items?.map((b) => (
          <li key={b.blogId}>
            <Link to={`/blogs/${b.blogId}`}>
              <span className={b.rank <= 3 ? 'rank top' : 'rank'}>{b.rank}</span>
              <span className="avatar sm" style={{ background: b.ownerColor }}>
                {[...b.ownerNickname][0]}
              </span>
              <span className="blogger-info">
                <strong>{b.blogName}</strong>
                <small>
                  {b.ownerNickname} · 구독자 {b.subscriberCount.toLocaleString()}
                </small>
              </span>
            </Link>
          </li>
        ))}
      </ol>
      <p className="hot-note">최근 7일 글의 좋아요·댓글 기준</p>
    </section>
  )
}

export { NOTICE_TYPE_LABEL }

/** 공지 · 이용 안내: 고정 글이 위, 최대 5개 (FR-077, BR-30) */
export function NoticePanel() {
  const [page, setPage] = useState<NoticePage | null>(null)

  useEffect(() => {
    get<NoticePage>('/api/notices?size=5')
      .then(setPage)
      .catch(() => setPage(null))
  }, [])

  return (
    <section className="side-panel" aria-labelledby="notice-title">
      <div className="side-head">
        <h3 id="notice-title">공지 · 이용 안내</h3>
        <Link className="side-more" to="/notices">
          더보기
        </Link>
      </div>
      <ul className="notice-list">
        {page?.items.map((n) => (
          <li key={n.id}>
            <Link className="notice-item" to={`/notices/${n.id}`}>
              <span className="notice-line">
                <NoticeBadge notice={n} />
                <span className="notice-title">{n.title}</span>
              </span>
              <span className="notice-date">{formatDate(n.createdAt)}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
