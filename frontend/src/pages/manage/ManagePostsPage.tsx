import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { del, get } from '../../api/client'
import type { ManagedPostPage } from '../../api/types'
import Pagination from '../../components/Pagination'
import { formatDate } from '../../format'
import { M } from '../../messages'
import { useManage } from './ManageLayout'

export const STATUS_TABS = [
  { value: '', label: '전체' },
  { value: 'PUBLIC', label: '공개' },
  { value: 'PRIVATE', label: '비공개' },
  { value: 'DRAFT', label: '임시저장' },
]

/** 글 관리: 비공개·임시저장 포함 내 글 전체, 상태(전체·공개·비공개·임시저장)·분류로 거르기 (BM-03, FR-32) */
export default function ManagePostsPage() {
  const { blog, reloadBlog } = useManage()
  const [params, setParams] = useSearchParams()
  const statusParam = (params.get('status') ?? params.get('visibility') ?? '').toUpperCase()
  const status = STATUS_TABS.some((t) => t.value === statusParam) ? statusParam : ''
  const categoryId = params.get('categoryId') ?? ''
  const page = Number(params.get('page') ?? '1')
  const [data, setData] = useState<ManagedPostPage | null>(null)

  const load = () => {
    const q = new URLSearchParams({ page: String(page) })
    if (status) q.set('status', status)
    if (categoryId) q.set('categoryId', categoryId)
    get<ManagedPostPage>(`/api/manage/blogs/${blog.id}/posts?${q}`).then(setData)
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(load, [blog.id, status, categoryId, page])

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    next.delete('page')
    next.delete('visibility')
    setParams(next)
  }

  const remove = async (id: number) => {
    if (!confirm(M.deleteConfirm)) return
    await del(`/api/posts/${id}`)
    load()
    reloadBlog()
  }

  const filtered = status || categoryId
  return (
    <>
      <div className="list-head">
        <h1>글 관리</h1>
        <Link to="/write" className="button primary">
          글쓰기
        </Link>
      </div>
      <div className="tabs" role="tablist" aria-label="상태">
        {STATUS_TABS.map((t) => (
          <button key={t.value} role="tab" aria-selected={status === t.value} className={status === t.value ? 'current' : ''} onClick={() => setFilter('status', t.value)}>
            {t.label}
          </button>
        ))}
      </div>
      <div className="filters">
        <select value={categoryId} onChange={(e) => setFilter('categoryId', e.target.value)} aria-label="분류">
          <option value="">모든 분류</option>
          {blog.categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
              {c.visibility === 'PRIVATE' ? ' (비공개)' : ''}
            </option>
          ))}
        </select>
      </div>
      {data &&
        (data.items.length === 0 ? (
          <div className="empty">
            <p>{filtered ? '글이 없습니다' : '아직 쓴 글이 없습니다'}</p>
            {!filtered && <Link to="/write">글쓰기</Link>}
          </div>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>제목</th>
                  <th>분류</th>
                  <th>작성일</th>
                  <th>상태</th>
                  <th>조회</th>
                  <th>댓글</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.items.map((p) => (
                  <tr key={p.id}>
                    <td>
                      {/* 임시저장 글은 이어 쓰도록 수정 화면으로 간다 */}
                      <Link to={p.status === 'DRAFT' ? `/posts/${p.id}/edit` : `/posts/${p.id}`}>{p.title}</Link>
                    </td>
                    <td>
                      {p.categoryName}
                      {p.categoryVisibility === 'PRIVATE' && <span aria-label="비공개 분류"> 🔒</span>}
                    </td>
                    <td>{formatDate(p.createdAt)}</td>
                    <td>
                      {p.status === 'DRAFT' ? (
                        <span className="badge">임시저장</span>
                      ) : p.visibility === 'PUBLIC' ? (
                        '공개'
                      ) : (
                        <span className="badge">비공개</span>
                      )}
                    </td>
                    <td>{p.viewCount.toLocaleString()}</td>
                    <td>{p.commentCount}</td>
                    <td className="actions-cell">
                      <Link to={`/posts/${p.id}/edit`}>수정</Link>
                      <button className="link danger" onClick={() => remove(p.id)}>
                        삭제
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
      {data && (
        <Pagination
          page={data.page}
          totalPages={data.totalPages}
          onChange={(p) => {
            const next = new URLSearchParams(params)
            next.set('page', String(p))
            setParams(next)
          }}
        />
      )}
    </>
  )
}
