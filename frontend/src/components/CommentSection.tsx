import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError, del, get, post } from '../api/client'
import type { CommentView } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime } from '../format'
import { M } from '../messages'

/** 글 상세의 댓글 목록과 입력칸 (CF-18) */
export default function CommentSection({ postId, onCountChange }: { postId: number; onCountChange: (count: number) => void }) {
  const { me, requireLogin } = useAuth()
  const [comments, setComments] = useState<CommentView[]>([])
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    const list = await get<CommentView[]>(`/api/posts/${postId}/comments`)
    setComments(list)
    onCountChange(list.length)
  }, [postId, onCountChange])

  useEffect(() => {
    load()
  }, [load, me])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (busy) return
    if (!content.trim()) {
      setError('댓글을 입력해 주세요')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await post(`/api/posts/${postId}/comments`, { content })
      setContent('')
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.fieldMessage('content') ?? err.message : '잠시 뒤 다시 시도해 주세요')
    } finally {
      setBusy(false)
    }
  }

  const remove = async (id: number) => {
    if (!confirm(M.commentDeleteConfirm)) return
    await del(`/api/comments/${id}`)
    await load()
  }

  return (
    <section className="comments" id="comments">
      <h2>댓글 {comments.length}</h2>
      <ul>
        {comments.map((c) => (
          <li key={c.id} id={`comment-${c.id}`}>
            <span className="avatar sm">{c.authorNickname ? [...c.authorNickname][0] : '?'}</span>
            <div className="body">
              <div className="meta">
                <strong>{c.authorNickname ?? M.withdrawnUser}</strong>
                <span>{formatDateTime(c.createdAt)}</span>
                {c.deletable && (
                  <button className="link danger" onClick={() => remove(c.id)}>
                    삭제
                  </button>
                )}
              </div>
              <p className="comment-body">{c.content}</p>
            </div>
          </li>
        ))}
      </ul>
      {me ? (
        <form className="comment-form" onSubmit={submit}>
          <textarea value={content} onChange={(e) => setContent(e.target.value)} maxLength={500} rows={3} aria-label="댓글" placeholder="댓글을 남겨 보세요" />
          <div className="row">
            <span className="faint small">{[...content].length} / 500</span>
            <button className="primary" disabled={busy}>
              등록
            </button>
          </div>
          {error && <p className="error">{error}</p>}
        </form>
      ) : (
        <div className="comment-login">
          <p className="muted">{M.commentLoginRequired}</p>
          <button onClick={() => requireLogin()}>로그인</button>
        </div>
      )}
    </section>
  )
}
