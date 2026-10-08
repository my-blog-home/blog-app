// 글쓰기 화면의 마크다운 도구 버튼 (FR-084). 고른 글자를 감싸거나 줄 앞에 기호를 붙인다

export type Format = 'h2' | 'h3' | 'bold' | 'italic' | 'strike' | 'ul' | 'ol' | 'quote' | 'link' | 'code'

export interface Edit {
  text: string
  selectionStart: number
  selectionEnd: number
}

export const TOOLBAR: { format: Format; label: string; title: string }[] = [
  { format: 'h2', label: 'H2', title: '제목2' },
  { format: 'h3', label: 'H3', title: '제목3' },
  { format: 'bold', label: 'B', title: '굵게' },
  { format: 'italic', label: 'I', title: '기울임' },
  { format: 'strike', label: 'S', title: '취소선' },
  { format: 'ul', label: '•', title: '목록' },
  { format: 'ol', label: '1.', title: '번호 목록' },
  { format: 'quote', label: '❝', title: '인용' },
  { format: 'link', label: '🔗', title: '링크' },
  { format: 'code', label: '</>', title: '코드' },
]

const WRAP: Partial<Record<Format, { mark: string; placeholder: string }>> = {
  bold: { mark: '**', placeholder: '굵은 글씨' },
  italic: { mark: '*', placeholder: '기울인 글씨' },
  strike: { mark: '~~', placeholder: '취소선' },
}

const LINE_PREFIX: Partial<Record<Format, string>> = { h2: '## ', h3: '### ', ul: '- ', quote: '> ' }

/** 고른 글자(없으면 안내 글자)를 앞뒤 기호로 감싸고, 감싼 글자를 다시 고른다 */
function wrap(text: string, start: number, end: number, mark: string, placeholder: string): Edit {
  const selected = text.slice(start, end) || placeholder
  const next = text.slice(0, start) + mark + selected + mark + text.slice(end)
  return { text: next, selectionStart: start + mark.length, selectionEnd: start + mark.length + selected.length }
}

/** 고른 범위가 걸친 줄 전체에 기호를 붙인다. 모든 줄에 이미 있으면 뗀다 */
function prefixLines(text: string, start: number, end: number, prefixFor: (i: number) => string, pattern: RegExp): Edit {
  const lineStart = text.lastIndexOf('\n', start - 1) + 1
  let lineEnd = text.indexOf('\n', end > start && text[end - 1] === '\n' ? end - 1 : end)
  if (lineEnd === -1) lineEnd = text.length
  const lines = text.slice(lineStart, lineEnd).split('\n')
  const remove = lines.every((line) => pattern.test(line))
  const changed = lines.map((line, i) => (remove ? line.replace(pattern, '') : prefixFor(i) + line.replace(/^(#{1,6} |> |- |\d+\. )/, '')))
  const block = changed.join('\n')
  return {
    text: text.slice(0, lineStart) + block + text.slice(lineEnd),
    selectionStart: lineStart,
    selectionEnd: lineStart + block.length,
  }
}

export function applyFormat(text: string, start: number, end: number, format: Format): Edit {
  const wrapper = WRAP[format]
  if (wrapper) return wrap(text, start, end, wrapper.mark, wrapper.placeholder)
  const prefix = LINE_PREFIX[format]
  if (prefix) {
    const escaped = prefix.replace(/[#>\-\s]/g, (c) => (c === ' ' ? ' ' : '\\' + c))
    return prefixLines(text, start, end, () => prefix, new RegExp('^' + escaped))
  }
  if (format === 'ol') return prefixLines(text, start, end, (i) => `${i + 1}. `, /^\d+\. /)
  if (format === 'link') {
    const label = text.slice(start, end) || '링크 텍스트'
    const url = 'https://'
    const inserted = `[${label}](${url})`
    const urlStart = start + label.length + 3
    return { text: text.slice(0, start) + inserted + text.slice(end), selectionStart: urlStart, selectionEnd: urlStart + url.length }
  }
  // 코드: 여러 줄이면 코드 블록, 한 줄이면 `코드`
  const selected = text.slice(start, end)
  if (selected.includes('\n')) {
    const before = start > 0 && text[start - 1] !== '\n' ? '\n' : ''
    const block = `${before}\`\`\`\n${selected.replace(/\n$/, '')}\n\`\`\`\n`
    const codeStart = start + before.length + 4
    return {
      text: text.slice(0, start) + block + text.slice(end),
      selectionStart: codeStart,
      selectionEnd: codeStart + selected.replace(/\n$/, '').length,
    }
  }
  return wrap(text, start, end, '`', '코드')
}

/** 커서 자리에 글자를 넣는다. 앞뒤가 줄 중간이면 줄을 바꿔 이미지가 한 줄을 차지하게 한다 */
export function insertBlock(text: string, at: number, block: string): Edit {
  const before = at > 0 && text[at - 1] !== '\n' ? '\n' : ''
  const after = at < text.length && text[at] !== '\n' ? '\n' : ''
  const inserted = before + block + after
  const cursor = at + before.length + block.length
  return { text: text.slice(0, at) + inserted + text.slice(at), selectionStart: cursor, selectionEnd: cursor }
}

/** 본문에 이미 들어 있는 올린 이미지 수 */
export function countImages(body: string): number {
  return (body.match(/!\[[^\]]*]\(\/images\/[^)]+\)/g) ?? []).length
}
