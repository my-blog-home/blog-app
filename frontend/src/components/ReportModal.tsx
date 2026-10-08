import { useState, type FormEvent } from 'react'
import { ApiError, post } from '../api/client'

const REASONS = [
  { value: 'SPAM', label: '스팸' },
  { value: 'ABUSE', label: '욕설·혐오' },
  { value: 'ADULT', label: '음란물' },
  { value: 'ETC', label: '기타' },
]

/** 신고 대상: 글 또는 댓글 (FR-071) */
export type ReportTarget = { kind: 'post' | 'comment'; id: number }

/** 신고: 사유를 고르고 기타면 설명을 쓴다. 글·댓글이 같은 창을 쓴다 (CF-21, FR-071). 접수되면 onDone을 부른다 */
export default function ReportModal({ target, onClose, onDone }: { target: ReportTarget; onClose: () => void; onDone?: () => void }) {
  const [reason, setReason] = useState('SPAM')
  const [detail, setDetail] = useState('')
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    try {
      const url = target.kind === 'post' ? `/api/posts/${target.id}/reports` : `/api/comments/${target.id}/reports`
      const res = await post<{ message: string }>(url, { reason, detail })
      setMessage({ ok: true, text: res.message })
      onDone?.()
    } catch (err) {
      setMessage({ ok: false, text: err instanceof ApiError ? err.fieldErrors[0]?.message ?? err.message : '잠시 뒤 다시 시도해 주세요' })
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" role="dialog" aria-label={target.kind === 'post' ? '글 신고하기' : '댓글 신고하기'} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h2>{target.kind === 'post' ? '글 신고하기' : '댓글 신고하기'}</h2>
          <button className="link" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </div>
        {message?.ok ? (
          <p className="notice">{message.text}</p>
        ) : (
          <form className="form" onSubmit={submit}>
            {REASONS.map((r) => (
              <label key={r.value} className="check">
                <input type="radio" name="reason" checked={reason === r.value} onChange={() => setReason(r.value)} /> {r.label}
              </label>
            ))}
            {reason === 'ETC' && <textarea value={detail} onChange={(e) => setDetail(e.target.value)} maxLength={200} rows={3} placeholder="설명 (선택, 200자 이내)" aria-label="신고 설명" />}
            {message && <p className="error">{message.text}</p>}
            <button className="primary">신고</button>
          </form>
        )}
      </div>
    </div>
  )
}
