import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  REPORT_STATUS_LABEL,
  SUSPEND_OPTIONS,
  type ProcessResult,
  type ReportGroup,
  type ReportPage,
  type ReportStatusFilter,
  type SuspensionHistory,
} from '../../api/adminTypes'
import { ApiError, get, patch } from '../../api/client'
import Pagination from '../../components/Pagination'
import { formatDate, formatDateTime } from '../../format'
import { useAdmin } from './AdminLayout'
import { AdminModal, CountTabs, StatusBadge, TargetText, charCount, errorOf, fieldError, short, targetKind } from './adminShared'

const STATUSES: ReportStatusFilter[] = ['PENDING', 'RESOLVED', 'REJECTED', 'ALL']
const NOTE_MAX = 200
const REASON_MAX = 200

const parseStatus = (raw: string | null): ReportStatusFilter =>
  STATUSES.includes(raw as ReportStatusFilter) ? (raw as ReportStatusFilter) : 'PENDING'

/**
 * 신고 처리 (FR-079, BR-48). 같은 글·댓글의 신고는 한 줄로 모아 보여 주고 한 번에 처리한다.
 * 상태 탭(처리 대기·처리 완료·반려·전체)에 신고 건수를 붙이고, 한 쪽에 10줄씩 보여 준다.
 */
export default function AdminReportsPage() {
  const { refreshBadge } = useAdmin()
  const [params, setParams] = useSearchParams()
  const status = parseStatus(params.get('status'))
  const page = Number(params.get('page') ?? '1') || 1
  const [data, setData] = useState<ReportPage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [handling, setHandling] = useState<ReportGroup | null>(null)

  const load = useCallback(async () => {
    try {
      setData(await get<ReportPage>(`/api/admin/reports?status=${status}&page=${page}`))
      setError(null)
    } catch (e) {
      setError(errorOf(e))
    }
  }, [status, page])

  useEffect(() => {
    load()
  }, [load])

  const done = async (message: string) => {
    setHandling(null)
    setNotice(message)
    await load()
    await refreshBadge()
  }

  return (
    <>
      <h1>신고 처리</h1>
      <CountTabs
        keys={STATUSES}
        labels={REPORT_STATUS_LABEL}
        counts={data?.counts ?? null}
        current={status}
        label="처리 상태"
        onChange={(s) => {
          setNotice(null)
          setParams({ status: s })
        }}
      />
      <p className="muted small">같은 글·댓글에 들어온 신고는 한 줄로 모아 보여 주고, 한 번에 처리합니다. 처리 완료하면 신고된 글·댓글이 삭제됩니다.</p>
      {notice && <p className="notice">{notice}</p>}
      {error && <p className="error">{error}</p>}
      {data &&
        (data.items.length === 0 ? (
          <p className="empty">{status === 'PENDING' ? '처리 대기 중인 신고가 없습니다' : '신고가 없습니다'}</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table admin-table">
              <thead>
                <tr>
                  <th>신고된 내용</th>
                  <th>대상</th>
                  <th>사유</th>
                  <th>신고자 → 작성자</th>
                  <th>최근 신고</th>
                  <th>상태</th>
                  <th>
                    <span className="sr-only">처리</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((g) => (
                  <tr key={g.key}>
                    <td className="wrap">
                      <TargetText group={g} />
                      {g.targetType === 'COMMENT' && g.postTitle && <div className="faint small">글: {short(g.postTitle, 30)}</div>}
                    </td>
                    <td>
                      {targetKind(g)}
                      {g.reportCount > 1 && <span className="badge status-pending">신고 {g.reportCount}건</span>}
                    </td>
                    <td className="wrap">
                      {g.reasonSummary}
                      {g.reportCount === 1 && g.reports[0].detail && <div className="faint small">{short(g.reports[0].detail, 40)}</div>}
                    </td>
                    <td className="wrap">
                      {g.reporterSummary} → {g.targetAuthor.nickname}
                      {g.targetAuthor.suspended && <span className="badge status-suspended">정지 중</span>}
                    </td>
                    <td>{formatDateTime(g.latestAt)}</td>
                    <td className="wrap">
                      <StatusBadge status={g.status} />
                      {g.status !== 'PENDING' && (
                        <div className="faint small">
                          {g.handledBy ?? ''} {g.handledAt ? `· ${formatDateTime(g.handledAt)}` : ''}
                          {g.handleNote && <div>메모: {short(g.handleNote, 40)}</div>}
                        </div>
                      )}
                    </td>
                    <td>
                      {g.status === 'PENDING' && (
                        <button type="button" className="sm primary" onClick={() => setHandling(g)}>
                          처리
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => setParams({ status, page: String(p) })} />}
      {handling && (
        <ProcessDialog
          group={handling}
          onClose={() => setHandling(null)}
          onDone={done}
          onStale={async (message) => {
            setHandling(null)
            setError(message)
            await load()
            await refreshBadge()
          }}
        />
      )}
    </>
  )
}

/** 신고 처리 창: 처리 완료(대상 삭제)·반려, 처리 메모, 작성자 정지(처리 완료일 때만, 선택) */
function ProcessDialog({
  group,
  onClose,
  onDone,
  onStale,
}: {
  group: ReportGroup
  onClose: () => void
  onDone: (message: string) => void
  onStale: (message: string) => void
}) {
  const kind = targetKind(group)
  const [action, setAction] = useState<'RESOLVE' | 'REJECT'>('RESOLVE')
  const [note, setNote] = useState('')
  const [suspend, setSuspend] = useState<string>('') // '' = 정지하지 않음, 'P' = 영구, 숫자 = 일수
  const [reason, setReason] = useState('')
  const [history, setHistory] = useState<SuspensionHistory[] | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const canSuspend = group.canSuspend && action === 'RESOLVE'

  useEffect(() => {
    if (group.canSuspend && group.targetAuthor.id != null)
      get<SuspensionHistory[]>(`/api/admin/members/${group.targetAuthor.id}/suspensions`)
        .then(setHistory)
        .catch(() => setHistory(null))
  }, [group])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const next: Record<string, string> = {}
    if (charCount(note.trim()) > NOTE_MAX) next.note = `메모는 ${NOTE_MAX}자 이하로 입력해 주세요`
    if (canSuspend && suspend && charCount(reason.trim()) > REASON_MAX) next.reason = `정지 사유는 ${REASON_MAX}자 이하로 입력해 주세요`
    setErrors(next)
    if (Object.keys(next).length > 0) return
    if (action === 'RESOLVE' && group.exists && !confirm(`신고된 ${kind}을(를) 삭제합니다. 삭제하면 되돌릴 수 없습니다. 처리할까요?`)) return

    const body: Record<string, unknown> = { reportIds: group.reportIds, action, note: note.trim() }
    if (canSuspend && suspend) body.suspend = { days: suspend === 'P' ? null : Number(suspend), reason: reason.trim() }
    setBusy(true)
    try {
      const result = await patch<ProcessResult>('/api/admin/reports', body)
      onDone(result.message)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409 && err.code === 'ALREADY_HANDLED') {
        onStale(err.message)
        return
      }
      const noteError = fieldError(err, 'note')
      const reasonError = fieldError(err, 'reason') ?? fieldError(err, 'days')
      setErrors({ note: noteError ?? '', reason: reasonError ?? '', form: noteError || reasonError ? '' : errorOf(err) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminModal title={group.reportCount > 1 ? `신고 처리 · ${group.reportCount}건` : '신고 처리'} wide onClose={onClose}>
      <form className="form" onSubmit={submit} noValidate>
        <div className="admin-quote">
          <div>
            <strong>{kind}</strong> · 작성자 {group.targetAuthor.nickname}
            {group.targetAuthor.suspended && <span className="badge status-suspended">정지 중</span>}
          </div>
          <div className="quote-text">
            <TargetText group={group} max={120} />
          </div>
          <ul className="mini-list">
            {group.reports.map((r) => (
              <li key={r.id}>
                <span>
                  {r.reporterNickname ?? '탈퇴한 사용자'} · {r.reasonLabel}
                  {r.detail ? ` (${short(r.detail, 60)})` : ''}
                </span>
                <span className="faint">{formatDateTime(r.createdAt)}</span>
              </li>
            ))}
          </ul>
        </div>

        <div className="field">
          <span>처리 결과</span>
          <div className="radio-row">
            <label>
              <input type="radio" name="action" checked={action === 'RESOLVE'} onChange={() => setAction('RESOLVE')} />
              {group.exists ? `처리 완료 (${kind} 삭제)` : '처리 완료 (이미 삭제됨)'}
            </label>
            <label>
              <input type="radio" name="action" checked={action === 'REJECT'} onChange={() => setAction('REJECT')} />
              반려 (문제 없음)
            </label>
          </div>
          <span className="muted small">
            {action === 'RESOLVE'
              ? group.exists
                ? `이 ${kind}을(를) 삭제하고 신고 ${group.reportCount}건을 모두 처리 완료로 바꿉니다.`
                : `${kind}은(는) 이미 삭제되었습니다. 신고 ${group.reportCount}건을 처리 완료로 기록합니다.`
              : `${kind}은(는) 그대로 두고 신고 ${group.reportCount}건을 모두 반려합니다.`}
          </span>
        </div>

        {action === 'RESOLVE' && (
          <div className="field">
            <span>
              작성자 정지 <span className="faint">(선택)</span>
            </span>
            {group.canSuspend ? (
              <>
                {history && (
                  <span className="muted small">
                    {group.targetAuthor.nickname}님의 정지 이력 {history.length}회
                    {history[0] ? ` · 최근 ${formatDate(history[0].startsAt)} ${history[0].permanent ? '영구' : ''}` : ''}
                  </span>
                )}
                <select value={suspend} onChange={(e) => setSuspend(e.target.value)} aria-label="작성자 정지 기간">
                  <option value="">정지하지 않음</option>
                  {SUSPEND_OPTIONS.map((o) => (
                    <option key={o.label} value={o.value == null ? 'P' : String(o.value)}>
                      {o.value == null ? '영구 정지' : `${o.label} 정지`}
                    </option>
                  ))}
                </select>
                {suspend && (
                  <input
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    placeholder="정지 사유 (정지된 본인에게 보입니다, 선택)"
                    aria-label="정지 사유"
                  />
                )}
                {errors.reason && <p className="error">{errors.reason}</p>}
              </>
            ) : (
              <span className="muted small">
                {group.targetAuthor.withdrawn
                  ? '탈퇴한 회원은 정지할 수 없습니다'
                  : group.targetAuthor.admin
                    ? '관리자는 정지할 수 없습니다'
                    : '정지할 수 없는 작성자입니다'}
              </span>
            )}
          </div>
        )}

        <label>
          처리 메모 (선택)
          <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={3} placeholder="예: 광고 댓글이라 삭제함" />
          <span className={`field-count ${charCount(note.trim()) > NOTE_MAX ? 'over' : ''}`}>
            {charCount(note.trim())} / {NOTE_MAX}
          </span>
        </label>
        {errors.note && <p className="error">{errors.note}</p>}
        {errors.form && <p className="error">{errors.form}</p>}
        <div className="modal-foot">
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button className="primary" disabled={busy}>
            처리하기
          </button>
        </div>
      </form>
    </AdminModal>
  )
}
