import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { useMemo } from 'react'

/**
 * 마크다운을 HTML로 바꾼 뒤 허용한 태그만 남긴다. 원시 HTML과 스크립트는 실행되지 않는다 (research R-10, NF-07).
 */
export function renderMarkdown(source: string): string {
  const html = marked.parse(source, { async: false, breaks: true, gfm: true }) as string
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'del', 'a', 'ul', 'ol', 'li', 'blockquote', 'code', 'pre',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr', 'img', 'table', 'thead', 'tbody', 'tr', 'th', 'td'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'title'],
    ALLOWED_URI_REGEXP: /^(?:https?:|\/|#)/i,
  })
}

export default function MarkdownView({ source }: { source: string }) {
  const html = useMemo(() => renderMarkdown(source), [source])
  return <div className="markdown" dangerouslySetInnerHTML={{ __html: html }} />
}
