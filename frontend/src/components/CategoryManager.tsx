import { useState, type FormEvent } from 'react'
import { ApiError, del, patch, post } from '../api/client'
import type { CategoryView } from '../api/types'
import { M } from '../messages'

/** 블로그 주인의 분류 관리: 추가, 이름 변경, 순서, 삭제 (CF-08) */
export default function CategoryManager({ blogId, categories, onChanged }: { blogId: number; categories: CategoryView[]; onChanged: () => void }) {
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  const run = async (action: () => Promise<unknown>) => {
    setError(null)
    try {
      await action()
      onChanged()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '잠시 뒤 다시 시도해 주세요')
    }
  }

  const add = (e: FormEvent) => {
    e.preventDefault()
    if (!name.trim()) {
      setError(M.categoryNameRequired)
      return
    }
    run(() => post(`/api/blogs/${blogId}/categories`, { name })).then(() => setName(''))
  }

  const rename = (category: CategoryView) => {
    const next = prompt('새 분류 이름', category.name)
    if (next === null || next.trim() === category.name) return
    run(() => patch(`/api/categories/${category.id}`, { name: next }))
  }

  return (
    <div className="category-manager">
      <h3>분류 관리</h3>
      <ul>
        {categories.map((c, i) => (
          <li key={c.id}>
            <span className={`dot color-${c.colorIndex}`} />
            <span className="grow">
              {c.name} ({c.postCount})
            </span>
            <button className="link" onClick={() => rename(c)}>
              이름
            </button>
            <button className="link" disabled={i === 0} onClick={() => run(() => post(`/api/categories/${c.id}/move`, { direction: 'UP' }))} aria-label="위로">
              ▲
            </button>
            <button
              className="link"
              disabled={i === categories.length - 1}
              onClick={() => run(() => post(`/api/categories/${c.id}/move`, { direction: 'DOWN' }))}
              aria-label="아래로"
            >
              ▼
            </button>
            {!c.isDefault && (
              <button className="link danger" onClick={() => confirm(`"${c.name}" 분류를 삭제할까요?`) && run(() => del(`/api/categories/${c.id}`))}>
                삭제
              </button>
            )}
          </li>
        ))}
      </ul>
      <form className="row" onSubmit={add}>
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={20} placeholder="새 분류" aria-label="새 분류 이름" />
        <button>추가</button>
      </form>
      {error && <p className="error">{error}</p>}
      <p className="muted small">글이 하나라도 있는 분류는 삭제할 수 없습니다. 글은 글 수정에서 다른 분류로 옮길 수 있습니다.</p>
    </div>
  )
}
