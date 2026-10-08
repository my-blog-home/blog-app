import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { del, get } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import Pagination from '../../components/Pagination'
import { formatDateTime } from '../../format'
import { M } from '../../messages'
import { useManage } from './ManageLayout'

interface ManagedComment {
  id: number
  authorNickname: string | null
  createdAt: string
  excerpt: string
  postId: number
  postTitle: string
  isNew: boolean
}

interface Page {
  totalCount: number
  page: number
  totalPages: number
  items: ManagedComment[]
}

/** 댓글 관리: 최신순, NEW 표시, 열면 읽음 처리 (BM-05) */
export default function ManageCommentsPage() {
  const { blog } = useManage()
  const { refreshNewComments } = useAuth()
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? '1')
  const [data, setData] = useState<Page | null>(null)

  const load = async () => {
    setData(await get<Page>(`/api/manage/blogs/${blog.id}/comments?page=${page}`))
    refreshNewComments()
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { load() }, [blog.id, page])

  const remove = async (id: number) => {
    if (!confirm(M.commentDeleteConfirm)) return
    await del(`/api/comments/${id}`)
    setData(await get<Page>(`/api/manage/blogs/${blog.id}/comments?page=${page}`))
  }

  if (!data) return null
  return (
    <>
      <h1>댓글 관리</h1>
      {data.items.length === 0 ? (
        <p className="empty">아직 달린 댓글이 없습니다</p>
      ) : (
        <ul className="post-list">
          {data.items.map((c) => (
            <li key={c.id}>
              <div className="meta">
                {c.isNew && <span className="badge new">NEW</span>}
                <strong>{c.authorNickname ?? M.withdrawnUser}</strong>
                <span>{formatDateTime(c.createdAt)}</span>
                <button className="link danger" onClick={() => remove(c.id)}>
                  삭제
                </button>
              </div>
              <p className="comment-body">{c.excerpt}</p>
              <Link to={`/posts/${c.postId}#comment-${c.id}`} className="small">
                {c.postTitle}
              </Link>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => setParams({ page: String(p) })} />
    </>
  )
}
