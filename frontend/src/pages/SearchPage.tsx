import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError, get } from '../api/client'
import type { PageResult } from '../api/types'
import Pagination from '../components/Pagination'
import PostList from '../components/PostList'
import { M } from '../messages'
import { useDocumentMeta } from '../meta'

/** 검색 결과. 검색창에 검색어가 남아 있다 (CF-11) */
export default function SearchPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const page = Number(params.get('page') ?? '1')
  const [input, setInput] = useState(q)
  const [result, setResult] = useState<PageResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  useDocumentMeta(q ? `"${q}" 검색 결과` : '검색 결과')

  useEffect(() => {
    setInput(q)
    setResult(null)
    if ([...q.trim()].length < 2) {
      setError(M.searchTooShort)
      return
    }
    setError(null)
    get<PageResult>(`/api/search?q=${encodeURIComponent(q)}&page=${page}`)
      .then(setResult)
      .catch((e) => setError(e instanceof ApiError ? e.message : '잠시 뒤 다시 시도해 주세요'))
  }, [q, page])

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setParams({ q: input.trim() })
  }

  return (
    <section>
      <form className="search-form" onSubmit={submit}>
        <input value={input} onChange={(e) => setInput(e.target.value)} maxLength={50} aria-label="검색어" />
        <button className="primary">검색</button>
      </form>
      {error && <p className="error">{error}</p>}
      {result && (
        <>
          <p className="muted">검색 결과 {result.totalCount}건</p>
          {result.items.length === 0 ? <p className="empty">{M.noResults}</p> : <PostList items={result.items} showBlog />}
          <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => setParams({ q, page: String(p) })} />
        </>
      )}
    </section>
  )
}
