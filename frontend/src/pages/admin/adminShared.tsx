import { useEffect, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { REPORT_STATUS_LABEL, type ReportGroup, type ReportStatus } from '../../api/adminTypes'

export const errorOf = (e: unknown) => (e instanceof ApiError ? e.fieldErrors[0]?.message ?? e.message : '잠시 뒤 다시 시도해 주세요')

export const fieldError = (e: unknown, field: string) => (e instanceof ApiError ? e.fieldMessage(field) : undefined)

/** 긴 글을 n자에서 자르고 …을 붙인다 */
export const short = (text: string, n: number) => {
  const chars = [...text]
  return chars.length > n ? chars.slice(0, n).join('') + '…' : text
}

export const charCount = (text: string) => [...text].length

export const targetKind = (g: ReportGroup) => (g.targetType === 'POST' ? '글' : '댓글')

/** 신고된 글·댓글로 가는 주소. 지워졌으면 없다 */
export function targetHref(g: ReportGroup): string | null {
  if (!g.exists || g.postId == null) return null
  return g.targetType === 'COMMENT' && g.commentId != null ? `/posts/${g.postId}#comment-${g.commentId}` : `/posts/${g.postId}`
}

export function StatusBadge({ status }: { status: ReportStatus }) {
  return <span className={`badge status-${status.toLowerCase()}`}>{REPORT_STATUS_LABEL[status]}</span>
}

/** 신고된 내용: 링크(대상이 남아 있을 때)와 "삭제됨" 표시 */
export function TargetText({ group, max = 60 }: { group: ReportGroup; max?: number }) {
  const href = targetHref(group)
  const text = short(group.targetText || '(내용 없음)', max)
  return (
    <>
      {href ? <Link to={href}>{text}</Link> : <span>{text}</span>}
      {!group.exists && <span className="badge"> 삭제됨</span>}
    </>
  )
}

/** 관리자 화면의 창. 바깥을 누르거나 ESC로 닫는다 */
export function AdminModal({ title, wide, onClose, children }: { title: string; wide?: boolean; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className={wide ? 'modal wide' : 'modal'} role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button type="button" className="link" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

/** 상태 탭: 이름 옆에 건수를 붙인다 */
export function CountTabs<K extends string>({
  keys,
  labels,
  counts,
  current,
  onChange,
  label,
}: {
  keys: K[]
  labels: Record<K, string>
  counts: Record<K, number> | null
  current: K
  onChange: (key: K) => void
  label: string
}) {
  return (
    <div className="tabs" role="tablist" aria-label={label}>
      {keys.map((k) => (
        <button key={k} type="button" role="tab" aria-selected={current === k} className={current === k ? 'current' : ''} onClick={() => onChange(k)}>
          {labels[k]} <span className="tab-count">{counts ? counts[k].toLocaleString() : ''}</span>
        </button>
      ))}
    </div>
  )
}
