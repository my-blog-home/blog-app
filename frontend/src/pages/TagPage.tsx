import { useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { get } from '../api/client'
import type { PageResult } from '../api/types'
import Pagination from '../components/Pagination'
import PostList from '../components/PostList'
import { M } from '../messages'
import { useDocumentMeta } from '../meta'

/** 같은 태그가 붙은 공개 글 목록 (CF-20-3) */
export default function TagPage() {
  const { name = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? '1')
  const [result, setResult] = useState<PageResult | null>(null)
  useDocumentMeta(`#${name}`)

  useEffect(() => {
    get<PageResult>(`/api/tags/${encodeURIComponent(name)}/posts?page=${page}`).then(setResult)
  }, [name, page])

  if (!result) return null
  return (
    <section>
      <h1>#{name}</h1>
      <p className="muted">{result.totalCount}개의 글</p>
      {result.items.length === 0 ? <p className="empty">{M.emptyList}</p> : <PostList items={result.items} showBlog />}
      <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => setParams({ page: String(p) })} />
    </section>
  )
}
