export type Visibility = 'PUBLIC' | 'PRIVATE'
export type PostStatus = 'DRAFT' | 'PUBLISHED'

export type MemberRole = 'MEMBER' | 'ADMIN'

/** 관리자는 블로그가 없을 수 있다. pendingReportCount는 관리자에게만 온다 (FR-078, FR-079) */
export interface Me {
  id: number
  nickname: string
  blogId: number | null
  profileColor: string
  role: MemberRole
  pendingReportCount: number | null
  /** 프로필 메뉴 머리에 보인다 (CR-60) */
  email: string
}

export interface Topic {
  id: number
  name: string
  postCount: number
}

export interface PostItem {
  id: number
  title: string
  excerpt: string
  blogId: number
  blogName: string
  categoryId: number
  categoryName: string
  categoryVisibility: Visibility
  topicId: number
  topicName: string
  createdAt: string
  visibility: Visibility
  thumbnailUrl: string | null
  authorColor: string
  // 좋아요 수와 댓글 수(답글 포함) (FR-085)
  likeCount: number
  commentCount: number
  authorId: number
  authorNickname: string
}

export interface PageResult {
  totalCount: number
  page: number
  totalPages: number
  items: PostItem[]
}

export interface CategoryView {
  id: number
  name: string
  description: string | null
  visibility: Visibility
  colorIndex: number
  isDefault: boolean
  postCount: number
  publicPostCount: number
  /** 임시저장 글 수. 주인에게만 채워진다 (FR-32) */
  draftCount: number
}

export interface BlogView {
  id: number
  name: string
  description: string | null
  topic: Ref
  ownerId: number
  ownerNickname: string
  ownerColor: string
  owner: boolean
  totalPostCount: number
  lastUsedCategoryId: number | null
  lastUsedTopicId: number | null
  categories: CategoryView[]
  subscriberCount: number
  subscribedByMe: boolean
}

export interface Ref {
  id: number
  name: string
}

export interface PostDetail {
  id: number
  blog: Ref
  category: Ref
  topic: Ref
  title: string
  body: string
  visibility: Visibility
  status: PostStatus
  createdAt: string | null
  updatedAt: string | null
  prevPostId: number | null
  nextPostId: number | null
  editable: boolean
  authorColor: string
  likeCount: number
  likedByMe: boolean
  commentCount: number
  tags: string[]
  viewCount: number
  authorId: number
  authorNickname: string
}

export interface PostSource {
  id: number
  blogId: number
  categoryId: number
  topicId: number
  title: string
  body: string
  visibility: Visibility
  status: PostStatus
  tags: string[]
}

export interface Limits {
  nicknameMin: number
  nicknameMax: number
  passwordMin: number
  passwordMax: number
  postTitleMax: number
  postBodyMax: number
  blogNameMax: number
  blogDescriptionMax: number
  categoryNameMax: number
  searchMin: number
  searchMax: number
}

// 댓글 한 개. hidden이면 가려진 비밀 댓글이라 content가 없다 (FR-065, FR-066)
export interface CommentView {
  id: number
  authorId: number | null
  authorNickname: string | null
  // 작성자가 탈퇴했으면 참. 번호·닉네임·색은 비어 있고 "탈퇴한 사용자"로 보인다 (FR-086)
  authorWithdrawn: boolean
  authorColor: string | null
  isBlogOwner: boolean
  content: string | null
  secret: boolean
  hidden: boolean
  createdAt: string
  deletable: boolean
  canReply: boolean
  reportable: boolean
  reportedByMe: boolean
  replies: CommentView[]
}

export interface SubscriptionState {
  subscribed: boolean
  subscriberCount: number
}

export interface MySubscriptions {
  totalCount: number
  items: { blogId: number; blogName: string; ownerNickname: string }[]
}

// 내 활동의 댓글 단 글 (FR-069)
export interface CommentedPost {
  postId: number
  postTitle: string
  blogId: number
  blogName: string
  commentId: number
  excerpt: string
  secret: boolean
  reply: boolean
  commentedAt: string
  otherCount: number
}

export interface CommentedPostPage {
  totalCount: number
  page: number
  totalPages: number
  items: CommentedPost[]
}

// 첫 화면의 지금 핫한 글 (FR-072)
export interface HotPost {
  id: number
  title: string
  blogId: number
  blogName: string
  categoryName: string
  authorNickname: string
  authorColor: string
  viewCount: number
  likeCount: number
  commentCount: number
  topicName: string
}

// 이번 주 인기 블로거 (FR-074)
export interface HotBlogger {
  rank: number
  blogId: number
  blogName: string
  ownerId: number
  ownerNickname: string
  ownerColor: string
  subscriberCount: number
  score: number
}

// 실시간 인기 검색어 (FR-073). change: 1시간 전보다 오른 단계(내리면 음수)
export interface PopularKeywords {
  asOf: string
  items: { rank: number; keyword: string; change: number; isNew: boolean }[]
}

// 공지·이용 안내 (FR-077)
export type NoticeType = 'NOTICE' | 'GUIDE'

export interface NoticeItem {
  id: number
  type: NoticeType
  title: string
  pinned: boolean
  createdAt: string
}

export interface NoticePage {
  totalCount: number
  page: number
  totalPages: number
  items: NoticeItem[]
}

export interface NoticeDetail extends NoticeItem {
  content: string
  updatedAt: string | null
  others: NoticeItem[]
}

// 작성자 프로필 (FR-075). privatePostCount는 내 프로필일 때만 있다
export interface UserProfile {
  id: number
  nickname: string
  bio: string | null
  profileColor: string
  publicPostCount: number
  privatePostCount: number | null
  isMe: boolean
  blog: { id: number; name: string; description: string | null; topicName: string; subscriberCount: number } | null
}

/** 글 관리·내가 쓴 글 목록의 한 줄 (BM-03, FR-32) */
export interface ManagedPost {
  id: number
  title: string
  categoryName: string
  categoryVisibility: Visibility
  createdAt: string
  visibility: Visibility
  status: PostStatus
  viewCount: number
  commentCount: number
}

export interface ManagedPostPage {
  totalCount: number
  page: number
  totalPages: number
  items: ManagedPost[]
}
