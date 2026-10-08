import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, del, get } from '../api/client'
import type { PostDetail } from '../api/types'
import AuthorBox from '../components/AuthorBox'
import BackButton from '../components/BackButton'
import CommentSection from '../components/CommentSection'
import LikeButton from '../components/LikeButton'
import MarkdownView from '../components/MarkdownView'
import ReportModal from '../components/ReportModal'
import { useAuth } from '../auth/AuthContext'
import { useMarkCurrentBlog } from '../layout/CurrentBlog'
import { formatDateTime } from '../format'
import { M } from '../messages'
import NotFoundPage from './NotFoundPage'
import { plainExcerpt, useDocumentMeta } from '../meta'

/** 링크 복사: 클립보드 API가 없으면 예전 방식으로 복사한다 */
async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // 아래 방식으로 다시 시도한다
  }
  const area = document.createElement('textarea')
  area.value = text
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  const ok = document.execCommand('copy')
  area.remove()
  return ok
}

/**
 * 글 상세. 없는 글과 볼 수 없는 글은 같은 문구 (CF-09)
 * 조회수, 링크 복사, 글쓴이 상자, 뒤로 버튼을 둔다 (FR-076). 아래 순서는 반응(좋아요·링크 복사·신고) → 댓글 → 글쓴이 → 목록으로 → 이전/다음 글
 */
export default function PostDetailPage() {
  const { postId } = useParams()
  const navigate = useNavigate()
  const [post, setPost] = useState<PostDetail | null>(null)
  const [missing, setMissing] = useState(false)
  const [commentCount, setCommentCount] = useState(0)
  const [reporting, setReporting] = useState(false)
  const [copyMessage, setCopyMessage] = useState<string | null>(null)
  const { requireLogin } = useAuth()
  const onCountChange = useCallback((n: number) => setCommentCount(n), [])
  useMarkCurrentBlog(post?.blog.id)
  const excerpt = useMemo(() => (post ? plainExcerpt(post.body) : null), [post])
  useDocumentMeta(post?.title, excerpt)

  useEffect(() => {
    if (!copyMessage) return
    const timer = window.setTimeout(() => setCopyMessage(null), 2500)
    return () => window.clearTimeout(timer)
  }, [copyMessage])

  useEffect(() => {
    setPost(null)
    setMissing(false)
    get<PostDetail>(`/api/posts/${postId}`)
      .then(setPost)
      .catch((e) => {
        if (e instanceof ApiError && e.status === 404) setMissing(true)
      })
  }, [postId])

  if (missing) return <NotFoundPage message={M.postNotFound} />
  if (!post) return null

  const remove = async () => {
    if (!confirm(M.deleteConfirm)) return
    const { blogId } = await del<{ blogId: number }>(`/api/posts/${post.id}`)
    navigate(`/blogs/${blogId}`, { replace: true })
  }

  const copyLink = async () => {
    setCopyMessage((await copyText(window.location.href)) ? M.linkCopied : M.linkCopyFailed)
  }

  return (
    <article className="reading">
      <BackButton fallback={`/blogs/${post.blog.id}`} />
      <header className="article-head">
        {/* 글 맨 위에 주제(누르면 그 주제의 글 모아 보기)와 분류를 함께 보인다 (CR-68) */}
        <p className="article-topic">
          <Link to={`/?topic=${post.topic.id}`} className="topic-link">
            {post.topic.name}
          </Link>
          <span aria-hidden="true"> · </span>
          <Link to={`/blogs/${post.blog.id}?category=${post.category.id}`} className="category">
            {post.category.name}
          </Link>
          {post.status === 'DRAFT' && <span className="badge" style={{ marginLeft: 8 }}>임시저장</span>}
          {post.visibility === 'PRIVATE' && <span className="badge" style={{ marginLeft: 8 }}>비공개</span>}
        </p>
        <h1>{post.title}</h1>
        <div className="meta">
          <Link to={`/blogs/${post.blog.id}`} className="card-blog">
            <span className="avatar sm" style={{ background: post.authorColor }}>
              {[...post.blog.name][0]}
            </span>
            <span>{post.blog.name}</span>
          </Link>
          <Link to={`/users/${post.authorId}`} className="author-link">
            {post.authorNickname}
          </Link>
          {post.createdAt && <span>{formatDateTime(post.createdAt)}</span>}
          {post.updatedAt && <span>수정 {formatDateTime(post.updatedAt)}</span>}
          <span>조회 {post.viewCount.toLocaleString()}</span>
          <a href="#comments">댓글 {commentCount || post.commentCount}</a>
          {post.editable && (
            <span className="owner-actions">
              <Link to={`/posts/${post.id}/edit`} className="button sm">
                수정
              </Link>
              <button className="sm danger" onClick={remove}>
                삭제
              </button>
            </span>
          )}
        </div>
      </header>
      <MarkdownView source={post.body} />
      {post.tags.length > 0 && (
        <p className="tags article-tags">
          {post.tags.map((t) => (
            <Link key={t} to={`/tags/${encodeURIComponent(t)}`} className="tag">
              #{t}
            </Link>
          ))}
        </p>
      )}
      <div className="reactions">
        <LikeButton key={post.id} postId={post.id} initialCount={post.likeCount} initialLiked={post.likedByMe} isMine={post.editable} />
        <button type="button" onClick={copyLink}>
          링크 복사
        </button>
        {!post.editable && <button onClick={() => requireLogin(() => setReporting(true)) && setReporting(true)}>신고</button>}
      </div>
      <p className="copy-toast" role="status" aria-live="polite">
        {copyMessage}
      </p>
      {reporting && <ReportModal target={{ kind: 'post', id: post.id }} onClose={() => setReporting(false)} />}
      <CommentSection postId={post.id} onCountChange={onCountChange} />
      <AuthorBox blogId={post.blog.id} authorId={post.authorId} authorNickname={post.authorNickname} authorColor={post.authorColor} />
      <p className="to-list">
        <Link to={`/blogs/${post.blog.id}?category=${post.category.id}`} className="button">
          목록으로
        </Link>
      </p>
      <nav className="prev-next" aria-label="이전 글과 다음 글">
        {post.prevPostId && (
          <Link to={`/posts/${post.prevPostId}`}>
            <small>이전 글</small>
            <span>← 이전 글 보기</span>
          </Link>
        )}
        {post.nextPostId && (
          <Link to={`/posts/${post.nextPostId}`} className="next">
            <small>다음 글</small>
            <span>다음 글 보기 →</span>
          </Link>
        )}
      </nav>
    </article>
  )
}
