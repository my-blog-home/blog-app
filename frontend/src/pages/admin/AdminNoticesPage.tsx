import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { del, get, patch, post } from '../../api/client'
import type { NoticeDetail, NoticePage, NoticeType } from '../../api/types'
import Pagination from '../../components/Pagination'
import PlainText from '../../components/PlainText'
import { formatDate } from '../../format'
import { AdminModal, charCount, errorOf, fieldError } from './adminShared'

const TITLE_MAX = 100
const CONTENT_MAX = 5000
const TYPE_LABEL: Record<NoticeType, string> = { NOTICE: '공지', GUIDE: '이용법' }

interface Editing {
  id: number | null
  type: NoticeType
  title: string
  content: string
  pinned: boolean
}

/** 공지 관리: 목록, 쓰기·고치기(미리 보기), 지우기 (FR-082) */
export default function AdminNoticesPage() {
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? '1') || 1
  const [data, setData] = useState<NoticePage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [editing, setEditing] = useState<Editing | null>(null)

  const load = useCallback(async () => {
    try {
      setData(await get<NoticePage>(`/api/notices?page=${page}&size=10`))
      setError(null)
    } catch (e) {
      setError(errorOf(e))
    }
  }, [page])

  useEffect(() => {
    load()
  }, [load])

  const edit = async (id: number) => {
    try {
      const d = await get<NoticeDetail>(`/api/notices/${id}`)
      setEditing({ id: d.id, type: d.type, title: d.title, content: d.content, pinned: d.pinned })
    } catch (e) {
      setError(errorOf(e))
    }
  }

  const remove = async (id: number, title: string) => {
    if (!confirm(`"${title}"을(를) 삭제합니다. 삭제하면 되돌릴 수 없습니다. 삭제할까요?`)) return
    try {
      await del(`/api/admin/notices/${id}`)
      setNotice('공지를 삭제했습니다')
      await load()
    } catch (e) {
      setError(errorOf(e))
    }
  }

  return (
    <>
      <h1 className="row-between">
        공지 관리
        <button type="button" className="primary sm" onClick={() => setEditing({ id: null, type: 'NOTICE', title: '', content: '', pinned: false })}>
          새 공지 쓰기
        </button>
      </h1>
      {notice && <p className="notice">{notice}</p>}
      {error && <p className="error">{error}</p>}
      {data &&
        (data.items.length === 0 ? (
          <p className="empty">등록된 안내가 없습니다.</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table admin-table">
              <thead>
                <tr>
                  <th>제목</th>
                  <th>종류</th>
                  <th>쓴 날</th>
                  <th>
                    <span className="sr-only">작업</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((n) => (
                  <tr key={n.id}>
                    <td className="wrap">
                      {n.pinned && <span className="badge status-pending">고정</span>} <Link to={`/notices/${n.id}`}>{n.title}</Link>
                    </td>
                    <td>
                      <span className={`badge ${n.type === 'NOTICE' ? 'notice-badge' : 'guide-badge'}`}>{TYPE_LABEL[n.type]}</span>
                    </td>
                    <td>{formatDate(n.createdAt)}</td>
                    <td>
                      <div className="actions-cell">
                        <button type="button" className="link" onClick={() => edit(n.id)}>
                          수정
                        </button>
                        <button type="button" className="link danger" onClick={() => remove(n.id, n.title)}>
                          삭제
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => setParams({ page: String(p) })} />}
      {editing && (
        <NoticeEditor
          initial={editing}
          onClose={() => setEditing(null)}
          onSaved={async (message) => {
            setEditing(null)
            setNotice(message)
            await load()
          }}
        />
      )}
    </>
  )
}

/** 공지 쓰기·고치기 창. 내용은 글자만 저장하고(HTML로 해석하지 않음) 빈 줄로 문단을 나눈다 */
function NoticeEditor({ initial, onClose, onSaved }: { initial: Editing; onClose: () => void; onSaved: (message: string) => void }) {
  const [form, setForm] = useState<Editing>(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const titleLength = charCount(form.title.trim())
  const contentLength = charCount(form.content.trim())

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const next: Record<string, string> = {}
    if (titleLength < 1 || titleLength > TITLE_MAX) next.title = `제목은 1~${TITLE_MAX}자로 입력해 주세요`
    if (contentLength < 1 || contentLength > CONTENT_MAX) next.content = `내용은 1~${CONTENT_MAX.toLocaleString()}자로 입력해 주세요`
    setErrors(next)
    if (Object.keys(next).length > 0) return
    const body = { type: form.type, title: form.title.trim(), content: form.content.trim(), pinned: form.pinned }
    setBusy(true)
    try {
      if (form.id == null) {
        await post('/api/admin/notices', body)
        onSaved('공지를 등록했습니다')
      } else {
        await patch(`/api/admin/notices/${form.id}`, body)
        onSaved('공지를 고쳤습니다')
      }
    } catch (err) {
      setErrors({
        type: fieldError(err, 'type') ?? '',
        title: fieldError(err, 'title') ?? '',
        content: fieldError(err, 'content') ?? '',
        form: fieldError(err, 'type') || fieldError(err, 'title') || fieldError(err, 'content') ? '' : errorOf(err),
      })
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminModal title={form.id == null ? '공지 쓰기' : '공지 고치기'} wide onClose={onClose}>
      <form className="form" onSubmit={submit} noValidate>
        <label>
          종류
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as NoticeType })}>
            <option value="NOTICE">공지</option>
            <option value="GUIDE">이용법</option>
          </select>
        </label>
        {errors.type && <p className="error">{errors.type}</p>}
        <label>
          제목
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="제목을 입력해 주세요" />
          <span className={`field-count ${titleLength > TITLE_MAX ? 'over' : ''}`}>
            {titleLength} / {TITLE_MAX}
          </span>
        </label>
        {errors.title && <p className="error">{errors.title}</p>}
        <label>
          내용
          <textarea
            value={form.content}
            onChange={(e) => setForm({ ...form, content: e.target.value })}
            rows={9}
            placeholder="내용을 입력해 주세요. 빈 줄로 문단을 나눕니다."
          />
          <span className="muted small">글자만 저장합니다. HTML 태그는 글자 그대로 보입니다.</span>
          <span className={`field-count ${contentLength > CONTENT_MAX ? 'over' : ''}`}>
            {contentLength.toLocaleString()} / {CONTENT_MAX.toLocaleString()}
          </span>
        </label>
        {errors.content && <p className="error">{errors.content}</p>}
        <label className="check">
          <input type="checkbox" checked={form.pinned} onChange={(e) => setForm({ ...form, pinned: e.target.checked })} /> 목록 맨 위에 고정합니다
        </label>
        <div className="field">
          <span>미리 보기</span>
          <div className="admin-preview">
            {form.title.trim() && <h3>{form.title.trim()}</h3>}
            {form.content.trim() ? <PlainText text={form.content} /> : <p className="faint small">내용을 쓰면 여기에 보입니다</p>}
          </div>
        </div>
        {errors.form && <p className="error">{errors.form}</p>}
        <div className="modal-foot">
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button className="primary" disabled={busy}>
            저장하기
          </button>
        </div>
      </form>
    </AdminModal>
  )
}
