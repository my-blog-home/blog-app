import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import { Marked } from 'marked'
import { useMemo } from 'react'

// 코드 블록 구문 강조 (FR-16 선택). 자주 쓰는 언어만 등록해 크기를 작게 둔다 (js·ts·java·python·sql·bash·json·css·html)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('java', java)
hljs.registerLanguage('python', python)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('css', css)
hljs.registerLanguage('xml', xml)

const escapeHtml = (text: string) =>
  text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

const markdown = new Marked({
  async: false,
  breaks: true,
  gfm: true,
  renderer: {
    // ```언어 로 연 코드 블록만 강조한다. 모르는 언어는 글자 그대로 보인다
    code({ text, lang }) {
      const language = (lang ?? '').trim().split(/\s+/)[0].toLowerCase()
      const known = language ? hljs.getLanguage(language) : undefined
      if (known) {
        const highlighted = hljs.highlight(text, { language, ignoreIllegals: true }).value
        return `<pre><code class="hljs">${highlighted}</code></pre>\n`
      }
      return `<pre><code>${escapeHtml(text)}</code></pre>\n`
    },
  },
})

// class는 강조용 span·code에만, highlight.js가 붙이는 이름만 남긴다 (NF-07)
const SAFE_CLASS = /^(?:hljs(?:-[\w-]+)?|[\w-]+_)$/

function keepHighlightClassOnly(node: Element, data: { attrName: string; attrValue: string; keepAttr: boolean }) {
  if (data.attrName !== 'class') return
  const tag = node.nodeName.toLowerCase()
  const classes = data.attrValue.split(/\s+/).filter(Boolean)
  if ((tag !== 'span' && tag !== 'code') || !classes.every((c) => SAFE_CLASS.test(c))) data.keepAttr = false
}

/**
 * 마크다운을 HTML로 바꾼 뒤 허용한 태그만 남긴다. 원시 HTML과 스크립트는 실행되지 않는다 (research R-10, NF-07).
 * 코드 블록 강조에 쓰는 span과, span·code의 hljs class만 더 허용한다 (FR-16).
 */
export function renderMarkdown(source: string): string {
  const html = markdown.parse(source) as string
  DOMPurify.addHook('uponSanitizeAttribute', keepHighlightClassOnly)
  try {
    return DOMPurify.sanitize(html, {
      ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'del', 'a', 'ul', 'ol', 'li', 'blockquote', 'code', 'pre', 'span',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr', 'img', 'table', 'thead', 'tbody', 'tr', 'th', 'td'],
      ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class'],
      ALLOWED_URI_REGEXP: /^(?:https?:|\/|#)/i,
    })
  } finally {
    DOMPurify.removeHook('uponSanitizeAttribute')
  }
}

export default function MarkdownView({ source }: { source: string }) {
  const html = useMemo(() => renderMarkdown(source), [source])
  return <div className="markdown" dangerouslySetInnerHTML={{ __html: html }} />
}
