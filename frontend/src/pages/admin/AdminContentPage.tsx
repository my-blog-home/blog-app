import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import type { AdminCommentRow, AdminContentPage as ContentPage, AdminPostRow } from '../../api/adminTypes'
import { del, get } from '../../api/client'
import Pagination from '../../components/Pagination'
import { formatDateTime } from '../../format'
import { errorOf, short } from './adminShared'

type Kind = 'posts' | 'comments'

const author = (nickname: string | null) => nickname ?? '탈퇴한 회원'

/**
 * 글·댓글 관리. 신고가 없어도 최근 글과 댓글을 10개씩 보고, 제목·블로그·작성자·내용으로 찾고, 지운다.
 * 비밀 댓글은 내용을 보여 주지 않는다. 지우면 되돌릴 수 없어 한 번 더 묻는다.
 */
export default function AdminContentPage() {
  const [params, setParams] = useSearchParams()
  const kind: Kind = params.get('kind') === 'comments' ? 'comments' : 'posts'
  const q = params.get('q') ?? ''
  const page = Number(params.get('page') ?? '1') || 1
  const [query, setQuery] = useState(q)
  const [posts, setPosts] = useState<ContentPage<AdminPostRow> | null>(null)
  const [comments, setComments] = useState<ContentPage<AdminCommentRow> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const search = new URLSearchParams({ page: String(page) })
      if (q) search.set('q', q)
      if (kind === 'posts') setPosts(await get<ContentPage<AdminPostRow>>(`/api/admin/posts?${search}`))
      else setComments(await get<ContentPage<AdminCommentRow>>(`/api/admin/comments?${search}`))
      setError(null)
    } catch (e) {
      setError(errorOf(e))
    }
  }, [kind, q, page])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => setQuery(q), [q, kind])

  const go = (next: { kind?: Kind; q?: string; page?: number }) => {
    const search: Record<string, string> = {}
    const nextKind = next.kind ?? kind
    if (nextKind === 'comments') search.kind = 'comments'
    const nextQ = next.q ?? q
    if (nextQ) search.q = nextQ
    if (next.page && next.page > 1) search.page = String(next.page)
    setParams(search)
  }

  const submitSearch = (e: FormEvent) => {
    e.preventDefault()
    setNotice(null)
    go({ q: query.trim(), page: 1 })
  }

  const remove = async (url: string, label: string) => {
    if (!confirm(`${label}을(를) 지울까요? 지우면 되돌릴 수 없습니다.`)) return
    try {
      await del(url)
      setNotice(`${label}을(를) 지웠습니다`)
      await load()
    } catch (e) {
      setError(errorOf(e))
    }
  }

  const data = kind === 'posts' ? posts : comments

  return (
    <>
      <h1>글·댓글 관리</h1>
      <div className="tabs" role="tablist" aria-label="관리 대상">
        {(['posts', 'comments'] as Kind[]).map((k) => (
          <button
            key={k}
            type="button"
            role="tab"
            aria-selected={kind === k}
            className={kind === k ? 'current' : ''}
            onClick={() => {
              setNotice(null)
              go({ kind: k, q: '', page: 1 })
            }}
          >
            {k === 'posts' ? '글' : '댓글'}
          </button>
        ))}
      </div>
      <form className="filters" onSubmit={submitSearch} role="search">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={kind === 'posts' ? '제목, 블로그 이름 또는 작성자' : '댓글 내용 또는 작성자'}
          aria-label={kind === 'posts' ? '글 검색' : '댓글 검색'}
          className="grow"
        />
        <button type="submit">검색</button>
        {q && (
          <button
            type="button"
            className="link muted"
            onClick={() => {
              setQuery('')
              go({ q: '', page: 1 })
            }}
          >
            검색 지우기
          </button>
        )}
      </form>
      {notice && <p className="notice">{notice}</p>}
      {error && <p className="error">{error}</p>}
      {data && data.items.length === 0 && <p className="empty">{q ? '검색 결과가 없습니다' : kind === 'posts' ? '글이 없습니다' : '댓글이 없습니다'}</p>}
      {kind === 'posts' && posts && posts.items.length > 0 && (
        <div className="table-wrap">
          <table className="data-table admin-table">
            <thead>
              <tr>
                <th>제목</th>
                <th>블로그·작성자</th>
                <th>올린 때</th>
                <th>댓글</th>
                <th>신고</th>
                <th>
                  <span className="sr-only">작업</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {posts.items.map((p) => (
                <tr key={p.id}>
                  <td className="wrap">
                    <Link to={`/posts/${p.id}`}>{short(p.title, 40)}</Link>
                    {p.visibility === 'PRIVATE' && <span className="badge">비공개</span>}
                  </td>
                  <td className="wrap">
                    <Link to={`/blogs/${p.blogId}`}>{p.blogName}</Link>
                    <div className="faint small">{author(p.authorNickname)}</div>
                  </td>
                  <td>{formatDateTime(p.publishedAt)}</td>
                  <td>{p.commentCount.toLocaleString()}</td>
                  <td>{p.reportCount.toLocaleString()}</td>
                  <td>
                    <button type="button" className="sm danger" onClick={() => remove(`/api/admin/posts/${p.id}`, '글')}>
                      삭제
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {kind === 'comments' && comments && comments.items.length > 0 && (
        <div className="table-wrap">
          <table className="data-table admin-table">
            <thead>
              <tr>
                <th>댓글</th>
                <th>글</th>
                <th>작성자</th>
                <th>쓴 때</th>
                <th>신고</th>
                <th>
                  <span className="sr-only">작업</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {comments.items.map((c) => (
                <tr key={c.id}>
                  <td className="wrap">
                    {c.reply && <span className="faint">↳ </span>}
                    {c.secret ? <span className="faint">비밀 댓글입니다</span> : short(c.content ?? '', 60)}
                  </td>
                  <td className="wrap">
                    <Link to={`/posts/${c.postId}#comment-${c.id}`}>{short(c.postTitle, 30)}</Link>
                  </td>
                  <td>{author(c.authorNickname)}</td>
                  <td>{formatDateTime(c.createdAt)}</td>
                  <td>{c.reportCount.toLocaleString()}</td>
                  <td>
                    <button type="button" className="sm danger" onClick={() => remove(`/api/admin/comments/${c.id}`, '댓글')}>
                      삭제
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => go({ page: p })} />}
    </>
  )
}
