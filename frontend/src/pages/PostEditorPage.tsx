import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useBlocker, useNavigate, useParams } from 'react-router-dom'
import { ApiError, get, post as httpPost, put } from '../api/client'
import type { BlogView, PostSource, Visibility } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import ImageUploadButton from '../components/ImageUploadButton'
import MarkdownView from '../components/MarkdownView'
import TagInput from '../components/TagInput'
import { M } from '../messages'
import NotFoundPage from './NotFoundPage'

interface Draft {
  title: string
  body: string
  categoryId: number | null
  visibility: Visibility
  tags: string[]
}

/** 글쓰기·글 수정 (CF-05, CF-13). 권한은 서버가 최종 확인한다 */
export default function PostEditorPage() {
  const { postId } = useParams()
  const editing = postId !== undefined
  const { me, loading, requireLogin } = useAuth()
  const navigate = useNavigate()
  const [blog, setBlog] = useState<BlogView | null>(null)
  const [draft, setDraft] = useState<Draft>({ title: '', body: '', categoryId: null, visibility: 'PUBLIC', tags: [] })
  const [saved, setSaved] = useState<Draft | null>(null)
  const [originalVisibility, setOriginalVisibility] = useState<Visibility>('PUBLIC')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [missing, setMissing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [preview, setPreview] = useState(false)
  const done = useRef(false)

  useEffect(() => {
    if (loading) return
    if (!me) {
      requireLogin(() => undefined)
      return
    }
    const load = async () => {
      try {
        let blogId = me.blogId
        let initial: Draft | null = null
        if (editing) {
          const source = await get<PostSource>(`/api/posts/${postId}/edit`)
          blogId = source.blogId
          initial = { title: source.title, body: source.body, categoryId: source.categoryId, visibility: source.visibility, tags: source.tags }
          setOriginalVisibility(source.visibility)
        }
        const view = await get<BlogView>(`/api/blogs/${blogId}`)
        setBlog(view)
        if (!initial) {
          // 분류 기본값은 마지막에 쓴 글의 분류, 처음이면 "미분류" (CF-05-5)
          const fallback = view.categories.find((c) => c.isDefault)?.id ?? null
          initial = { title: '', body: '', categoryId: view.lastUsedCategoryId ?? fallback, visibility: 'PUBLIC', tags: [] }
        }
        setDraft(initial)
        setSaved(initial)
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) setMissing(true)
      }
    }
    load()
  }, [loading, me, editing, postId, requireLogin])

  const dirty = saved !== null && JSON.stringify(saved) !== JSON.stringify(draft)

  // 입력하던 중 나가려 하면 묻는다 (CF-05-10)
  const blocker = useBlocker(({ currentLocation, nextLocation }) =>
    dirty && !done.current && currentLocation.pathname !== nextLocation.pathname)
  useEffect(() => {
    if (blocker.state === 'blocked') {
      if (confirm(M.leaveConfirm)) blocker.proceed()
      else blocker.reset()
    }
  }, [blocker])
  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => {
      if (dirty && !done.current) e.preventDefault()
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  if (missing) return <NotFoundPage message={M.postNotFound} />
  if (loading || !me) return <p className="empty">로그인이 필요합니다</p>
  if (!blog) return null

  const update = <K extends keyof Draft>(key: K, value: Draft[K]) => setDraft({ ...draft, [key]: value })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (saving) return
    const next: Record<string, string> = {}
    if (!draft.title.trim()) next.title = M.titleRequired
    if (!draft.body.trim()) next.body = M.bodyRequired
    setErrors(next)
    if (Object.keys(next).length > 0) return
    if (draft.visibility === 'PUBLIC' && originalVisibility === 'PRIVATE' && !confirm(M.makePublicConfirm)) return

    setSaving(true)
    try {
      const result = editing
        ? await put<{ id: number }>(`/api/posts/${postId}`, draft)
        : await httpPost<{ id: number }>(`/api/blogs/${blog.id}/posts`, draft)
      done.current = true
      navigate(`/posts/${result.id}`, { replace: true })
    } catch (err) {
      // 저장에 실패하면 입력한 내용을 그대로 둔다 (CF-05-8)
      if (err instanceof ApiError) {
        const fields: Record<string, string> = {}
        err.fieldErrors.forEach((f) => (fields[f.field] = f.message))
        setErrors(Object.keys(fields).length > 0 ? fields : { form: err.message })
      } else {
        setErrors({ form: '잠시 뒤 다시 시도해 주세요' })
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="write-layout" onSubmit={submit}>
      <div className="write-main">
        <input className="title-input" value={draft.title} onChange={(e) => update('title', e.target.value)} maxLength={100} placeholder="제목을 입력하세요" aria-label="제목" />
        {errors.title && <span className="hint error">{errors.title}</span>}
        <div className="editor-toolbar">
          <div className="tabs">
            <button type="button" className={!preview ? 'current' : ''} onClick={() => setPreview(false)}>
              쓰기
            </button>
            <button type="button" className={preview ? 'current' : ''} onClick={() => setPreview(true)}>
              미리보기
            </button>
          </div>
          <ImageUploadButton onUploaded={(md) => update('body', draft.body + (draft.body && !draft.body.endsWith('\n') ? '\n' : '') + md + '\n')} />
          <span className="count">{[...draft.body].length.toLocaleString()} / 10,000</span>
        </div>
        <div className="editor">
          {preview ? (
            <MarkdownView source={draft.body} />
          ) : (
            <textarea value={draft.body} onChange={(e) => update('body', e.target.value)} placeholder="마크다운으로 쓸 수 있습니다" aria-label="본문" />
          )}
        </div>
        {errors.body && <span className="hint error">{errors.body}</span>}
      </div>
      <aside className="write-side">
        <div className="panel">
          <h4>{editing ? '글 수정' : '새 글'}</h4>
          {errors.form && <p className="error">{errors.form}</p>}
          <button type="submit" className="primary" disabled={saving}>
            저장
          </button>
        </div>
        <div className="panel">
          <h4>분류</h4>
          <select value={draft.categoryId ?? ''} onChange={(e) => update('categoryId', Number(e.target.value))} aria-label="분류">
            {blog.categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          {errors.categoryId && <span className="hint error">{errors.categoryId}</span>}
        </div>
        <div className="panel">
          <h4>공개 여부</h4>
          <div className="radio-row">
            <label>
              <input type="radio" checked={draft.visibility === 'PUBLIC'} onChange={() => update('visibility', 'PUBLIC')} />
              공개
            </label>
            <label>
              <input type="radio" checked={draft.visibility === 'PRIVATE'} onChange={() => update('visibility', 'PRIVATE')} />
              비공개
            </label>
          </div>
        </div>
        <div className="panel">
          <h4>태그</h4>
          <TagInput tags={draft.tags} onChange={(tags) => update('tags', tags)} />
          {errors.tags && <span className="hint error">{errors.tags}</span>}
        </div>
      </aside>
    </form>
  )
}
