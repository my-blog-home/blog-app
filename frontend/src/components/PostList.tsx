import { Link } from 'react-router-dom'
import type { PostItem } from '../api/types'
import { formatDate } from '../format'
import Thumb from './Thumb'

/** 글 아래의 좋아요 수와 댓글 수(답글 포함) (FR-085, BR-05) */
export function Reactions({ post }: { post: PostItem }) {
  return (
    <p className="reactions-line" aria-label={`좋아요 ${post.likeCount}개, 댓글 ${post.commentCount}개`}>
      <span aria-hidden="true">♥</span> 좋아요 {post.likeCount.toLocaleString()} · 댓글 {post.commentCount.toLocaleString()}
    </p>
  )
}

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
          <Reactions post={post} />
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
            <span className="card-who">
              <Link to={`/blogs/${post.blogId}`} className="card-blog">
                <span className="avatar sm" style={{ background: post.authorColor }}>
                  {[...post.blogName][0]}
                </span>
                <span>{post.blogName}</span>
              </Link>
              {/* 작성자 이름을 누르면 작성자 프로필 (FR-075) */}
              <Link to={`/users/${post.authorId}`} className="card-author">
                {post.authorNickname}
              </Link>
            </span>
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
          <Reactions post={post} />
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
