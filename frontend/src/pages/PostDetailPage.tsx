import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, del, get } from '../api/client'
import type { PostDetail } from '../api/types'
import CommentSection from '../components/CommentSection'
import LikeButton from '../components/LikeButton'
import MarkdownView from '../components/MarkdownView'
import ReportModal from '../components/ReportModal'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime } from '../format'
import { M } from '../messages'
import NotFoundPage from './NotFoundPage'

/** 글 상세. 없는 글과 볼 수 없는 글은 같은 문구 (CF-09) */
export default function PostDetailPage() {
  const { postId } = useParams()
  const navigate = useNavigate()
  const [post, setPost] = useState<PostDetail | null>(null)
  const [missing, setMissing] = useState(false)
  const [commentCount, setCommentCount] = useState(0)
  const [reporting, setReporting] = useState(false)
  const { requireLogin } = useAuth()
  const onCountChange = useCallback((n: number) => setCommentCount(n), [])

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

  return (
    <article className="reading">
      <header className="article-head">
        <Link to={`/blogs/${post.blog.id}?category=${post.category.id}`} className="category">
          {post.category.name}
        </Link>
        {post.visibility === 'PRIVATE' && <span className="badge" style={{ marginLeft: 8 }}>비공개</span>}
        <h1>{post.title}</h1>
        <div className="meta">
          <Link to={`/blogs/${post.blog.id}`} className="card-blog">
            <span className="avatar sm">{[...post.blog.name][0]}</span>
            <span>{post.blog.name}</span>
          </Link>
          <span>{formatDateTime(post.createdAt)}</span>
          {post.updatedAt && <span>수정 {formatDateTime(post.updatedAt)}</span>}
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
        {!post.editable && <button onClick={() => requireLogin(() => setReporting(true)) && setReporting(true)}>신고</button>}
      </div>
      {reporting && <ReportModal postId={post.id} onClose={() => setReporting(false)} />}
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
      <p className="to-list">
        <Link to={`/blogs/${post.blog.id}?category=${post.category.id}`} className="button">
          목록으로
        </Link>
      </p>
      <CommentSection postId={post.id} onCountChange={onCountChange} />
    </article>
  )
}
