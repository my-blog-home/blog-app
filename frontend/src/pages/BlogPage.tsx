import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { withPreview } from '../preview'
import { ApiError, del, get, put } from '../api/client'
import type { BlogView, PageResult, SubscriptionState } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useMarkCurrentBlog } from '../layout/CurrentBlog'
import Pagination from '../components/Pagination'
import PostList from '../components/PostList'
import { M } from '../messages'
import { categoryColor } from '../colors'
import NotFoundPage from './NotFoundPage'

/**
 * 블로그 화면: 분류 목록과 글 목록. 분류 선택은 페이지를 넘겨도 유지된다 (CF-10)
 * 왼쪽 검색창으로 이 블로그의 제목·본문·태그를 찾는다(?q=). 주인은 비공개 글까지 (FR-070, BR-10)
 */
export default function BlogPage() {
  const { blogId } = useParams()
  const [params, setParams] = useSearchParams()
  const categoryId = params.get('category')
  const q = params.get('q')
  const page = Number(params.get('page') ?? '1')
  const [searchInput, setSearchInput] = useState(q ?? '')
  const [searchError, setSearchError] = useState<string | null>(null)
  const [blog, setBlog] = useState<BlogView | null>(null)
  const [posts, setPosts] = useState<PageResult | null>(null)
  const [missing, setMissing] = useState(false)
  const [categoryMissing, setCategoryMissing] = useState(false)
  const [subscribing, setSubscribing] = useState(false)
  const { me, requireLogin } = useAuth()
  useMarkCurrentBlog(blogId ? Number(blogId) : null)

  const loadBlog = useCallback(() => {
    get<BlogView>(`/api/blogs/${blogId}`)
      .then(setBlog)
      .catch((e) => e instanceof ApiError && e.status === 404 && setMissing(true))
  }, [blogId])

  // 로그인·로그아웃하면 구독 여부와 주인 여부를 다시 읽는다
  useEffect(loadBlog, [loadBlog, me])

  useEffect(() => setSearchInput(q ?? ''), [q])

  useEffect(() => {
    const query = new URLSearchParams({ page: String(page) })
    if (categoryId) query.set('categoryId', categoryId)
    if (q) query.set('q', q)
    setCategoryMissing(false)
    setSearchError(null)
    get<PageResult>(`/api/blogs/${blogId}/posts?${query}`)
      .then(setPosts)
      .catch((e) => {
        // 없는 분류와 방문자에게 숨긴 비공개 분류는 같은 안내 (BR-46)
        if (e instanceof ApiError && e.status === 404 && categoryId) setCategoryMissing(true)
        else if (e instanceof ApiError && q) {
          setSearchError(e.message)
          setPosts({ totalCount: 0, page: 1, totalPages: 1, items: [] })
        }
      })
  }, [blogId, categoryId, q, page, blog?.owner])

  if (missing) return <NotFoundPage />
  if (categoryMissing) return <NotFoundPage message={M.categoryNotFound} />
  if (!blog || !posts) return null

  // 구독은 로그인한 회원이 남의 블로그에만. 비회원이 누르면 로그인 창 (FR-067, BR-12)
  const toggleSubscription = async () => {
    if (subscribing) return
    setSubscribing(true)
    try {
      const url = `/api/blogs/${blog.id}/subscription`
      const next = blog.subscribedByMe ? await del<SubscriptionState>(url) : await put<SubscriptionState>(url)
      setBlog({ ...blog, subscribedByMe: next.subscribed, subscriberCount: next.subscriberCount })
    } finally {
      setSubscribing(false)
    }
  }
  const subscribe = () => {
    if (requireLogin(() => loadBlog())) toggleSubscription()
  }

  const selectCategory = (id: number | null) => setParams(id ? { category: String(id) } : {})
  const search = (e: FormEvent) => {
    e.preventDefault()
    const word = searchInput.trim()
    if ([...word].length < 2) {
      setSearchError(M.searchTooShort)
      return
    }
    setParams({ q: word })
  }
  const clearSearch = () => setParams({})
  const selected = blog.categories.find((c) => String(c.id) === categoryId)

  return (
    <>
      <section className="blog-cover">
        <div className="blog-cover-inner">
          <span className="avatar" style={{ background: blog.ownerColor }}>
            {[...blog.ownerNickname][0]}
          </span>
          <div className="grow">
            <Link to={`/?topic=${blog.topic.id}`} className="topic-badge">
              {blog.topic.name}
            </Link>
            <h1>{blog.name}</h1>
            {blog.description && <p>{blog.description}</p>}
            <p className="stats">
              <Link to={`/users/${blog.ownerId}`} className="author-link">
                {blog.ownerNickname}
              </Link>{' '}
              · 글 {blog.totalPostCount.toLocaleString()}개 · 구독자 {blog.subscriberCount.toLocaleString()}명
            </p>
          </div>
          {!blog.owner && (
            <div className="actions">
              <button
                type="button"
                className={blog.subscribedByMe ? 'subscribe-btn on' : 'subscribe-btn primary'}
                aria-pressed={blog.subscribedByMe}
                disabled={subscribing}
                onClick={subscribe}
              >
                {blog.subscribedByMe ? '구독 중' : '구독'}
              </button>
            </div>
          )}
          {blog.owner && (
            <div className="actions">
              <Link to={withPreview(`/blogs/${blog.id}`)} className="button">
                방문자 화면으로 보기
              </Link>
              <Link to="/manage" className="button">
                블로그 관리
              </Link>
              <Link to="/write" className="button primary">
                글쓰기
              </Link>
            </div>
          )}
        </div>
      </section>
      <div className="blog-layout">
        <aside className="sidebar">
          <form className="blog-search" role="search" onSubmit={search}>
            <input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              maxLength={50}
              placeholder="이 블로그에서 검색"
              aria-label="이 블로그에서 검색"
            />
            <button type="submit" className="sm">
              검색
            </button>
          </form>
          {searchError && <p className="error small">{searchError}</p>}
          <h4>분류</h4>
          <ul className="categories">
            <li>
              <button className={!categoryId && !q ? 'active' : ''} onClick={() => selectCategory(null)}>
                <span className="cat-name">전체</span>
                <span className="count">{blog.totalPostCount}</span>
              </button>
            </li>
            {blog.categories.map((c) => (
              <li key={c.id}>
                <button
                  className={categoryId === String(c.id) ? 'active' : ''}
                  onClick={() => selectCategory(c.id)}
                  title={c.description ?? undefined}
                >
                  <span className="cat-name">
                    <span className="dot" style={{ ['--c' as string]: categoryColor(c.colorIndex) }} />
                    {c.name}
                    {blog.owner && c.visibility === 'PRIVATE' && (
                      <span className="lock" aria-label="비공개 분류" title="비공개 분류">
                        🔒
                      </span>
                    )}
                  </span>
                  <span className="count">{c.postCount}</span>
                </button>
              </li>
            ))}
          </ul>
          {blog.owner && (
            <p className="small">
              <Link to="/manage/categories" className="muted">
                분류 관리
              </Link>
            </p>
          )}
        </aside>
        <section className="grow">
          <div className="list-head">
            {q ? (
              <>
                <span>
                  '{q}' 검색 결과 {posts.totalCount.toLocaleString()}건
                </span>
                <button type="button" className="sm" onClick={clearSearch}>
                  검색 지우기
                </button>
              </>
            ) : (
              <span>
                {selected ? `${selected.name} ` : ''}
                {posts.totalCount}개의 글
              </span>
            )}
          </div>
          {/* 분류를 열면 이름 아래에 그 분류의 소개글을 한 줄로 보인다. 없으면 칸을 만들지 않는다 (BR-34) */}
          {selected?.description && <p className="category-intro">{selected.description}</p>}
          {posts.items.length === 0 && q ? (
            <p className="empty">{M.noResults}</p>
          ) : posts.items.length === 0 ? (
            <div className="empty">
              <p>{M.emptyList}</p>
              {blog.owner && (
                <p>
                  {M.firstPost} <Link to="/write">글쓰기</Link>
                </p>
              )}
            </div>
          ) : (
            <PostList items={posts.items} />
          )}
          <Pagination
            page={posts.page}
            totalPages={posts.totalPages}
            onChange={(p) => {
              const next: Record<string, string> = { page: String(p) }
              if (categoryId) next.category = categoryId
              if (q) next.q = q
              setParams(next)
            }}
          />
        </section>
      </div>
    </>
  )
}
