export type Visibility = 'PUBLIC' | 'PRIVATE'
export type PostStatus = 'DRAFT' | 'PUBLISHED'

export interface Me {
  id: number
  nickname: string
  blogId: number | null
  profileColor: string
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
}

export interface BlogView {
  id: number
  name: string
  description: string | null
  topic: Ref
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
