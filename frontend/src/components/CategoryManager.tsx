import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type PointerEvent } from 'react'
import { ApiError, del, patch, post, put } from '../api/client'
import type { CategoryView } from '../api/types'
import { categoryColor } from '../colors'
import { M } from '../messages'

const DEFAULT_NAME = '미분류'

const GRIP = (
  <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
    <circle cx="5.5" cy="3.5" r="1.4" />
    <circle cx="10.5" cy="3.5" r="1.4" />
    <circle cx="5.5" cy="8" r="1.4" />
    <circle cx="10.5" cy="8" r="1.4" />
    <circle cx="5.5" cy="12.5" r="1.4" />
    <circle cx="10.5" cy="12.5" r="1.4" />
  </svg>
)

/** ids에서 id를 빼서 to 자리(빼고 난 목록 기준)에 넣는다 */
export function moveId(ids: number[], id: number, to: number): number[] {
  const rest = ids.filter((x) => x !== id)
  const at = Math.max(0, Math.min(to, rest.length))
  return [...rest.slice(0, at), id, ...rest.slice(at)]
}

/**
 * 블로그 주인의 분류 관리: 추가(이름·소개글), 수정(이름·소개글 함께), 공개 범위, 순서, 삭제 (CF-08, FR-17, BR-34, BR-46).
 * "미분류"는 맨 뒤에 있고 움직이지 않으며, 다른 분류는 "미분류"라는 이름을 쓸 수 없다.
 */
export default function CategoryManager({
  blogId,
  categories,
  onChanged,
  initialEditId,
}: {
  blogId: number
  categories: CategoryView[]
  onChanged: () => void
  /** 내 블로그 화면의 [수정]으로 들어오면 그 분류를 바로 고친다 (FR-32) */
  initialEditId?: number | null
}) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editName, setEditName] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const movable = categories.filter((c) => !c.isDefault)
  const listRef = useRef<HTMLUListElement>(null)
  const [focusHandle, setFocusHandle] = useState<number | null>(null)
  const [status, setStatus] = useState('')

  useEffect(() => {
    const target = initialEditId ? categories.find((c) => c.id === initialEditId) : undefined
    if (target) startEdit(target)
    // 처음 열 때 한 번만
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialEditId])

  // 키보드로 옮긴 뒤 목록을 새로 읽어도 같은 손잡이에 초점을 둔다
  useEffect(() => {
    if (focusHandle == null) return
    listRef.current?.querySelector<HTMLButtonElement>(`[data-handle="${focusHandle}"]`)?.focus()
  }, [categories, focusHandle])

  const run = async (action: () => Promise<unknown>) => {
    setError(null)
    try {
      await action()
      onChanged()
      return true
    } catch (e) {
      setError(e instanceof ApiError ? e.fieldErrors[0]?.message ?? e.message : '잠시 뒤 다시 시도해 주세요')
      return false
    }
  }

  const checkName = (value: string, isDefault: boolean) => {
    if (!value.trim()) return M.categoryNameRequired
    if (!isDefault && value.trim().toLowerCase() === DEFAULT_NAME) return M.categoryReservedName
    return null
  }

  const add = async (e: FormEvent) => {
    e.preventDefault()
    const problem = checkName(name, false)
    if (problem) {
      setError(problem)
      return
    }
    if (await run(() => post(`/api/blogs/${blogId}/categories`, { name, description }))) {
      setName('')
      setDescription('')
    }
  }

  const startEdit = (category: CategoryView) => {
    setEditingId(category.id)
    setEditName(category.name)
    setEditDescription(category.description ?? '')
    setError(null)
  }

  const saveEdit = async (category: CategoryView) => {
    const problem = checkName(editName, category.isDefault)
    if (problem) {
      setError(problem)
      return
    }
    if (await run(() => patch(`/api/categories/${category.id}`, { name: editName, description: editDescription }))) {
      setEditingId(null)
    }
  }

  /** 직접 만든 분류의 새 순서를 한 번에 저장한다. "미분류"는 늘 맨 뒤라 보내지 않는다 (FR-17, CR-31) */
  const saveOrder = async (ids: number[], moved: CategoryView, focus: boolean) => {
    const before = movable.map((c) => c.id)
    if (ids.every((id, i) => id === before[i])) return
    setFocusHandle(focus ? moved.id : null)
    if (await run(() => put(`/api/blogs/${blogId}/categories/order`, { categoryIds: ids }))) {
      setStatus(`"${moved.name}" 분류를 ${ids.indexOf(moved.id) + 1}번째로 옮겼습니다`)
    }
  }

  // 손잡이에서 ↑ ↓ 로 한 칸씩 (CR-31)
  const onHandleKey = (e: KeyboardEvent<HTMLButtonElement>, category: CategoryView) => {
    if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return
    e.preventDefault()
    const ids = movable.map((c) => c.id)
    const at = ids.indexOf(category.id)
    const to = e.key === 'ArrowUp' ? at - 1 : at + 1
    if (to < 0 || to >= ids.length) return
    saveOrder(moveId(ids, category.id, to), category, true)
  }

  // 손잡이를 끌어서 옮긴다. 포인터 이벤트라 마우스·터치·펜이 모두 되고, 끄는 중 Esc로 취소한다 (CR-31)
  const onHandlePointerDown = (e: PointerEvent<HTMLButtonElement>, category: CategoryView) => {
    if (e.button !== 0) return
    const list = listRef.current
    const handle = e.currentTarget
    if (!list) return
    const rows = [...list.querySelectorAll<HTMLLIElement>('li[data-cat]')]
    const dragged = rows.find((r) => Number(r.dataset.cat) === category.id)
    const others = rows.filter((r) => r !== dragged)
    if (!dragged || others.length === 0) return
    e.preventDefault()
    const pointerId = e.pointerId
    const startY = e.clientY
    const startIndex = rows.indexOf(dragged)
    let target = startIndex
    handle.setPointerCapture?.(pointerId)
    dragged.classList.add('dragging')
    document.body.classList.add('is-dragging')

    // 포인터보다 위에 있는 다른 줄의 수 = 놓았을 때의 자리. "미분류" 줄은 셈에 넣지 않아 늘 맨 뒤에 남는다
    const indexAt = (y: number) =>
      others.filter((r) => {
        const box = r.getBoundingClientRect()
        return box.top + box.height / 2 < y
      }).length
    const clearMarks = () => others.forEach((r) => r.classList.remove('drop-before', 'drop-after'))
    const mark = (index: number) => {
      clearMarks()
      if (index === startIndex) return
      if (index < others.length) others[index].classList.add('drop-before')
      else others[others.length - 1].classList.add('drop-after')
    }
    const move = (ev: globalThis.PointerEvent) => {
      dragged.style.transform = `translateY(${ev.clientY - startY}px)`
      target = indexAt(ev.clientY)
      mark(target)
    }
    const finish = (commit: boolean) => {
      handle.removeEventListener('pointermove', move)
      handle.removeEventListener('pointerup', up)
      handle.removeEventListener('pointercancel', cancel)
      document.removeEventListener('keydown', onKey)
      try {
        handle.releasePointerCapture?.(pointerId)
      } catch {
        // 이미 놓인 경우
      }
      document.body.classList.remove('is-dragging')
      dragged.classList.remove('dragging')
      dragged.style.transform = ''
      clearMarks()
      if (commit && target !== startIndex) saveOrder(moveId(movable.map((c) => c.id), category.id, target), category, false)
    }
    const up = (ev: globalThis.PointerEvent) => {
      target = indexAt(ev.clientY)
      finish(true)
    }
    const cancel = () => finish(false)
    const onKey = (ev: globalThis.KeyboardEvent) => {
      if (ev.key === 'Escape') {
        finish(false)
        setStatus('옮기기를 취소했습니다')
      }
    }
    handle.addEventListener('pointermove', move)
    handle.addEventListener('pointerup', up)
    handle.addEventListener('pointercancel', cancel)
    document.addEventListener('keydown', onKey)
  }

  // 공개 → 비공개는 바로, 비공개 → 공개는 공개 글이 있으면 한 번 더 묻는다 (BR-46)
  const toggleVisibility = (category: CategoryView) => {
    const next = category.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC'
    if (next === 'PUBLIC' && category.publicPostCount > 0 && !confirm(M.categoryMakePublicConfirm)) return
    run(() => put(`/api/categories/${category.id}/visibility`, { visibility: next }))
  }

  return (
    <div className="category-manager">
      <ul ref={listRef}>
        {categories.map((c) => {
          const index = movable.findIndex((m) => m.id === c.id)
          return (
            <li key={c.id} className={c.visibility === 'PRIVATE' ? 'private' : ''} data-cat={c.isDefault ? undefined : c.id}>
              {c.isDefault ? (
                <span className="drag-spacer" aria-hidden="true" />
              ) : (
                <button
                  type="button"
                  className="drag-handle"
                  data-handle={c.id}
                  aria-label={`${c.name} 순서 바꾸기. 끌어서 옮기거나 위·아래 화살표 키를 누르세요`}
                  disabled={movable.length < 2}
                  onKeyDown={(e) => onHandleKey(e, c)}
                  onPointerDown={(e) => onHandlePointerDown(e, c)}
                  onBlur={() => setFocusHandle(null)}
                >
                  {GRIP}
                </button>
              )}
              <span className="dot" style={{ ['--c' as string]: categoryColor(c.colorIndex) }} />
              {editingId === c.id ? (
                <span className="grow edit-fields">
                  <input value={editName} onChange={(e) => setEditName(e.target.value)} maxLength={20} aria-label="분류 이름" />
                  <input
                    value={editDescription}
                    onChange={(e) => setEditDescription(e.target.value)}
                    maxLength={100}
                    placeholder="소개글 (선택, 100자까지)"
                    aria-label="분류 소개글"
                  />
                </span>
              ) : (
                <span className="grow">
                  {c.name}
                  {c.visibility === 'PRIVATE' && (
                    <span className="badge" style={{ marginLeft: 6 }}>
                      🔒 비공개
                    </span>
                  )}{' '}
                  <span className="count">글 {c.postCount}개</span>
                  {c.description && <span className="cat-desc">{c.description}</span>}
                </span>
              )}
              {editingId === c.id ? (
                <>
                  <button className="link" onClick={() => saveEdit(c)}>
                    저장
                  </button>
                  <button className="link" onClick={() => setEditingId(null)}>
                    취소
                  </button>
                </>
              ) : (
                <button className="link" onClick={() => startEdit(c)}>
                  수정
                </button>
              )}
              <button className="link" onClick={() => toggleVisibility(c)}>
                {c.visibility === 'PUBLIC' ? '비공개로' : '공개로'}
              </button>
              {!c.isDefault && (
                <>
                  <button
                    className="link"
                    disabled={index <= 0}
                    onClick={() => run(() => post(`/api/categories/${c.id}/move`, { direction: 'UP' }))}
                    aria-label={`${c.name} 위로 옮기기`}
                  >
                    ▲
                  </button>
                  <button
                    className="link"
                    disabled={index === movable.length - 1}
                    onClick={() => run(() => post(`/api/categories/${c.id}/move`, { direction: 'DOWN' }))}
                    aria-label={`${c.name} 아래로 옮기기`}
                  >
                    ▼
                  </button>
                  <button className="link danger" onClick={() => confirm(`"${c.name}" 분류를 삭제할까요?`) && run(() => del(`/api/categories/${c.id}`))}>
                    삭제
                  </button>
                </>
              )}
            </li>
          )
        })}
      </ul>
      <form className="row" onSubmit={add}>
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={20} placeholder="새 분류" aria-label="새 분류 이름" />
        <input
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={100}
          placeholder="소개글 (선택)"
          aria-label="새 분류 소개글"
        />
        <button>추가</button>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
      <p className="sr-only" role="status" aria-live="polite">
        {status}
      </p>
      <p className="note">
        왼쪽 손잡이를 끌거나 손잡이에서 ↑ ↓ 키로 순서를 바꿀 수 있습니다. 글이 하나라도 있는 분류는 삭제할 수 없습니다. 글은 글 수정에서 다른 분류로 옮길 수 있습니다. "미분류"는 항상 맨 뒤에 있습니다. 비공개 분류의 글은 나만 볼 수 있습니다.
      </p>
    </div>
  )
}
