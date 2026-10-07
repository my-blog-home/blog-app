import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { get } from '../api/client'
import type { PageResult } from '../api/types'
import Pagination from '../components/Pagination'
import PostList from '../components/PostList'
import { M } from '../messages'

/** 첫 화면: 모든 블로그의 최근 공개 글 (contracts/screens.md) */
export default function HomePage() {
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? '1')
  const [result, setResult] = useState<PageResult | null>(null)

  useEffect(() => {
    get<PageResult>(`/api/posts?page=${page}`).then(setResult)
  }, [page])

  if (!result) return null
  return (
    <section>
      <h1>최근 글</h1>
      {result.items.length === 0 ? <p className="empty">{M.emptyList}</p> : <PostList items={result.items} showBlog />}
      <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => setParams({ page: String(p) })} />
    </section>
  )
}
