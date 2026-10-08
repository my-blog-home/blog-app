import { describe, expect, it } from 'vitest'
import { leavePreview, previewActive, withPreview, withoutPreview } from './preview'

describe('방문자 화면 미리보기 (FR-083)', () => {
  it('블로그·글 주소에 view=visitor가 있으면 켜지고, 블로그·글 사이를 옮기는 동안 이어진다', () => {
    expect(previewActive('/blogs/3', '?view=visitor')).toBe(true)
    expect(previewActive('/posts/9', '')).toBe(true)
    expect(previewActive('/blogs/3', '?page=2')).toBe(true)
  })

  it('다른 화면으로 가거나 내 화면으로 보기를 누르면 끝난다', () => {
    expect(previewActive('/blogs/3', '?view=visitor')).toBe(true)
    expect(previewActive('/users/1', '')).toBe(false)
    expect(previewActive('/posts/9', '')).toBe(false)

    expect(previewActive('/posts/9', '?view=visitor')).toBe(true)
    leavePreview()
    expect(previewActive('/posts/9', '')).toBe(false)
  })

  it('블로그·글이 아닌 화면에서는 view=visitor가 있어도 켜지지 않는다', () => {
    expect(previewActive('/manage', '?view=visitor')).toBe(false)
  })

  it('주소에 붙이고 뺀다', () => {
    expect(withPreview('/blogs/3')).toBe('/blogs/3?view=visitor')
    expect(withPreview('/blogs/3?page=2')).toBe('/blogs/3?page=2&view=visitor')
    expect(withoutPreview('?page=2&view=visitor')).toBe('?page=2')
    expect(withoutPreview('?view=visitor')).toBe('')
  })
})
