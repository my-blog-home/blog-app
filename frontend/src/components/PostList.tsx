import { Link } from 'react-router-dom'
import type { PostItem } from '../api/types'
import { formatDate } from '../format'
import Thumb from './Thumb'

/** 블로그·검색·태그 화면의 한 줄 목록 */
export default function PostList({ items, showBlog = false }: { items: PostItem[]; showBlog?: boolean }) {
  return (
    <ul className="post-list">
      {items.map((post) => (
        <li key={post.id}>
          <div className="meta">
            {showBlog && (
              <Link to={`/blogs/${post.blogId}`} className="card-blog">
                {post.blogName}
              </Link>
            )}
            <span>
              {post.categoryName}
              {post.categoryVisibility === 'PRIVATE' && <span aria-label="비공개 분류"> 🔒</span>}
            </span>
            <span>{formatDate(post.createdAt)}</span>
            {post.visibility === 'PRIVATE' && <span className="badge">비공개</span>}
          </div>
          <Link to={`/posts/${post.id}`} className="title">
            {post.title}
          </Link>
          <p className="excerpt">{post.excerpt}</p>
        </li>
      ))}
    </ul>
  )
}

/** 첫 화면의 카드 목록: 카드 어디를 눌러도 글로, 블로그 이름만 따로 블로그로 간다 */
export function PostCards({ items }: { items: PostItem[] }) {
  return (
    <ul className="card-grid">
      {items.map((post) => (
        <li key={post.id} className="post-card">
          <div className="card-top">
            <Link to={`/blogs/${post.blogId}`} className="card-blog">
              <span className="avatar sm" style={{ background: post.authorColor }}>
                {[...post.blogName][0]}
              </span>
              <span>{post.blogName}</span>
            </Link>
            <span className="card-date">{formatDate(post.createdAt)}</span>
          </div>
          <span className="cat-chip">
            <i />
            {post.topicName} · {post.categoryName}
          </span>
          {post.thumbnailUrl && <Thumb url={post.thumbnailUrl} label={post.categoryName} />}
          <Link to={`/posts/${post.id}`} className="card-link">
            <h3>{post.title}</h3>
          </Link>
          <p>{post.excerpt}</p>
        </li>
      ))}
    </ul>
  )
}

/** 요즘것들처럼 정사각 썸네일 카드를 가로로 넘겨 보는 줄 */
export function ThumbRow({ items }: { items: PostItem[] }) {
  return (
    <ul className="thumb-row">
      {items.map((post) => (
        <li key={post.id}>
          <Link to={`/posts/${post.id}`} className="thumb-card">
            <Thumb url={post.thumbnailUrl} label={post.categoryName} />
            <strong>{post.title}</strong>
            <span>{post.blogName}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
