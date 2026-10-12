import { describe, expect, it } from 'vitest'
import { POLICIES } from '../pages/PolicyPage'
import { fillPlaceholders, LEGAL } from './config'

/** 마크다운용 이스케이프를 벗긴 글 */
const unescape = (s: string) => s.replace(/\\([[\]])/g, '$1')

describe('정책 문서', () => {
  it('세 문서 모두 자리 표시(운영자·연락처·시행일)가 채워진다', () => {
    for (const { text } of Object.values(POLICIES)) {
      const filled = unescape(fillPlaceholders(text))
      expect(filled).not.toMatch(/\{\{[^}]+\}\}/)
      expect(filled).toContain(LEGAL.시행일)
    }
  })

  it('개인정보처리방침에 수집 항목·보유 기간·보호책임자가 있다', () => {
    const text = unescape(fillPlaceholders(POLICIES.privacy.text))
    expect(text).toContain('처리하는 개인정보 항목')
    expect(text).toContain('30일')
    expect(text).toContain(LEGAL.보호책임자)
  })
})

describe('자리 표시자 렌더링', () => {
  it('대괄호 자리 표시자가 링크로 바뀌지 않는다', async () => {
    const { marked } = await import('marked')
    const { default: privacy } = await import('./privacy.md?raw')
    const html = marked.parse(fillPlaceholders(privacy)) as string
    expect(html).not.toMatch(/<a [^>]*>운영자 이름/)
    expect(html).toContain('[운영자 이름]')
  })
})
