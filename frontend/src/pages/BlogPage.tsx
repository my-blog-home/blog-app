import { useCallback, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, get } from '../api/client'
import type { BlogView, PageResult } from '../api/types'
import Pagination from '../components/Pagination'
import PostList from '../components/PostList'
import { M } from '../messages'
import { categoryColor } from '../colors'
import NotFoundPage from './NotFoundPage'

/** 블로그 화면: 분류 목록과 글 목록. 분류 선택은 페이지를 넘겨도 유지된다 (CF-10) */
export default function BlogPage() {
  const { blogId } = useParams()
  const [params, setParams] = useSearchParams()
  const categoryId = params.get('category')
  const page = Number(params.get('page') ?? '1')
  const [blog, setBlog] = useState<BlogView | null>(null)
  const [posts, setPosts] = useState<PageResult | null>(null)
  const [missing, setMissing] = useState(false)

  const loadBlog = useCallback(() => {
    get<BlogView>(`/api/blogs/${blogId}`)
      .then(setBlog)
      .catch((e) => e instanceof ApiError && e.status === 404 && setMissing(true))
  }, [blogId])

  useEffect(loadBlog, [loadBlog])

  useEffect(() => {
    const query = new URLSearchParams({ page: String(page) })
    if (categoryId) query.set('categoryId', categoryId)
    get<PageResult>(`/api/blogs/${blogId}/posts?${query}`).then(setPosts)
  }, [blogId, categoryId, page, blog?.owner])

  if (missing) return <NotFoundPage />
  if (!blog || !posts) return null

  const selectCategory = (id: number | null) => setParams(id ? { category: String(id) } : {})

  return (
    <>
      <section className="blog-cover">
        <div className="blog-cover-inner">
          <span className="avatar">{[...blog.ownerNickname][0]}</span>
          <div className="grow">
            <h1>{blog.name}</h1>
            {blog.description && <p>{blog.description}</p>}
            <p className="stats">
              {blog.ownerNickname} · 글 {blog.totalPostCount.toLocaleString()}개
            </p>
          </div>
          {blog.owner && (
            <div className="actions">
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
          <h4>분류</h4>
          <ul className="categories">
            <li>
              <button className={!categoryId ? 'active' : ''} onClick={() => selectCategory(null)}>
                <span className="cat-name">전체</span>
                <span className="count">{blog.totalPostCount}</span>
              </button>
            </li>
            {blog.categories.map((c) => (
              <li key={c.id}>
                <button className={categoryId === String(c.id) ? 'active' : ''} onClick={() => selectCategory(c.id)}>
                  <span className="cat-name">
                    <span className="dot" style={{ ['--c' as string]: categoryColor(c.colorIndex) }} />
                    {c.name}
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
            <span>{posts.totalCount}개의 글</span>
          </div>
          {posts.items.length === 0 ? (
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
            onChange={(p) => setParams(categoryId ? { category: categoryId, page: String(p) } : { page: String(p) })}
          />
        </section>
      </div>
    </>
  )
}
