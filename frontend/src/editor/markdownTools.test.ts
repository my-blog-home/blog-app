import { describe, expect, it } from 'vitest'
import { applyFormat, countImages, insertBlock } from './markdownTools'

// 마크다운 도구 버튼 (FR-084)
describe('applyFormat', () => {
  it('고른 글자를 굵게·기울임·취소선으로 감싼다', () => {
    expect(applyFormat('안녕 세상', 3, 5, 'bold')).toEqual({ text: '안녕 **세상**', selectionStart: 5, selectionEnd: 7 })
    expect(applyFormat('abc', 0, 3, 'italic').text).toBe('*abc*')
    expect(applyFormat('abc', 1, 2, 'strike').text).toBe('a~~b~~c')
  })

  it('고른 글자가 없으면 안내 글자를 넣고 고른다', () => {
    const edit = applyFormat('', 0, 0, 'bold')
    expect(edit.text).toBe('**굵은 글씨**')
    expect(edit.text.slice(edit.selectionStart, edit.selectionEnd)).toBe('굵은 글씨')
  })

  it('제목·목록·인용은 줄 앞에 붙이고 다시 누르면 뗀다', () => {
    expect(applyFormat('첫 줄\n둘째 줄', 2, 2, 'h2').text).toBe('## 첫 줄\n둘째 줄')
    expect(applyFormat('## 첫 줄', 0, 0, 'h3').text).toBe('### 첫 줄')
    expect(applyFormat('가\n나', 0, 3, 'ul').text).toBe('- 가\n- 나')
    expect(applyFormat('- 가\n- 나', 0, 7, 'ul').text).toBe('가\n나')
    expect(applyFormat('가\n나', 0, 3, 'ol').text).toBe('1. 가\n2. 나')
    expect(applyFormat('말', 0, 1, 'quote').text).toBe('> 말')
  })

  it('링크는 주소 자리를 고른다', () => {
    const edit = applyFormat('여기', 0, 2, 'link')
    expect(edit.text).toBe('[여기](https://)')
    expect(edit.text.slice(edit.selectionStart, edit.selectionEnd)).toBe('https://')
  })

  it('코드는 한 줄이면 `코드`, 여러 줄이면 코드 블록', () => {
    expect(applyFormat('a b', 2, 3, 'code').text).toBe('a `b`')
    expect(applyFormat('x\ny', 0, 3, 'code').text).toBe('```\nx\ny\n```\n')
  })
})

describe('insertBlock', () => {
  it('줄 중간이면 줄을 바꿔 넣는다', () => {
    expect(insertBlock('앞뒤', 1, '![](/images/a.png)').text).toBe('앞\n![](/images/a.png)\n뒤')
    expect(insertBlock('', 0, '![](/images/a.png)').text).toBe('![](/images/a.png)')
  })
})

describe('countImages', () => {
  it('올린 이미지만 센다', () => {
    expect(countImages('![](/images/a.png) ![x](/images/b.jpg) ![](https://e.com/c.png)')).toBe(2)
  })
})
