import { Link } from 'react-router-dom'
import type { PostItem } from '../api/types'
import { formatDate } from '../format'

export default function PostList({ items, showBlog = false }: { items: PostItem[]; showBlog?: boolean }) {
  return (
    <ul className="post-list">
      {items.map((post) => (
        <li key={post.id}>
          <div className="meta">
            {showBlog && <Link to={`/blogs/${post.blogId}`}>{post.blogName}</Link>}
            <span>{post.categoryName}</span>
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
