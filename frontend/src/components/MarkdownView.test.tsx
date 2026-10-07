import { describe, expect, it } from 'vitest'
import { renderMarkdown } from './MarkdownView'

// 본문에 스크립트를 넣어도 실행되지 않는다 (FR-053, quickstart S1)
describe('renderMarkdown', () => {
  it('script 태그를 지운다', () => {
    expect(renderMarkdown('<script>alert(1)</script>안녕')).not.toContain('<script')
  })

  it('javascript: 링크를 지운다', () => {
    expect(renderMarkdown('[x](javascript:alert(1))')).not.toContain('javascript:')
  })

  it('이벤트 속성을 지운다', () => {
    const html = renderMarkdown('<img src=x onerror=alert(1)>')
    expect(html).not.toContain('onerror')
  })

  it('일반 마크다운은 그대로 보여 준다', () => {
    const html = renderMarkdown('# 제목\n\n**굵게** [링크](https://example.com)')
    expect(html).toContain('<h1>제목</h1>')
    expect(html).toContain('<strong>굵게</strong>')
    expect(html).toContain('href="https://example.com"')
  })
})
