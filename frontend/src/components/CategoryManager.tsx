import { useState, type FormEvent } from 'react'
import { ApiError, del, patch, post, put } from '../api/client'
import type { CategoryView } from '../api/types'
import { categoryColor } from '../colors'
import { M } from '../messages'

const DEFAULT_NAME = '미분류'

/**
 * 블로그 주인의 분류 관리: 추가(이름·소개글), 수정(이름·소개글 함께), 공개 범위, 순서, 삭제 (CF-08, FR-17, BR-34, BR-46).
 * "미분류"는 맨 뒤에 있고 움직이지 않으며, 다른 분류는 "미분류"라는 이름을 쓸 수 없다.
 */
export default function CategoryManager({ blogId, categories, onChanged }: { blogId: number; categories: CategoryView[]; onChanged: () => void }) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editName, setEditName] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const movable = categories.filter((c) => !c.isDefault)

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

  // 공개 → 비공개는 바로, 비공개 → 공개는 공개 글이 있으면 한 번 더 묻는다 (BR-46)
  const toggleVisibility = (category: CategoryView) => {
    const next = category.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC'
    if (next === 'PUBLIC' && category.publicPostCount > 0 && !confirm(M.categoryMakePublicConfirm)) return
    run(() => put(`/api/categories/${category.id}/visibility`, { visibility: next }))
  }

  return (
    <div className="category-manager">
      <ul>
        {categories.map((c) => {
          const index = movable.findIndex((m) => m.id === c.id)
          return (
            <li key={c.id} className={c.visibility === 'PRIVATE' ? 'private' : ''}>
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
                    aria-label="위로"
                  >
                    ▲
                  </button>
                  <button
                    className="link"
                    disabled={index === movable.length - 1}
                    onClick={() => run(() => post(`/api/categories/${c.id}/move`, { direction: 'DOWN' }))}
                    aria-label="아래로"
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
      {error && <p className="error">{error}</p>}
      <p className="note">
        글이 하나라도 있는 분류는 삭제할 수 없습니다. 글은 글 수정에서 다른 분류로 옮길 수 있습니다. "미분류"는 항상 맨 뒤에 있습니다. 비공개 분류의 글은 나만 볼 수 있습니다.
      </p>
    </div>
  )
}
