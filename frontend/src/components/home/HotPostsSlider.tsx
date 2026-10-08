import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../../api/client'
import type { HotPost } from '../../api/types'

const INTERVAL_MS = 5000

/** 움직임 줄이기 설정을 따른다 */
function usePrefersReducedMotion() {
  const query = '(prefers-reduced-motion: reduce)'
  const [reduce, setReduce] = useState(() => typeof window !== 'undefined' && !!window.matchMedia?.(query).matches)
  useEffect(() => {
    const media = window.matchMedia?.(query)
    if (!media) return
    const change = () => setReduce(media.matches)
    media.addEventListener?.('change', change)
    return () => media.removeEventListener?.('change', change)
  }, [])
  return reduce
}

/**
 * 지금 핫한 글 슬라이드 (FR-072, BR-33). 최대 3개, 5초마다 넘어가고 마우스를 올리거나 키보드로 들어오면 멈춘다.
 * 움직임 줄이기 설정이면 자동으로 넘기지 않고, 글이 하나뿐이면 넘김 버튼과 점이 없다. 주제를 고르면 그 주제의 글만.
 */
export default function HotPostsSlider({ topicId }: { topicId: string | null }) {
  const [items, setItems] = useState<HotPost[] | null>(null)
  const [current, setCurrent] = useState(0)
  const [paused, setPaused] = useState(false)
  const reduceMotion = usePrefersReducedMotion()
  const area = useRef<HTMLElement>(null)

  useEffect(() => {
    setCurrent(0)
    const q = new URLSearchParams()
    if (topicId) q.set('topicId', topicId)
    get<HotPost[]>(`/api/home/hot-posts?${q}`)
      .then(setItems)
      .catch(() => setItems([]))
  }, [topicId])

  const count = items?.length ?? 0
  // current가 바뀌면(직접 넘겨도) 타이머를 처음부터 다시 잰다
  useEffect(() => {
    if (count < 2 || paused || reduceMotion) return
    const timer = window.setTimeout(() => setCurrent((c) => (c + 1) % count), INTERVAL_MS)
    return () => window.clearTimeout(timer)
  }, [count, paused, reduceMotion, current])

  if (!items) return null
  const go = (i: number) => setCurrent((i + count) % count)

  return (
    <section
      ref={area}
      className="hot-posts"
      aria-roledescription="carousel"
      aria-label="지금 핫한 글"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={(e) => {
        if (!area.current?.contains(e.relatedTarget as Node | null)) setPaused(false)
      }}
    >
      <div className="hot-posts-head">
        <h2>지금 핫한 글</h2>
        <span className="hot-posts-note">최근 2주 · 좋아요·댓글 기준</span>
      </div>
      <div className="slider">
        {count === 0 ? (
          <div className="slide empty-slide">
            <span>최근 2주 안에 올라온 글이 아직 없습니다.</span>
          </div>
        ) : (
          <div className="slider-track" style={{ transform: `translateX(-${current * 100}%)` }} aria-live={paused ? 'polite' : 'off'}>
            {items.map((p, i) => (
              <Link
                key={p.id}
                to={`/posts/${p.id}`}
                className="slide"
                style={{ background: `linear-gradient(135deg, ${p.authorColor}, #efe9fc)` }}
                role="group"
                aria-roledescription="slide"
                aria-label={`${i + 1} / ${count}`}
                aria-hidden={i !== current}
                tabIndex={i === current ? 0 : -1}
              >
                <span className="slide-label">
                  <span className="slide-rank">HOT {i + 1}</span>
                  <span className="slide-blog">
                    {p.blogName} · {p.topicName}
                  </span>
                </span>
                <span className="slide-title">{p.title}</span>
                <span className="slide-meta">
                  {p.authorNickname} · 조회 {p.viewCount.toLocaleString()} · 좋아요 {p.likeCount.toLocaleString()} · 댓글{' '}
                  {p.commentCount.toLocaleString()}
                </span>
              </Link>
            ))}
          </div>
        )}
        {count > 1 && (
          <div className="slider-ctrl">
            <button type="button" className="slider-btn" aria-label="이전 글" onClick={() => go(current - 1)}>
              ‹
            </button>
            <div className="slider-dots">
              {items.map((p, i) => (
                <button
                  key={p.id}
                  type="button"
                  className={i === current ? 'slider-dot active' : 'slider-dot'}
                  aria-label={`${i + 1}번째 글`}
                  aria-current={i === current}
                  onClick={() => go(i)}
                />
              ))}
            </div>
            <button type="button" className="slider-btn" aria-label="다음 글" onClick={() => go(current + 1)}>
              ›
            </button>
          </div>
        )}
      </div>
    </section>
  )
}
