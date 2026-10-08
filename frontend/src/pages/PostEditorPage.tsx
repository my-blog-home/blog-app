import { useEffect, useRef, useState, type ClipboardEvent, type DragEvent, type FormEvent } from 'react'
import { useBlocker, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError, get, post as httpPost, put } from '../api/client'
import { loadTopics } from '../api/topics'
import type { BlogView, PostSource, PostStatus, Topic, Visibility } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import ImageUploadButton, { isAllowedImage, upload } from '../components/ImageUploadButton'
import { applyFormat, countImages, insertBlock, TOOLBAR, type Format } from '../editor/markdownTools'
import MarkdownView from '../components/MarkdownView'
import TagInput from '../components/TagInput'
import { M } from '../messages'
import NotFoundPage from './NotFoundPage'

interface Draft {
  title: string
  body: string
  categoryId: number | null
  topicId: number | null
  visibility: Visibility
  tags: string[]
}

const IMAGES_PER_POST = 10

/**
 * 글쓰기·글 수정 (CF-05, CF-13). 권한은 서버가 최종 확인한다.
 * [임시저장]은 제목·본문을 비워도 저장하고 화면에 남는다. [작성완료]는 올린다 (FR-09, FR-11, CR-06)
 * 본문 위의 마크다운 도구 버튼으로 서식을 넣고, 이미지 파일을 붙여 넣거나 끌어다 놓으면 올려서 커서 자리에 넣는다 (FR-084)
 */
export default function PostEditorPage() {
  const { postId } = useParams()
  const editing = postId !== undefined
  const { me, loading, requireLogin } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [notice, setNotice] = useState<string | null>((location.state as { notice?: string } | null)?.notice ?? null)
  const [blog, setBlog] = useState<BlogView | null>(null)
  const [topics, setTopics] = useState<Topic[]>([])
  const [status, setStatus] = useState<PostStatus>('DRAFT')
  const [draft, setDraft] = useState<Draft>({ title: '', body: '', categoryId: null, topicId: null, visibility: 'PUBLIC', tags: [] })
  const [saved, setSaved] = useState<Draft | null>(null)
  const [originalVisibility, setOriginalVisibility] = useState<Visibility>('PUBLIC')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [missing, setMissing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [preview, setPreview] = useState(false)
  const done = useRef(false)
  const textarea = useRef<HTMLTextAreaElement>(null)
  const [imageBusy, setImageBusy] = useState(false)
  const [imageError, setImageError] = useState<string | null>(null)

  useEffect(() => {
    if (loading) return
    if (!me) {
      requireLogin(() => undefined)
      return
    }
    if (!editing && !me.blogId) return
    const load = async () => {
      try {
        let blogId = me.blogId
        let initial: Draft | null = null
        setTopics(await loadTopics())
        if (editing) {
          const source = await get<PostSource>(`/api/posts/${postId}/edit`)
          blogId = source.blogId
          initial = {
            title: source.title,
            body: source.body,
            categoryId: source.categoryId,
            topicId: source.topicId,
            visibility: source.visibility,
            tags: source.tags,
          }
          setOriginalVisibility(source.visibility)
          setStatus(source.status)
        }
        const view = await get<BlogView>(`/api/blogs/${blogId}`)
        setBlog(view)
        if (!initial) {
          // 분류 기본값은 마지막에 쓴 글의 분류, 처음이면 "미분류" (CF-05-5)
          // 주제 기본값은 마지막에 쓴 글의 주제, 처음이면 블로그의 대표 주제 (FR-09)
          const fallback = view.categories.find((c) => c.isDefault)?.id ?? null
          initial = {
            title: '',
            body: '',
            categoryId: view.lastUsedCategoryId ?? fallback,
            topicId: view.lastUsedTopicId ?? view.topic.id,
            visibility: 'PUBLIC',
            tags: [],
          }
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
  // 관리자 계정은 블로그가 없어 글을 쓸 수 없다 (FR-078)
  if (!editing && !me.blogId) return <p className="empty">블로그가 없는 계정은 글을 쓸 수 없습니다</p>
  if (!blog) return null

  const update = <K extends keyof Draft>(key: K, value: Draft[K]) => setDraft({ ...draft, [key]: value })

  const published = editing && status === 'PUBLISHED'

  const select = (start: number, end: number) =>
    requestAnimationFrame(() => {
      textarea.current?.focus()
      textarea.current?.setSelectionRange(start, end)
    })

  // 도구 버튼: 고른 글자를 감싸거나 줄 앞에 기호를 붙인다
  const format = (f: Format) => {
    const ta = textarea.current
    const edit = applyFormat(draft.body, ta?.selectionStart ?? draft.body.length, ta?.selectionEnd ?? draft.body.length, f)
    update('body', edit.text)
    select(edit.selectionStart, edit.selectionEnd)
  }

  // 커서 자리(미리보기 중이면 맨 끝)에 마크다운을 넣는다
  const insertAtCursor = (markdown: string, at?: number) => {
    const position = at ?? (preview ? undefined : textarea.current?.selectionStart)
    setDraft((d) => {
      const edit = insertBlock(d.body, Math.min(position ?? d.body.length, d.body.length), markdown)
      select(edit.selectionStart, edit.selectionEnd)
      return { ...d, body: edit.text }
    })
  }

  // 붙여 넣거나 끌어다 놓은 이미지 파일을 같은 규칙(jpg·png·gif·webp, 5MB, 글마다 10장)으로 올린다
  const uploadImages = async (files: File[], at: number) => {
    if (imageBusy) return
    if (files.some((f) => !isAllowedImage(f))) {
      setImageError(M.imageRule)
      return
    }
    if (countImages(draft.body) + files.length > IMAGES_PER_POST) {
      setImageError(M.imageTooMany)
      return
    }
    setImageBusy(true)
    setImageError(null)
    try {
      const urls: string[] = []
      for (const file of files) urls.push(await upload(file))
      insertAtCursor(urls.map((url) => `![](${url})`).join('\n'), at)
    } catch (e) {
      setImageError(e instanceof ApiError ? e.fieldErrors[0]?.message ?? e.message : M.imageRule)
    } finally {
      setImageBusy(false)
    }
  }

  const imageFiles = (list: FileList | null | undefined) => Array.from(list ?? []).filter((f) => f.type.startsWith('image/'))

  // HTML(다른 문서에서 복사한 글)이 함께 있으면 이미지는 빼고 글자만 붙여 넣는다
  const onPaste = (e: ClipboardEvent<HTMLTextAreaElement>) => {
    const files = imageFiles(e.clipboardData.files)
    if (files.length === 0 || e.clipboardData.getData('text/html')) return
    e.preventDefault()
    uploadImages(files, e.currentTarget.selectionStart)
  }

  const onDrop = (e: DragEvent<HTMLTextAreaElement>) => {
    const files = imageFiles(e.dataTransfer.files)
    if (files.length === 0) return
    e.preventDefault()
    uploadImages(files, e.currentTarget.selectionStart)
  }

  // asDraft: 임시저장. 이미 작성완료한 글이면 서버가 작성완료 상태를 그대로 두고 내용만 저장한다 (BR-04)
  const save = async (asDraft: boolean) => {
    if (saving) return
    const publishing = !asDraft || published
    const next: Record<string, string> = {}
    if (publishing && !draft.title.trim()) next.title = M.titleRequired
    if (publishing && !draft.body.trim()) next.body = M.bodyRequired
    setErrors(next)
    setNotice(null)
    if (Object.keys(next).length > 0) return
    if (!asDraft && draft.visibility === 'PUBLIC' && originalVisibility === 'PRIVATE' && !confirm(M.makePublicConfirm)) return

    setSaving(true)
    try {
      const body = { ...draft, draft: asDraft }
      const result = editing
        ? await put<{ id: number }>(`/api/posts/${postId}`, body)
        : await httpPost<{ id: number }>(`/api/blogs/${blog.id}/posts`, body)
      if (asDraft) {
        // 임시저장하면 안내를 띄우고 글쓰기 화면에 남는다. 주소는 이 글의 수정 주소로 바뀐다
        const kept = { ...draft, title: draft.title.trim() ? draft.title : published ? draft.title : M.draftTitle }
        setDraft(kept)
        setSaved(kept)
        setNotice(M.draftSaved)
        if (!editing) {
          done.current = true
          navigate(`/posts/${result.id}/edit`, { replace: true, state: { notice: M.draftSaved } })
        }
        return
      }
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

  const submit = (e: FormEvent) => {
    e.preventDefault()
    save(false)
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
          <ImageUploadButton onUploaded={(md) => insertAtCursor(md)} />
          <span className="count">{[...draft.body].length.toLocaleString()} / 10,000</span>
        </div>
        {!preview && (
          <div className="md-toolbar" role="toolbar" aria-label="서식">
            {TOOLBAR.map((t) => (
              <button
                key={t.format}
                type="button"
                className={`md-btn md-${t.format}`}
                title={t.title}
                aria-label={t.title}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => format(t.format)}
              >
                {t.label}
              </button>
            ))}
            {imageBusy && <span className="hint">이미지를 올리는 중…</span>}
          </div>
        )}
        {imageError && <span className="hint error">{imageError}</span>}
        <div className="editor">
          {preview ? (
            <MarkdownView source={draft.body} />
          ) : (
            <textarea
              ref={textarea}
              value={draft.body}
              onChange={(e) => update('body', e.target.value)}
              onPaste={onPaste}
              onDrop={onDrop}
              onDragOver={(e) => {
                if (Array.from(e.dataTransfer.types).includes('Files')) e.preventDefault()
              }}
              placeholder="마크다운으로 쓸 수 있습니다. 이미지는 붙여 넣거나 끌어다 놓아도 됩니다"
              aria-label="본문"
            />
          )}
        </div>
        {errors.body && <span className="hint error">{errors.body}</span>}
      </div>
      <aside className="write-side">
        <div className="panel">
          <h4>
            {editing ? '글 수정' : '새 글'}
            {editing && status === 'DRAFT' && <span className="badge" style={{ marginLeft: 8 }}>임시저장</span>}
          </h4>
          {notice && <p className="notice" role="status">{notice}</p>}
          {errors.form && <p className="error">{errors.form}</p>}
          <div className="row">
            <button type="button" disabled={saving} onClick={() => save(true)}>
              임시저장
            </button>
            <button type="submit" className="primary" disabled={saving}>
              {published ? '수정 완료' : '작성완료'}
            </button>
          </div>
        </div>
        <div className="panel">
          <h4>주제</h4>
          <select value={draft.topicId ?? ''} onChange={(e) => update('topicId', Number(e.target.value))} aria-label="주제">
            {topics.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
          {errors.topicId && <span className="hint error">{errors.topicId}</span>}
        </div>
        <div className="panel">
          <h4>분류</h4>
          <select value={draft.categoryId ?? ''} onChange={(e) => update('categoryId', Number(e.target.value))} aria-label="분류">
            {blog.categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
                {c.visibility === 'PRIVATE' ? ' (비공개)' : ''}
              </option>
            ))}
          </select>
          {blog.categories.find((c) => c.id === draft.categoryId)?.visibility === 'PRIVATE' && (
            <span className="hint muted">비공개 분류의 글은 나만 볼 수 있습니다</span>
          )}
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
