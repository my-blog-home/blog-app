import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useLocation } from 'react-router-dom'
import { ApiError, del, get, post } from '../api/client'
import type { CommentView } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime } from '../format'
import { M } from '../messages'
import ReportModal from './ReportModal'

const COMMENT_MAX = 500

/**
 * 글 상세의 댓글 목록과 입력칸 (CF-18).
 * 답글은 한 단계까지(FR-065), 비밀 댓글은 가려지면 "비밀 댓글입니다"만 보이고 버튼이 없다(FR-066), 댓글도 신고한다(FR-071).
 * 댓글 수는 답글을 포함한다.
 */
export default function CommentSection({ postId, onCountChange }: { postId: number; onCountChange: (count: number) => void }) {
  const { me, requireLogin } = useAuth()
  const location = useLocation()
  const [comments, setComments] = useState<CommentView[]>([])
  const [replyTo, setReplyTo] = useState<number | null>(null)
  const [reporting, setReporting] = useState<number | null>(null)
  const [loaded, setLoaded] = useState(false)

  const load = useCallback(async () => {
    const list = await get<CommentView[]>(`/api/posts/${postId}/comments`)
    setComments(list)
    setLoaded(true)
    onCountChange(list.reduce((sum, c) => sum + 1 + c.replies.length, 0))
  }, [postId, onCountChange])

  useEffect(() => {
    load()
  }, [load, me])

  // 내 활동에서 댓글을 눌러 들어오면 그 댓글로 이동한다 (FR-069)
  useEffect(() => {
    if (!loaded || !location.hash.startsWith('#comment-')) return
    document.getElementById(location.hash.slice(1))?.scrollIntoView({ block: 'center' })
  }, [loaded, location.hash])

  const remove = async (id: number) => {
    if (!confirm(M.commentDeleteConfirm)) return
    await del(`/api/comments/${id}`)
    await load()
  }

  const count = comments.reduce((sum, c) => sum + 1 + c.replies.length, 0)

  const item = (c: CommentView, isReply: boolean) => (
    <div className="comment-item" id={`comment-${c.id}`}>
      <span className="avatar sm" style={c.authorColor ? { background: c.authorColor } : undefined}>
        {c.authorNickname ? [...c.authorNickname][0] : '?'}
      </span>
      <div className="body">
        <div className="meta">
          <strong>{c.authorNickname ?? M.withdrawnUser}</strong>
          {c.isBlogOwner && <span className="badge owner">{M.blogOwnerLabel}</span>}
          {c.secret && (
            <span className="badge secret" aria-label="비밀 댓글">
              🔒 비밀
            </span>
          )}
          <span>{formatDateTime(c.createdAt)}</span>
          {c.canReply && (
            <button className="link" onClick={() => setReplyTo(replyTo === c.id ? null : c.id)} aria-expanded={replyTo === c.id}>
              답글
            </button>
          )}
          {c.reportable &&
            (c.reportedByMe ? (
              <button className="link" disabled>
                신고함
              </button>
            ) : (
              <button className="link" onClick={() => setReporting(c.id)}>
                신고
              </button>
            ))}
          {c.deletable && (
            <button className="link danger" onClick={() => remove(c.id)}>
              삭제
            </button>
          )}
        </div>
        {c.hidden ? <p className="comment-body hidden-comment">{M.secretComment}</p> : <p className="comment-body">{c.content}</p>}
        {!isReply && replyTo === c.id && (
          <CommentForm
            postId={postId}
            parent={c}
            onCancel={() => setReplyTo(null)}
            onSaved={async () => {
              setReplyTo(null)
              await load()
            }}
          />
        )}
      </div>
    </div>
  )

  return (
    <section className="comments" id="comments">
      <h2>댓글 {count}</h2>
      <ul>
        {comments.map((c) => (
          <li key={c.id}>
            {item(c, false)}
            {c.replies.length > 0 && (
              <ul className="replies" aria-label="답글">
                {c.replies.map((r) => (
                  <li key={r.id}>{item(r, true)}</li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
      {me ? (
        <CommentForm postId={postId} onSaved={load} />
      ) : (
        <div className="comment-login">
          <p className="muted">{M.commentLoginRequired}</p>
          <button onClick={() => requireLogin()}>로그인</button>
        </div>
      )}
      {reporting !== null && (
        <ReportModal
          target={{ kind: 'comment', id: reporting }}
          onClose={() => {
            setReporting(null)
            load()
          }}
        />
      )}
    </section>
  )
}

/** 댓글·답글 입력칸. 비밀 댓글 체크는 기본 꺼짐이고, 비밀 댓글에 다는 답글은 항상 비밀이다 (FR-066) */
function CommentForm({
  postId,
  parent,
  onSaved,
  onCancel,
}: {
  postId: number
  parent?: CommentView
  onSaved: () => Promise<void> | void
  onCancel?: () => void
}) {
  const [content, setContent] = useState('')
  const [secret, setSecret] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const forcedSecret = parent?.secret ?? false

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
      await post(`/api/posts/${postId}/comments`, { content, secret: secret || forcedSecret, parentId: parent?.id ?? null })
      setContent('')
      setSecret(false)
      await onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.fieldMessage('content') ?? err.message : '잠시 뒤 다시 시도해 주세요')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className={parent ? 'comment-form reply-form' : 'comment-form'} onSubmit={submit}>
      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        maxLength={COMMENT_MAX}
        rows={parent ? 2 : 3}
        aria-label={parent ? '답글' : '댓글'}
        placeholder={parent ? `${parent.authorNickname ?? M.withdrawnUser}님에게 답글 남기기` : '댓글을 남겨 보세요'}
        autoFocus={!!parent}
      />
      <div className="row">
        <span className="comment-form-left">
          <label className="check">
            <input type="checkbox" checked={secret || forcedSecret} disabled={forcedSecret} onChange={(e) => setSecret(e.target.checked)} /> 비밀 댓글
          </label>
          {forcedSecret && <span className="faint small">비밀 댓글의 답글은 비밀 댓글이 됩니다</span>}
        </span>
        <span className="comment-form-right">
          <span className="faint small">
            {[...content].length} / {COMMENT_MAX}
          </span>
          {onCancel && (
            <button type="button" onClick={onCancel}>
              취소
            </button>
          )}
          <button className="primary" disabled={busy}>
            등록
          </button>
        </span>
      </div>
      {error && <p className="error">{error}</p>}
    </form>
  )
}
