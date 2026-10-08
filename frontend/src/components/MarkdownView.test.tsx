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

  it('이미지의 대체 텍스트를 남긴다 (NFR-08)', () => {
    expect(renderMarkdown('![고양이 사진](/images/cat.png)')).toContain('alt="고양이 사진"')
  })

  // 코드 블록 구문 강조 (FR-16)
  it('```java 코드 블록을 강조하고 스크립트는 넣지 않는다', () => {
    const html = renderMarkdown('```java\npublic class A { String s = "</code><script>alert(1)</script>"; }\n```')
    expect(html).toContain('<code class="hljs">')
    expect(html).toContain('<span class="hljs-keyword">public</span>')
    expect(html).not.toContain('<script')
  })

  it('강조용이 아닌 class는 지운다', () => {
    const html = renderMarkdown('<p class="evil">x</p><span class="hljs-keyword">y</span><span class="evil">z</span>')
    expect(html).not.toContain('evil')
    expect(html).toContain('<span class="hljs-keyword">y</span>')
  })
})
