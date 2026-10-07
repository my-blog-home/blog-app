import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, del, get } from '../api/client'
import type { PostDetail } from '../api/types'
import MarkdownView from '../components/MarkdownView'
import { formatDateTime } from '../format'
import { M } from '../messages'
import NotFoundPage from './NotFoundPage'

/** 글 상세. 없는 글과 볼 수 없는 글은 같은 문구 (CF-09) */
export default function PostDetailPage() {
  const { postId } = useParams()
  const navigate = useNavigate()
  const [post, setPost] = useState<PostDetail | null>(null)
  const [missing, setMissing] = useState(false)

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
    <article className="post">
      <p className="meta">
        <Link to={`/blogs/${post.blog.id}?category=${post.category.id}`}>{post.category.name}</Link>
        {post.visibility === 'PRIVATE' && <span className="badge">비공개</span>}
      </p>
      <h1>{post.title}</h1>
      <p className="meta">
        <Link to={`/blogs/${post.blog.id}`}>{post.blog.name}</Link>
        <span>{formatDateTime(post.createdAt)}</span>
        {post.updatedAt && <span>수정 {formatDateTime(post.updatedAt)}</span>}
      </p>
      {post.editable && (
        <div className="actions">
          <Link to={`/posts/${post.id}/edit`}>수정</Link>
          <button className="link danger" onClick={remove}>
            삭제
          </button>
        </div>
      )}
      <MarkdownView source={post.body} />
      <nav className="post-nav">
        {post.prevPostId ? <Link to={`/posts/${post.prevPostId}`}>← 이전 글</Link> : <span />}
        <Link to={`/blogs/${post.blog.id}?category=${post.category.id}`}>목록으로</Link>
        {post.nextPostId ? <Link to={`/posts/${post.nextPostId}`}>다음 글 →</Link> : <span />}
      </nav>
    </article>
  )
}
