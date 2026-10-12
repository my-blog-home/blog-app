import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import type { PostItem } from '../api/types'
import { PostAlbum, PostCards } from './PostList'

const post = (id: number, thumbnailUrl: string | null): PostItem => ({
  id, title: `글 ${id}`, excerpt: '내용', blogId: 1, blogName: '블로그', categoryId: 1, categoryName: '미분류',
  categoryVisibility: 'PUBLIC', topicId: 1, topicName: '여행', createdAt: new Date().toISOString(), visibility: 'PUBLIC',
  thumbnailUrl, authorColor: '#ccc', likeCount: 0, commentCount: 0, authorId: 1, authorNickname: '작성자',
})

afterEach(cleanup)

describe('첫 화면 글 목록', () => {
  it('목록형은 이미지가 있는 글에만 오른쪽 썸네일을 둔다', () => {
    const { container } = render(<MemoryRouter><PostCards items={[post(1, '/images/a.png'), post(2, null)]} /></MemoryRouter>)
    expect(container.querySelectorAll('.feed-thumb')).toHaveLength(1)
  })

  it('앨범형은 이미지가 없으면 주제 이름 칸을 보인다', () => {
    const { container } = render(<MemoryRouter><PostAlbum items={[post(1, '/images/a.png'), post(2, null)]} /></MemoryRouter>)
    expect(container.querySelectorAll('.album-grid img')).toHaveLength(1)
    expect(screen.getByText('여행')).toBeTruthy()
    expect(screen.getByText('글 2').closest('a')?.getAttribute('href')).toBe('/posts/2')
  })
})
