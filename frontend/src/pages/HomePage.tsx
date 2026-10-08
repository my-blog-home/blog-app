import { useEffect, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { get } from '../api/client'
import { loadTopics } from '../api/topics'
import type { MySubscriptions, PageResult, Topic } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import Pagination from '../components/Pagination'
import HotPostsSlider from '../components/home/HotPostsSlider'
import { HotBloggersPanel, NoticePanel, PopularKeywordsPanel } from '../components/home/HomeSidePanels'
import { PostCards, ThumbRow } from '../components/PostList'
import { M } from '../messages'

type Sort = 'latest' | 'popular'
type Tab = 'all' | 'feed'

/**
 * 첫 화면: 주제 버튼(글의 주제로 모아 보기)과 새 글 썸네일 줄, 아래에 전체 글을 카드로.
 * 주제(?topic=)와 정렬(?sort=)은 주소에 남는다 (FR-34, BR-07, CR-37)
 * "구독 피드" 탭(?tab=feed)은 구독한 블로그의 공개 글만 모은다. 비회원에게는 로그인 안내, 구독이 없으면 구독 안내 (FR-067, CR-19)
 * 오른쪽 칸(좁은 화면에서는 아래)에 지금 핫한 글, 실시간 인기 검색어, 이번 주 인기 블로거, 공지를 둔다.
 * 고른 주제는 핫한 글과 인기 블로거에도 적용한다 (FR-072~074, FR-077)
 */
export default function HomePage() {
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? '1')
  const topicId = params.get('topic')
  const sort: Sort = params.get('sort') === 'popular' ? 'popular' : 'latest'
  const tab: Tab = params.get('tab') === 'feed' ? 'feed' : 'all'
  const { me, loading, requireLogin } = useAuth()
  const [feed, setFeed] = useState<PageResult | null>(null)
  const [hasSubscriptions, setHasSubscriptions] = useState(true)
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
    if (tab === 'feed') return
    const q = new URLSearchParams({ page: String(page), sort })
    if (topicId) q.set('topicId', topicId)
    get<PageResult>(`/api/posts?${q}`).then(setResult)
  }, [page, topicId, sort, tab])

  useEffect(() => {
    if (tab !== 'feed' || loading) return
    if (!me) {
      setFeed(null)
      return
    }
    get<PageResult>(`/api/feed?${new URLSearchParams({ page: String(page), sort })}`).then(async (r) => {
      setFeed(r)
      if (r.totalCount === 0) {
        const subs = await get<MySubscriptions>('/api/me/subscriptions?limit=1')
        setHasSubscriptions(subs.totalCount > 0)
      } else setHasSubscriptions(true)
    })
  }, [tab, page, sort, me, loading])

  const go = (next: { topic?: string | null; sort?: Sort; page?: number; tab?: Tab }) => {
    const q = new URLSearchParams()
    const nextTab = next.tab ?? tab
    // 구독 피드는 주제로 거르지 않는다. 주제를 고르면 전체 글로 돌아간다
    const t = nextTab === 'feed' ? null : next.topic !== undefined ? next.topic : topicId
    const s = next.sort ?? sort
    if (nextTab === 'feed') q.set('tab', 'feed')
    if (t) q.set('topic', t)
    if (s !== 'latest') q.set('sort', s)
    if (next.page && next.page > 1) q.set('page', String(next.page))
    setParams(q)
  }

  if (!latest || (tab === 'all' && !result)) return null
  const current = topics.find((t) => String(t.id) === topicId)
  const sideTopic = tab === 'feed' ? null : topicId
  return (
    <div className="home-layout">
      <div className="home-main">
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
            <button type="button" className={!topicId ? 'current' : ''} aria-pressed={!topicId} onClick={() => go({ topic: null, tab: 'all' })}>
              전체
            </button>
            {topics.map((t) => (
              <button
                key={t.id}
                type="button"
                className={String(t.id) === topicId ? 'current' : ''}
                aria-pressed={String(t.id) === topicId}
                onClick={() => go({ topic: String(t.id), tab: 'all' })}
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
        <div className="tabs home-tabs" role="tablist" aria-label="글 목록">
          <button type="button" role="tab" aria-selected={tab === 'all'} className={tab === 'all' ? 'current' : ''} onClick={() => go({ tab: 'all', page: 1 })}>
            전체 글
          </button>
          <button type="button" role="tab" aria-selected={tab === 'feed'} className={tab === 'feed' ? 'current' : ''} onClick={() => go({ tab: 'feed', page: 1 })}>
            구독 피드
          </button>
        </div>
        <div className="section-head with-sort">
          <div>
            {tab === 'all' && result ? (
              <>
                <p>
                  {current ? `'${current.name}' 주제의 공개 글` : '모든 블로그의 공개 글'} {result.totalCount.toLocaleString()}개
                </p>
                <h2>전체 글</h2>
              </>
            ) : (
              <>
                <p>내가 구독한 블로그의 공개 글{feed ? ` ${feed.totalCount.toLocaleString()}개` : ''}</p>
                <h2>구독 피드</h2>
              </>
            )}
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
        {tab === 'all' && result && (
          <>
            {result.items.length === 0 ? <p className="empty">{M.emptyList}</p> : <PostCards items={result.items} />}
            <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => go({ page: p })} />
          </>
        )}
        {tab === 'feed' &&
          (!me ? (
            !loading && (
              <div className="comment-login">
                <p>{M.feedLoginRequired}</p>
                <button onClick={() => requireLogin()}>로그인</button>
              </div>
            )
          ) : (
            feed && (
              <>
                {feed.items.length === 0 ? (
                  <p className="empty">{hasSubscriptions ? M.emptyList : M.feedEmpty}</p>
                ) : (
                  <PostCards items={feed.items} />
                )}
                <Pagination page={feed.page} totalPages={feed.totalPages} onChange={(p) => go({ page: p })} />
              </>
            )
          ))}
      </section>
      </div>
      <aside className="home-side" aria-label="안내">
        <HotPostsSlider topicId={sideTopic} />
        <PopularKeywordsPanel />
        <HotBloggersPanel topicId={sideTopic} />
        <NoticePanel />
      </aside>
    </div>
  )
}
