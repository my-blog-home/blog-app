import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { del, get, post } from '../../api/client'
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
  secret: boolean
  reply: boolean
}

interface Page {
  totalCount: number
  page: number
  totalPages: number
  items: ManagedComment[]
}

/** 댓글 관리: 최신순(답글 포함), NEW·답글·비밀 표시, 열면 읽음 처리 (BM-05, FR-065, FR-066) */
export default function ManageCommentsPage() {
  const { blog } = useManage()
  const { refreshNewComments } = useAuth()
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? '1')
  const [data, setData] = useState<Page | null>(null)
  // 이 화면을 연 순간 새 댓글이던 것은 계속 NEW로 보여 준다
  const newIds = useRef(new Set<number>())
  const markedRead = useRef(false)

  const load = async () => {
    const result = await get<Page>(`/api/manage/blogs/${blog.id}/comments?page=${page}`)
    result.items.forEach((c) => c.isNew && newIds.current.add(c.id))
    setData(result)
    if (!markedRead.current) {
      markedRead.current = true
      await post(`/api/manage/blogs/${blog.id}/comments/read`)
      refreshNewComments()
    }
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
        <ul className="comment-rows">
          {data.items.map((c) => (
            <li key={c.id} className={newIds.current.has(c.id) ? 'is-new' : undefined}>
              <div className="meta">
                {newIds.current.has(c.id) && <span className="badge new">NEW</span>}
                <strong>{c.authorNickname ?? M.withdrawnUser}</strong>
                {c.reply && <span className="badge">답글</span>}
                {c.secret && (
                  <span className="badge secret" aria-label="비밀 댓글">
                    🔒 비밀
                  </span>
                )}
                <span>{formatDateTime(c.createdAt)}</span>
                <button className="link danger" onClick={() => remove(c.id)}>
                  삭제
                </button>
              </div>
              <p className="comment-body">{c.excerpt}</p>
              <Link to={`/posts/${c.postId}#comment-${c.id}`} className="post-link">
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
