/**
 * 방문자 화면 미리보기 (FR-083, BR-44).
 * 블로그 주인이 블로그 홈·글 상세를 주소에 view=visitor를 붙여 열면, 로그인하지 않은 방문자에게 보이는 모습으로 본다.
 * 미리보기 중에는 모든 API 요청에 X-Ylog-View: visitor를 붙이고(서버가 비회원으로 처리하고 조회수·방문자에 세지 않음),
 * 블로그·글 사이를 옮겨 다니는 동안에는 미리보기를 이어 간다. 다른 화면으로 가거나 "내 화면으로 보기"를 누르면 끝난다.
 */

export const PREVIEW_HEADER = 'X-Ylog-View'
export const PREVIEW_PARAM = 'view'
export const PREVIEW_VALUE = 'visitor'

// 미리보기를 이어 가는 화면: 블로그 홈, 글 상세
const PREVIEW_PATH = /^\/(blogs|posts)\/\d+\/?$/

let sticky = false

/** 지금 화면이 미리보기인지. 주소(경로·검색어)를 받아 판단하고, 블로그·글 사이를 옮기는 동안은 이어 간다 */
export function previewActive(pathname: string = window.location.pathname, search: string = window.location.search): boolean {
  if (!PREVIEW_PATH.test(pathname)) {
    sticky = false
    return false
  }
  if (new URLSearchParams(search).get(PREVIEW_PARAM) === PREVIEW_VALUE) {
    sticky = true
    return true
  }
  return sticky
}

/** "내 화면으로 보기"·"블로그 관리로 돌아가기"를 누르면 미리보기를 끝낸다 */
export function leavePreview() {
  sticky = false
}

/** 주소에 view=visitor를 붙인다 (블로그 홈 미리보기 링크) */
export function withPreview(path: string): string {
  return path + (path.includes('?') ? '&' : '?') + `${PREVIEW_PARAM}=${PREVIEW_VALUE}`
}

/** 검색어에서 view=visitor를 뺀다 */
export function withoutPreview(search: string): string {
  const params = new URLSearchParams(search)
  params.delete(PREVIEW_PARAM)
  const rest = params.toString()
  return rest ? `?${rest}` : ''
}
