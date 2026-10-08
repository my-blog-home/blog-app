import { describe, expect, it } from 'vitest'
import { applyDocumentMeta, plainExcerpt, SITE_DESCRIPTION } from './meta'

// 글마다 제목과 설명 meta를 단다 (NFR-10)
describe('meta', () => {
  it('마크다운 기호를 빼고 앞부분만 쓴다', () => {
    expect(plainExcerpt('# 제목\n\n**굵게** 본문')).toBe('제목 굵게 본문')
    expect(plainExcerpt('가'.repeat(130))).toBe('가'.repeat(120) + '…')
  })

  it('제목·설명을 바꾸고 기본값으로 되돌린다', () => {
    applyDocumentMeta('첫 글', '설명')
    expect(document.title).toBe('첫 글 · Ylog')
    expect(document.head.querySelector('meta[name="description"]')?.getAttribute('content')).toBe('설명')
    expect(document.head.querySelector('meta[property="og:title"]')?.getAttribute('content')).toBe('첫 글')
    applyDocumentMeta()
    expect(document.title).toBe('Ylog')
    expect(document.head.querySelector('meta[property="og:description"]')?.getAttribute('content')).toBe(SITE_DESCRIPTION)
  })
})
