import { useEffect, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { get } from '../api/client'
import { loadTopics } from '../api/topics'
import type { PageResult, Topic } from '../api/types'
import Pagination from '../components/Pagination'
import { PostCards, ThumbRow } from '../components/PostList'
import { M } from '../messages'

type Sort = 'latest' | 'popular'

/**
 * 첫 화면: 주제 버튼(글의 주제로 모아 보기)과 새 글 썸네일 줄, 아래에 전체 글을 카드로.
 * 주제(?topic=)와 정렬(?sort=)은 주소에 남는다 (FR-34, BR-07, CR-37)
 */
export default function HomePage() {
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? '1')
  const topicId = params.get('topic')
  const sort: Sort = params.get('sort') === 'popular' ? 'popular' : 'latest'
  const [topics, setTopics] = useState<Topic[]>([])
  const [latest, setLatest] = useState<PageResult | null>(null)
  const [result, setResult] = useState<PageResult | null>(null)
  const notice = (useLocation().state as { notice?: string } | null)?.notice

  useEffect(() => {
    loadTopics(true).then(setTopics).catch(() => setTopics([]))
  }, [])

  useEffect(() => {
    const q = new URLSearchParams({ page: '1' })
    if (topicId) q.set('topicId', topicId)
    get<PageResult>(`/api/posts?${q}`).then(setLatest)
  }, [topicId])

  useEffect(() => {
    const q = new URLSearchParams({ page: String(page), sort })
    if (topicId) q.set('topicId', topicId)
    get<PageResult>(`/api/posts?${q}`).then(setResult)
  }, [page, topicId, sort])

  const go = (next: { topic?: string | null; sort?: Sort; page?: number }) => {
    const q = new URLSearchParams()
    const t = next.topic !== undefined ? next.topic : topicId
    const s = next.sort ?? sort
    if (t) q.set('topic', t)
    if (s !== 'latest') q.set('sort', s)
    if (next.page && next.page > 1) q.set('page', String(next.page))
    setParams(q)
  }

  if (!result || !latest) return null
  const current = topics.find((t) => String(t.id) === topicId)
  return (
    <>
      {notice && <p className="notice">{notice}</p>}
      <div className="hero">
        <div className="hero-head">
          <div>
            <p className="hero-kicker">Ylog</p>
            <h1>오늘의 이야기</h1>
          </div>
          {topicId && (
            <button type="button" className="link" onClick={() => go({ topic: null })}>
              전체 보기
            </button>
          )}
        </div>
        <p>{current ? `'${current.name}' 주제의 글만 모아 보고 있어요.` : 'Ylog에 올라온 새 글을 관심 있는 주제로 골라 만나 보세요.'}</p>
        {topics.length > 0 && (
          <div className="topic-chips" role="group" aria-label="주제">
            <button type="button" className={!topicId ? 'current' : ''} aria-pressed={!topicId} onClick={() => go({ topic: null })}>
              전체
            </button>
            {topics.map((t) => (
              <button
                key={t.id}
                type="button"
                className={String(t.id) === topicId ? 'current' : ''}
                aria-pressed={String(t.id) === topicId}
                onClick={() => go({ topic: String(t.id) })}
              >
                {t.name}
              </button>
            ))}
          </div>
        )}
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
        <div className="section-head with-sort">
          <div>
            <p>
              {current ? `'${current.name}' 주제의 공개 글` : '모든 블로그의 공개 글'} {result.totalCount.toLocaleString()}개
            </p>
            <h2>전체 글</h2>
          </div>
          <div className="sort-toggle" role="group" aria-label="정렬">
            <button type="button" className={sort === 'latest' ? 'current' : ''} aria-pressed={sort === 'latest'} onClick={() => go({ sort: 'latest' })}>
              최신순
            </button>
            <button type="button" className={sort === 'popular' ? 'current' : ''} aria-pressed={sort === 'popular'} onClick={() => go({ sort: 'popular' })}>
              인기순
            </button>
          </div>
        </div>
        {result.items.length === 0 ? <p className="empty">{M.emptyList}</p> : <PostCards items={result.items} />}
        <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => go({ page: p })} />
      </section>
    </>
  )
}
