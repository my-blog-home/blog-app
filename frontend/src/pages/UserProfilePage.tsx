import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, get } from '../api/client'
import type { PageResult, UserProfile } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import BackButton from '../components/BackButton'
import Pagination from '../components/Pagination'
import PostList from '../components/PostList'
import { M } from '../messages'
import NotFoundPage from './NotFoundPage'

/**
 * 작성자 프로필 (FR-075, BR-28): 닉네임·소개·공개 글 수, 블로그 카드, 글 목록.
 * 내 프로필을 내가 보면 "내 프로필" 표시와 비공개 글(개수 포함), 마이페이지·블로그 관리 버튼. 임시저장 글은 없다
 */
export default function UserProfilePage() {
  const { userId } = useParams()
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? '1')
  const { me } = useAuth()
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [posts, setPosts] = useState<PageResult | null>(null)
  const [missing, setMissing] = useState(false)

  useEffect(() => {
    setMissing(false)
    get<UserProfile>(`/api/users/${userId}`)
      .then(setProfile)
      .catch((e) => e instanceof ApiError && e.status === 404 && setMissing(true))
  }, [userId, me])

  useEffect(() => {
    get<PageResult>(`/api/users/${userId}/posts?page=${page}`)
      .then(setPosts)
      .catch(() => setPosts(null))
  }, [userId, page, me])

  if (missing) return <NotFoundPage message={M.memberNotFound} />
  if (!profile) return null
  const blog = profile.blog
  const privateCount = profile.privatePostCount ?? 0

  return (
    <>
      <section className="blog-cover profile-cover">
        <div className="blog-cover-inner">
          <span className="avatar" style={{ background: profile.profileColor }}>
            {[...profile.nickname][0]}
          </span>
          <div className="grow">
            <h1>
              {profile.nickname}
              {profile.isMe && <span className="badge me-badge">내 프로필</span>}
            </h1>
            <p>{profile.bio || '소개가 없습니다'}</p>
            <p className="stats">
              {profile.isMe
                ? `글 ${(profile.publicPostCount + privateCount).toLocaleString()}개${privateCount ? ` (비공개 ${privateCount.toLocaleString()}개 포함)` : ''}`
                : `공개 글 ${profile.publicPostCount.toLocaleString()}개`}
            </p>
          </div>
          {profile.isMe && (
            <div className="actions">
              <Link to="/me" className="button">
                마이페이지
              </Link>
              <Link to="/manage" className="button primary">
                블로그 관리
              </Link>
            </div>
          )}
        </div>
      </section>
      <div className="profile-page">
        <BackButton fallback="/" />
        <h2>{profile.isMe ? '내 블로그' : `${profile.nickname}님의 블로그`}</h2>
        {blog ? (
          <Link to={`/blogs/${blog.id}`} className="blog-card">
            <span className="avatar sm" style={{ background: profile.profileColor }}>
              {[...profile.nickname][0]}
            </span>
            <span className="grow">
              <strong>{blog.name}</strong>
              <span className="blog-card-desc">{blog.description || '소개가 없습니다'}</span>
              <span className="blog-card-stats">
                <span className="badge">{blog.topicName}</span> {profile.isMe ? '글' : '공개 글'}{' '}
                {(profile.isMe ? profile.publicPostCount + privateCount : profile.publicPostCount).toLocaleString()} · 구독자{' '}
                {blog.subscriberCount.toLocaleString()}
              </span>
            </span>
          </Link>
        ) : (
          <p className="empty">아직 블로그가 없습니다.</p>
        )}
        <h2 className="profile-posts-head">
          {profile.isMe ? '내가 쓴 글' : '작성한 글'} <span className="faint">{posts?.totalCount ?? ''}</span>
        </h2>
        {posts &&
          (posts.items.length === 0 ? (
            <p className="empty">{profile.isMe ? '아직 쓴 글이 없습니다.' : '공개된 글이 아직 없습니다.'}</p>
          ) : (
            <PostList items={posts.items} />
          ))}
        {posts && <Pagination page={posts.page} totalPages={posts.totalPages} onChange={(p) => setParams({ page: String(p) })} />}
      </div>
    </>
  )
}
