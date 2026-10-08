import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import PlainText, { paragraphs } from './PlainText'

// 공지 내용은 글자로만 보여 준다: 빈 줄로 문단, 줄바꿈은 그대로, HTML은 해석하지 않는다 (FR-077)
describe('PlainText', () => {
  it('빈 줄로 문단을 나눈다', () => {
    expect(paragraphs('첫 문단\n둘째 줄\n\n\n둘째 문단')).toEqual([['첫 문단', '둘째 줄'], ['둘째 문단']])
  })

  it('HTML 태그를 글자 그대로 보인다', () => {
    const { container } = render(<PlainText text={'<script>alert(1)</script>\n\n<b>굵게</b>'} />)
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
    expect(container.querySelectorAll('p')).toHaveLength(2)
    expect(container.textContent).toContain('<b>굵게</b>')
  })
})
