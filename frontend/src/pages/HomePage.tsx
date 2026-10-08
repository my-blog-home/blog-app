import { useEffect, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { get } from '../api/client'
import type { PageResult } from '../api/types'
import Pagination from '../components/Pagination'
import { PostCards, ThumbRow } from '../components/PostList'
import { M } from '../messages'

/** 첫 화면: 새 글을 썸네일 줄로 먼저 보여 주고, 아래에 전체 글을 카드로 (contracts/screens.md) */
export default function HomePage() {
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? '1')
  const [latest, setLatest] = useState<PageResult | null>(null)
  const [result, setResult] = useState<PageResult | null>(null)
  const notice = (useLocation().state as { notice?: string } | null)?.notice

  useEffect(() => {
    get<PageResult>('/api/posts?page=1').then(setLatest)
  }, [])

  useEffect(() => {
    get<PageResult>(`/api/posts?page=${page}`).then(setResult)
  }, [page])

  if (!result || !latest) return null
  return (
    <>
      {notice && <p className="notice">{notice}</p>}
      <div className="hero">
        <p className="hero-kicker">Ylog</p>
        <h1>오늘의 이야기</h1>
        <p>Ylog에 올라온 새 글을 만나 보세요.</p>
      </div>

      {latest.items.length > 0 && (
        <section className="section">
          <div className="section-head">
            <p>방금 올라온 새 글</p>
            <h2>이 글 어때요?</h2>
          </div>
          <ThumbRow items={latest.items.slice(0, 8)} />
        </section>
      )}

      <section className="section">
        <div className="section-head">
          <p>모든 블로그의 공개 글 {result.totalCount.toLocaleString()}개</p>
          <h2>전체 글</h2>
        </div>
        {result.items.length === 0 ? <p className="empty">{M.emptyList}</p> : <PostCards items={result.items} />}
        <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => setParams({ page: String(p) })} />
      </section>
    </>
  )
}
