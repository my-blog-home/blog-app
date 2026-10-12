import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PostItem } from '../api/types'
import { PostCards, PostFeed } from './PostList'

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

  it('피드형은 이미지가 있는 글에만 넓은 이미지를 두고 제목은 글로 간다', () => {
    const { container } = render(<MemoryRouter><PostFeed items={[post(1, '/images/a.png'), post(2, null)]} /></MemoryRouter>)
    expect(container.querySelectorAll('.feed-post')).toHaveLength(2)
    expect(container.querySelectorAll('.feed-image img')).toHaveLength(1)
    expect(screen.getByText('글 2').closest('a')?.getAttribute('href')).toBe('/posts/2')
  })
})

describe('보기 방식 기억', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('기본은 목록형, 예전 앨범형은 피드형으로 바뀐다', async () => {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => store.set(k, v),
    })
    const { loadView } = await import('../pages/HomePage')
    expect(loadView()).toBe('list')
    localStorage.setItem('ylog.homeView', 'album')
    expect(loadView()).toBe('feed')
    localStorage.setItem('ylog.homeView', 'feed')
    expect(loadView()).toBe('feed')
  })
})
