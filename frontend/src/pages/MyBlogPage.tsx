import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError, get, put } from '../api/client'
import type { BlogView, CategoryView, ManagedPostPage } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import Pagination from '../components/Pagination'
import { categoryColor } from '../colors'
import { formatDate } from '../format'
import { M } from '../messages'
import { useDocumentMeta } from '../meta'
import { STATUS_TABS } from './manage/ManagePostsPage'

/**
 * 내 블로그 (FR-32, CR-59): 위쪽 상자(블로그 이름·분류 수·비공개 분류 수, 블로그 가기·분류 관리·글쓰기),
 * 분류 카드(색 점, 공개 범위, 소개글, 글 수·임시저장 수, 공개로/비공개로·수정), 그 아래 "내가 쓴 글"(공개·비공개·임시저장으로 거르기)과 내 활동.
 */
export default function MyBlogPage() {
  const { me, loading, requireLogin } = useAuth()
  const [params, setParams] = useSearchParams()
  const [blog, setBlog] = useState<BlogView | null>(null)
  const [posts, setPosts] = useState<ManagedPostPage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const statusParam = (params.get('status') ?? '').toUpperCase()
  const status = STATUS_TABS.some((t) => t.value === statusParam) ? statusParam : ''
  const page = Number(params.get('page') ?? '1')
  useDocumentMeta('내 블로그')

  useEffect(() => {
    if (!loading && !me) requireLogin()
  }, [loading, me, requireLogin])

  const loadBlog = useCallback(async () => {
    if (me?.blogId) setBlog(await get<BlogView>(`/api/blogs/${me.blogId}`))
  }, [me?.blogId])

  useEffect(() => {
    loadBlog()
  }, [loadBlog])

  useEffect(() => {
    if (!me?.blogId) return
    const q = new URLSearchParams({ page: String(page) })
    if (status) q.set('status', status)
    get<ManagedPostPage>(`/api/manage/blogs/${me.blogId}/posts?${q}`).then(setPosts)
  }, [me?.blogId, status, page])

  if (!me) return null
  // 관리자 계정은 블로그가 없다 (FR-078)
  if (!me.blogId)
    return (
      <div className="empty">
        <p>블로그가 없는 계정입니다</p>
        <Link to="/me/activity">내 활동 보기</Link>
      </div>
    )
  if (!blog) return null

  const privateCount = blog.categories.filter((c) => c.visibility === 'PRIVATE').length

  // 공개 → 비공개는 바로, 비공개 → 공개는 공개 글이 있으면 한 번 더 묻는다 (BR-46)
  const toggleVisibility = async (category: CategoryView) => {
    const next = category.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC'
    if (next === 'PUBLIC' && category.publicPostCount > 0 && !confirm(M.categoryMakePublicConfirm)) return
    setError(null)
    try {
      await put(`/api/categories/${category.id}/visibility`, { visibility: next })
      await loadBlog()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '잠시 뒤 다시 시도해 주세요')
    }
  }

  const setFilter = (value: string) => {
    const next = new URLSearchParams()
    if (value) next.set('status', value)
    setParams(next)
  }

  return (
    <section className="my-blog">
      <div className="my-blog-top">
        <div>
          <h1>{blog.name}</h1>
          <p>
            분류 {blog.categories.length}개 · 비공개 분류 {privateCount}개
          </p>
        </div>
        <div className="row">
          <Link to={`/blogs/${blog.id}`} className="button sm">
            블로그 가기
          </Link>
          <Link to="/manage/categories" className="button sm">
            분류 관리
          </Link>
          <Link to="/write" className="button sm primary">
            글쓰기
          </Link>
        </div>
      </div>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <h2 className="sr-only">분류</h2>
      <ul className="cat-cards" role="list">
        {blog.categories.map((c) => (
          <li key={c.id} className={c.visibility === 'PRIVATE' ? 'cat-card private' : 'cat-card'}>
            <div className="cat-card-head">
              <span className="dot" style={{ ['--c' as string]: categoryColor(c.colorIndex) }} aria-hidden="true" />
              <span className="name">{c.name}</span>
              {c.visibility === 'PRIVATE' ? <span className="badge">🔒 비공개</span> : <span className="badge public">공개</span>}
            </div>
            {c.description && <p>{c.description}</p>}
            <span className="counts">
              글 {c.postCount}개 · 임시저장 {c.draftCount}개
            </span>
            <div className="row">
              <button type="button" className="sm" onClick={() => toggleVisibility(c)} aria-label={`${c.name} ${c.visibility === 'PUBLIC' ? '비공개로' : '공개로'} 바꾸기`}>
                {c.visibility === 'PUBLIC' ? '비공개로' : '공개로'}
              </button>
              <Link to={`/manage/categories?edit=${c.id}`} className="button sm" aria-label={`${c.name} 수정`}>
                수정
              </Link>
            </div>
          </li>
        ))}
      </ul>

      <nav className="tabs" aria-label="내 글과 활동">
        <button type="button" aria-current="page" className="current">
          내가 쓴 글{posts ? ` ${posts.totalCount}` : ''}
        </button>
        <Link to="/me/activity" className="tab-link">
          내 활동
        </Link>
      </nav>
      <div className="sort-toggle" role="group" aria-label="상태로 거르기" style={{ marginBottom: 16 }}>
        {STATUS_TABS.map((t) => (
          <button key={t.value} type="button" aria-pressed={status === t.value} className={status === t.value ? 'current' : ''} onClick={() => setFilter(t.value)}>
            {t.label}
          </button>
        ))}
      </div>

      {posts &&
        (posts.items.length === 0 ? (
          <div className="empty">
            <p>{status ? M.emptyList : '아직 쓴 글이 없습니다'}</p>
            {!status && <Link to="/write">{M.firstPost}</Link>}
          </div>
        ) : (
          <ul className="post-list">
            {posts.items.map((p) => (
              <li key={p.id}>
                <div className="meta">
                  <span>
                    {p.categoryName}
                    {p.categoryVisibility === 'PRIVATE' && <span aria-label="비공개 분류"> 🔒</span>}
                  </span>
                  <span>{formatDate(p.createdAt)}</span>
                  {p.status === 'DRAFT' ? (
                    <span className="badge">임시저장</span>
                  ) : (
                    p.visibility === 'PRIVATE' && <span className="badge">비공개</span>
                  )}
                </div>
                {/* 임시저장 글은 이어 쓰도록 수정 화면으로 간다 */}
                <Link to={p.status === 'DRAFT' ? `/posts/${p.id}/edit` : `/posts/${p.id}`} className="title">
                  {p.title}
                </Link>
              </li>
            ))}
          </ul>
        ))}
      {posts && (
        <Pagination
          page={posts.page}
          totalPages={posts.totalPages}
          onChange={(p) => {
            const next = new URLSearchParams(params)
            next.set('page', String(p))
            setParams(next)
          }}
        />
      )}
    </section>
  )
}
