import { useEffect } from 'react'
import { renderMarkdown } from './components/MarkdownView'

// 검색 노출용 제목·설명 (NFR-10). 서버 렌더링 없이 화면마다 document.title과 meta를 바꾼다
export const SITE_NAME = 'Ylog'
export const SITE_DESCRIPTION = 'Ylog는 누구나 블로그를 만들어 글을 쓰고 나누는 블로그 서비스입니다.'
const EXCERPT_LENGTH = 120

/** 마크다운 본문의 글자만 앞에서 120자 (설명 meta에 쓴다) */
export function plainExcerpt(markdown: string, length = EXCERPT_LENGTH): string {
  const doc = new DOMParser().parseFromString(renderMarkdown(markdown), 'text/html')
  const text = (doc.body.textContent ?? '').replace(/\s+/g, ' ').trim()
  const chars = [...text]
  return chars.length > length ? chars.slice(0, length).join('') + '…' : text
}

function setMeta(attr: 'name' | 'property', key: string, content: string) {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute(attr, key)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

/** 제목은 "{title} · Ylog", 없으면 "Ylog". 설명이 없으면 사이트 소개 */
export function applyDocumentMeta(title?: string | null, description?: string | null) {
  const fullTitle = title ? `${title} · ${SITE_NAME}` : SITE_NAME
  const desc = description?.trim() || SITE_DESCRIPTION
  document.title = fullTitle
  setMeta('name', 'description', desc)
  setMeta('property', 'og:title', title || SITE_NAME)
  setMeta('property', 'og:description', desc)
}

/** 화면이 열려 있는 동안 제목·설명을 정하고, 화면을 떠나면 기본값으로 되돌린다 */
export function useDocumentMeta(title?: string | null, description?: string | null) {
  useEffect(() => {
    applyDocumentMeta(title, description)
  }, [title, description])
  useEffect(() => () => applyDocumentMeta(), [])
}
