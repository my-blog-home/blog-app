export type Visibility = 'PUBLIC' | 'PRIVATE'

export interface Me {
  id: number
  nickname: string
  blogId: number | null
}

export interface PostItem {
  id: number
  title: string
  excerpt: string
  blogId: number
  blogName: string
  categoryId: number
  categoryName: string
  createdAt: string
  visibility: Visibility
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
  colorIndex: number
  isDefault: boolean
  postCount: number
}

export interface BlogView {
  id: number
  name: string
  description: string | null
  ownerNickname: string
  owner: boolean
  totalPostCount: number
  lastUsedCategoryId: number | null
  categories: CategoryView[]
}

export interface Ref {
  id: number
  name: string
}

export interface PostDetail {
  id: number
  blog: Ref
  category: Ref
  title: string
  body: string
  visibility: Visibility
  createdAt: string
  updatedAt: string | null
  prevPostId: number | null
  nextPostId: number | null
  editable: boolean
}

export interface PostSource {
  id: number
  blogId: number
  categoryId: number
  title: string
  body: string
  visibility: Visibility
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
