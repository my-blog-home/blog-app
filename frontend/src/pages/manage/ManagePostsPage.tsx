import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { del, get } from '../../api/client'
import Pagination from '../../components/Pagination'
import { formatDate } from '../../format'
import { M } from '../../messages'
import { useManage } from './ManageLayout'

interface ManagedPost {
  id: number
  title: string
  categoryName: string
  createdAt: string
  visibility: 'PUBLIC' | 'PRIVATE'
  viewCount: number
  commentCount: number
}

interface Page {
  totalCount: number
  page: number
  totalPages: number
  items: ManagedPost[]
}

/** 글 관리: 비공개 포함 내 글 전체, 공개 여부·분류로 거르기 (BM-03) */
export default function ManagePostsPage() {
  const { blog, reloadBlog } = useManage()
  const [params, setParams] = useSearchParams()
  const visibility = params.get('visibility') ?? ''
  const categoryId = params.get('categoryId') ?? ''
  const page = Number(params.get('page') ?? '1')
  const [data, setData] = useState<Page | null>(null)

  const load = () => {
    const q = new URLSearchParams({ page: String(page) })
    if (visibility) q.set('visibility', visibility)
    if (categoryId) q.set('categoryId', categoryId)
    get<Page>(`/api/manage/blogs/${blog.id}/posts?${q}`).then(setData)
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(load, [blog.id, visibility, categoryId, page])

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    next.delete('page')
    setParams(next)
  }

  const remove = async (id: number) => {
    if (!confirm(M.deleteConfirm)) return
    await del(`/api/posts/${id}`)
    load()
    reloadBlog()
  }

  const filtered = visibility || categoryId
  return (
    <>
      <div className="list-head">
        <h1>글 관리</h1>
        <Link to="/write" className="button primary">
          글쓰기
        </Link>
      </div>
      <div className="filters">
        <select value={visibility} onChange={(e) => setFilter('visibility', e.target.value)} aria-label="공개 여부">
          <option value="">전체</option>
          <option value="PUBLIC">공개</option>
          <option value="PRIVATE">비공개</option>
        </select>
        <select value={categoryId} onChange={(e) => setFilter('categoryId', e.target.value)} aria-label="분류">
          <option value="">모든 분류</option>
          {blog.categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
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
                  <th>공개</th>
                  <th>조회</th>
                  <th>댓글</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.items.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <Link to={`/posts/${p.id}`}>{p.title}</Link>
                    </td>
                    <td>{p.categoryName}</td>
                    <td>{formatDate(p.createdAt)}</td>
                    <td>{p.visibility === 'PUBLIC' ? '공개' : <span className="badge">비공개</span>}</td>
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
