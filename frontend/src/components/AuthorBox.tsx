import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { del, get, put } from '../api/client'
import type { BlogView, SubscriptionState } from '../api/types'
import { useAuth } from '../auth/AuthContext'

/**
 * 글 상세 아래의 글쓴이 상자 (FR-076): 아바타, 블로그 이름(블로그로), 닉네임(작성자 프로필로), 블로그 소개, 구독 버튼(내 글이 아니면)
 */
export default function AuthorBox({ blogId, authorId, authorNickname, authorColor }: {
  blogId: number
  authorId: number
  authorNickname: string
  authorColor: string
}) {
  const { me, requireLogin } = useAuth()
  const [blog, setBlog] = useState<BlogView | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    get<BlogView>(`/api/blogs/${blogId}`).then(setBlog).catch(() => setBlog(null))
  }, [blogId])

  // 로그인하면 구독 여부를 다시 읽는다
  useEffect(load, [load, me])

  const toggle = async () => {
    if (!blog || busy) return
    setBusy(true)
    try {
      const url = `/api/blogs/${blog.id}/subscription`
      const next = blog.subscribedByMe ? await del<SubscriptionState>(url) : await put<SubscriptionState>(url)
      setBlog({ ...blog, subscribedByMe: next.subscribed, subscriberCount: next.subscriberCount })
    } finally {
      setBusy(false)
    }
  }
  const subscribe = () => {
    if (requireLogin(load)) toggle()
  }

  return (
    <section className="author-box" aria-label="글쓴이">
      <Link to={`/users/${authorId}`} className="avatar" style={{ background: authorColor }} aria-label={`${authorNickname} 프로필`}>
        {[...authorNickname][0]}
      </Link>
      <div className="info">
        <Link to={`/blogs/${blogId}`} className="author-blog">
          {blog?.name ?? ''}
        </Link>
        <p>
          <Link to={`/users/${authorId}`} className="author-link">
            {authorNickname}
          </Link>
          {blog && <span className="faint"> · 구독자 {blog.subscriberCount.toLocaleString()}명</span>}
        </p>
        {blog?.description && <p className="author-desc">{blog.description}</p>}
      </div>
      {blog && !blog.owner && (
        <button
          type="button"
          className={blog.subscribedByMe ? 'subscribe-btn on' : 'subscribe-btn primary'}
          aria-pressed={blog.subscribedByMe}
          disabled={busy}
          onClick={subscribe}
        >
          {blog.subscribedByMe ? '구독 중' : '구독'}
        </button>
      )}
    </section>
  )
}
