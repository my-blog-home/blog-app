import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { get } from '../api/client'
import type { CommentedPostPage, PageResult } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import Pagination from '../components/Pagination'
import PostList from '../components/PostList'
import { formatDateTime } from '../format'

type Tab = 'liked' | 'commented'

/**
 * 내 활동: 좋아요한 글(최근 누른 순)과 댓글 단 글(내 최근 댓글 순, 글마다 내 최근 댓글 하나와 "외 n개").
 * 지금 읽을 수 없는 글은 서버가 빼고 준다. 댓글을 누르면 그 댓글 위치로 간다 (FR-069, BR-32)
 */
export default function ActivityPage() {
  const { me, loading, requireLogin } = useAuth()
  const [params, setParams] = useSearchParams()
  const tab: Tab = params.get('tab') === 'commented' ? 'commented' : 'liked'
  const page = Number(params.get('page') ?? '1')
  const [liked, setLiked] = useState<PageResult | null>(null)
  const [commented, setCommented] = useState<CommentedPostPage | null>(null)

  useEffect(() => {
    if (!loading && !me) requireLogin()
  }, [loading, me, requireLogin])

  useEffect(() => {
    if (!me) return
    if (tab === 'liked') get<PageResult>(`/api/me/liked-posts?page=${page}`).then(setLiked)
    else get<CommentedPostPage>(`/api/me/commented-posts?page=${page}`).then(setCommented)
  }, [me, tab, page])

  if (!me) return null

  const go = (next: { tab?: Tab; page?: number }) => {
    const q = new URLSearchParams()
    const t = next.tab ?? tab
    if (t !== 'liked') q.set('tab', t)
    if (next.page && next.page > 1) q.set('page', String(next.page))
    setParams(q)
  }

  return (
    <section className="activity-page">
      <h1>내 활동</h1>
      <div className="tabs" role="tablist" aria-label="내 활동">
        <button type="button" role="tab" aria-selected={tab === 'liked'} className={tab === 'liked' ? 'current' : ''} onClick={() => go({ tab: 'liked' })}>
          좋아요한 글{liked && tab === 'liked' ? ` ${liked.totalCount}` : ''}
        </button>
        <button type="button" role="tab" aria-selected={tab === 'commented'} className={tab === 'commented' ? 'current' : ''} onClick={() => go({ tab: 'commented' })}>
          댓글 단 글{commented && tab === 'commented' ? ` ${commented.totalCount}` : ''}
        </button>
      </div>

      {tab === 'liked' && liked && (
        <>
          {liked.items.length === 0 ? <p className="empty">아직 좋아요한 글이 없습니다</p> : <PostList items={liked.items} showBlog />}
          <Pagination page={liked.page} totalPages={liked.totalPages} onChange={(p) => go({ page: p })} />
        </>
      )}

      {tab === 'commented' && commented && (
        <>
          {commented.items.length === 0 ? (
            <p className="empty">아직 댓글을 단 글이 없습니다</p>
          ) : (
            <ul className="post-list activity-list">
              {commented.items.map((c) => (
                <li key={c.postId}>
                  <div className="meta">
                    <Link to={`/blogs/${c.blogId}`} className="card-blog">
                      {c.blogName}
                    </Link>
                  </div>
                  <Link to={`/posts/${c.postId}`} className="title">
                    {c.postTitle}
                  </Link>
                  <Link to={`/posts/${c.postId}#comment-${c.commentId}`} className="my-comment">
                    <span className="meta">
                      {c.reply && <span className="badge">답글</span>}
                      {c.secret && <span className="badge secret">🔒 비밀</span>}
                      <span>{formatDateTime(c.commentedAt)}</span>
                    </span>
                    <span className="excerpt">
                      {c.excerpt}
                      {c.otherCount > 0 && <span className="faint"> 외 {c.otherCount}개</span>}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <Pagination page={commented.page} totalPages={commented.totalPages} onChange={(p) => go({ page: p })} />
        </>
      )}
    </section>
  )
}
