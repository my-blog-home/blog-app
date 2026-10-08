import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  MEMBER_STATUS_LABEL,
  SUSPEND_OPTIONS,
  type MemberPage,
  type MemberRow,
  type MemberStatus,
  type SuspensionHistory,
} from '../../api/adminTypes'
import { del, get, post } from '../../api/client'
import Pagination from '../../components/Pagination'
import { formatDate, formatDateTime } from '../../format'
import { AdminModal, CountTabs, charCount, errorOf, fieldError } from './adminShared'

const STATUSES: MemberStatus[] = ['ALL', 'ACTIVE', 'SUSPENDED', 'WITHDRAWN']
const REASON_MAX = 200

const parseStatus = (raw: string | null): MemberStatus => (STATUSES.includes(raw as MemberStatus) ? (raw as MemberStatus) : 'ALL')

const memberName = (m: MemberRow) => m.nickname ?? `탈퇴한 회원 #${m.id}`

/** 정지가 끝나는 때: 영구 또는 날짜·시각 */
const until = (endsAt: string | null) => (endsAt ? `${formatDateTime(endsAt)}까지` : '영구 정지')

/**
 * 회원 관리 (FR-080, FR-081). 상태 탭(전체·활동 중·정지·탈퇴), 닉네임·이메일 검색, 최근 가입 순 10명씩.
 * 탈퇴한 회원은 닉네임·이메일을 보여 주지 않는다. 정지·해제는 창에서 받은 신고 수와 정지 이력을 보고 정한다.
 */
export default function AdminMembersPage() {
  const [params, setParams] = useSearchParams()
  const status = parseStatus(params.get('status'))
  const q = params.get('q') ?? ''
  const page = Number(params.get('page') ?? '1') || 1
  const [query, setQuery] = useState(q)
  const [data, setData] = useState<MemberPage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [selected, setSelected] = useState<MemberRow | null>(null)

  const load = useCallback(async () => {
    try {
      const search = new URLSearchParams({ status, page: String(page) })
      if (q) search.set('q', q)
      setData(await get<MemberPage>(`/api/admin/members?${search}`))
      setError(null)
    } catch (e) {
      setError(errorOf(e))
    }
  }, [status, q, page])

  useEffect(() => {
    load()
  }, [load])

  const go = (next: { status?: MemberStatus; q?: string; page?: number }) => {
    const search: Record<string, string> = { status: next.status ?? status }
    const nextQ = next.q ?? q
    if (nextQ) search.q = nextQ
    if (next.page && next.page > 1) search.page = String(next.page)
    setParams(search)
  }

  const submitSearch = (e: FormEvent) => {
    e.preventDefault()
    setNotice(null)
    go({ q: query.trim(), page: 1 })
  }

  return (
    <>
      <h1>회원 관리</h1>
      <CountTabs
        keys={STATUSES}
        labels={MEMBER_STATUS_LABEL}
        counts={data?.counts ?? null}
        current={status}
        label="회원 상태"
        onChange={(s) => {
          setNotice(null)
          go({ status: s, page: 1 })
        }}
      />
      <form className="filters" onSubmit={submitSearch} role="search">
        <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="닉네임 또는 이메일" aria-label="회원 검색" className="grow" />
        <button type="submit">검색</button>
        {q && (
          <button
            type="button"
            className="link muted"
            onClick={() => {
              setQuery('')
              go({ q: '', page: 1 })
            }}
          >
            검색 지우기
          </button>
        )}
      </form>
      {notice && <p className="notice">{notice}</p>}
      {error && <p className="error">{error}</p>}
      {data &&
        (data.items.length === 0 ? (
          <p className="empty">{q ? '검색 결과가 없습니다' : '회원이 없습니다'}</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table admin-table">
              <thead>
                <tr>
                  <th>회원</th>
                  <th>가입일</th>
                  <th>블로그</th>
                  <th>공개 글</th>
                  <th>받은 신고</th>
                  <th>상태</th>
                  <th>
                    <span className="sr-only">작업</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((m) => (
                  <tr key={m.id}>
                    <td className="wrap">
                      {memberName(m)}
                      {m.role === 'ADMIN' && <span className="badge notice-badge">관리자</span>}
                      {m.email && <div className="faint small">{m.email}</div>}
                    </td>
                    <td>{formatDate(m.joinedAt)}</td>
                    <td className="wrap">{m.blog ? <Link to={`/blogs/${m.blog.id}`}>{m.blog.name}</Link> : <span className="faint">-</span>}</td>
                    <td>{m.postCount.toLocaleString()}</td>
                    <td>
                      {m.reportCount.toLocaleString()}
                      {m.resolvedReportCount > 0 && <span className="faint small"> (처리 완료 {m.resolvedReportCount})</span>}
                    </td>
                    <td className="wrap">
                      <MemberStatusBadge member={m} />
                      {m.suspension && <div className="faint small">{until(m.suspension.endsAt)}</div>}
                      {m.withdrawnAt && <div className="faint small">{formatDate(m.withdrawnAt)} 탈퇴</div>}
                    </td>
                    <td>
                      {m.canSuspend && (
                        <button type="button" className={m.status === 'SUSPENDED' ? 'sm' : 'sm primary'} onClick={() => setSelected(m)}>
                          {m.status === 'SUSPENDED' ? '정지 관리' : '정지'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => go({ page: p })} />}
      {selected && (
        <SuspendDialog
          member={selected}
          onClose={() => setSelected(null)}
          onDone={async (message) => {
            setSelected(null)
            setNotice(message)
            await load()
          }}
        />
      )}
    </>
  )
}

function MemberStatusBadge({ member }: { member: MemberRow }) {
  const cls = member.status === 'SUSPENDED' ? 'status-suspended' : member.status === 'WITHDRAWN' ? 'status-withdrawn' : 'status-active'
  return <span className={`badge ${cls}`}>{MEMBER_STATUS_LABEL[member.status]}</span>
}

/** 정지·해제 창: 받은 신고 수, 정지 이력, 지금 정지(있으면 해제), 새 정지(기간·사유) */
function SuspendDialog({ member, onClose, onDone }: { member: MemberRow; onClose: () => void; onDone: (message: string) => void }) {
  const [history, setHistory] = useState<SuspensionHistory[] | null>(null)
  const [days, setDays] = useState<string>('3')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<{ field?: string; text: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const name = memberName(member)

  useEffect(() => {
    get<SuspensionHistory[]>(`/api/admin/members/${member.id}/suspensions`)
      .then(setHistory)
      .catch((e) => setError({ text: errorOf(e) }))
  }, [member.id])

  const suspend = async (e: FormEvent) => {
    e.preventDefault()
    if (charCount(reason.trim()) > REASON_MAX) {
      setError({ field: 'reason', text: `정지 사유는 ${REASON_MAX}자 이하로 입력해 주세요` })
      return
    }
    const label = days === 'P' ? '영구 정지' : `${days}일 정지`
    if (!confirm(`${name}님을 ${label}합니다. 정지하면 바로 로그아웃되고 로그인할 수 없습니다. 정지할까요?`)) return
    setBusy(true)
    try {
      await post(`/api/admin/members/${member.id}/suspensions`, { days: days === 'P' ? null : Number(days), reason: reason.trim() })
      onDone(`${name}님을 ${label}했습니다`)
    } catch (err) {
      setError({ field: fieldError(err, 'reason') ? 'reason' : undefined, text: errorOf(err) })
    } finally {
      setBusy(false)
    }
  }

  const lift = async () => {
    if (!confirm(`${name}님의 정지를 해제할까요?`)) return
    setBusy(true)
    try {
      await del(`/api/admin/members/${member.id}/suspensions`)
      onDone(`${name}님의 정지를 해제했습니다`)
    } catch (err) {
      setError({ text: errorOf(err) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminModal title={member.status === 'SUSPENDED' ? '정지 관리' : '회원 정지'} wide onClose={onClose}>
      <div className="admin-quote">
        <div>
          <strong>{name}</strong> {member.email && <span className="faint small">{member.email}</span>}
        </div>
        <div className="muted small">
          받은 신고 {member.reportCount}건 (처리 완료 {member.resolvedReportCount}건) · 정지 이력 {member.suspensionCount}회 · 공개 글{' '}
          {member.postCount}개
        </div>
      </div>

      {member.suspension && (
        <div className="warning suspend-now">
          <p>
            <strong>지금 정지 중</strong> · {formatDateTime(member.suspension.startsAt)} 부터 {until(member.suspension.endsAt)}
          </p>
          {member.suspension.reason && <p>사유: {member.suspension.reason}</p>}
          <button type="button" className="sm" onClick={lift} disabled={busy}>
            정지 해제
          </button>
        </div>
      )}

      <h3 className="dialog-sub">정지 이력</h3>
      {history == null ? (
        <p className="muted small">불러오는 중…</p>
      ) : history.length === 0 ? (
        <p className="muted small">정지 이력이 없습니다</p>
      ) : (
        <ul className="mini-list">
          {history.map((h) => (
            <li key={h.id}>
              <span>
                {formatDate(h.startsAt)} · {h.permanent ? '영구' : `${until(h.endsAt)}`}
                {h.reason ? ` · ${h.reason}` : ''}
                {h.reportId ? ' · 신고 처리' : ''}
              </span>
              <span className="faint">{h.active ? '정지 중' : h.liftedAt ? `해제 ${formatDate(h.liftedAt)}${h.liftedBy ? ` (${h.liftedBy})` : ''}` : '끝남'}</span>
            </li>
          ))}
        </ul>
      )}

      <form className="form" onSubmit={suspend} noValidate>
        <h3 className="dialog-sub">{member.suspension ? '정지 더하기' : '정지하기'}</h3>
        <p className="muted small">정지하면 로그인할 수 없고, 로그인해 둔 곳에서도 바로 로그아웃됩니다. 쓴 글과 댓글은 그대로 보입니다. 겹치면 가장 오래 가는 정지가 적용됩니다.</p>
        <label>
          정지 기간
          <select value={days} onChange={(e) => setDays(e.target.value)}>
            {SUSPEND_OPTIONS.map((o) => (
              <option key={o.label} value={o.value == null ? 'P' : String(o.value)}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          정지 사유 (선택)
          <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} placeholder="예: 광고성 댓글을 반복해서 작성함. 정지된 본인에게 보입니다" />
          <span className={`field-count ${charCount(reason.trim()) > REASON_MAX ? 'over' : ''}`}>
            {charCount(reason.trim())} / {REASON_MAX}
          </span>
        </label>
        {error && <p className="error">{error.text}</p>}
        <div className="modal-foot">
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button className="primary danger-bg" disabled={busy}>
            정지하기
          </button>
        </div>
      </form>
    </AdminModal>
  )
}
