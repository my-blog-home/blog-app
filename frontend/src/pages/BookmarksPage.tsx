import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { get } from '../api/client'
import type { PageResult } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import Pagination from '../components/Pagination'
import PostList from '../components/PostList'
import { useDocumentMeta } from '../meta'

/** 내 북마크: 최근에 저장한 순서. 지금 읽을 수 없는 글은 빠진다 */
export default function BookmarksPage() {
  const { me, loading, requireLogin } = useAuth()
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? '1')
  const [result, setResult] = useState<PageResult | null>(null)
  useDocumentMeta('북마크')

  useEffect(() => {
    if (!loading && !me) requireLogin()
  }, [loading, me, requireLogin])

  useEffect(() => {
    if (me) get<PageResult>(`/api/me/bookmarks?page=${page}`).then(setResult)
  }, [me, page])

  if (!me) return <p className="empty">로그인이 필요합니다</p>
  if (!result) return null
  return (
    <section>
      <div className="list-head">
        <h1>북마크</h1>
        <span className="muted small">{result.totalCount.toLocaleString()}개</span>
      </div>
      {result.items.length === 0 ? (
        <div className="empty">
          <p>아직 북마크한 글이 없어요.</p>
          <p className="small">글 아래의 ☆ 북마크를 누르면 여기에 모여요.</p>
        </div>
      ) : (
        <PostList items={result.items} showBlog />
      )}
      <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => setParams({ page: String(p) })} />
    </section>
  )
}
