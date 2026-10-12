// 서버는 UTC로 주고, 화면은 한국 시간으로 보여 준다
const dateFormat = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' })
const dateTimeFormat = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

export const formatDate = (iso: string) => dateFormat.format(new Date(iso))
export const formatDateTime = (iso: string) => dateTimeFormat.format(new Date(iso))

const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/**
 * 글이 올라온 지 7일 안이면 "방금 전 / N분 전 / N시간 전 / N일 전"으로, 그보다 오래되면 null.
 * 7일 이후는 화면마다 원래대로 날짜(목록) 또는 날짜와 시간(글 상세)을 보여 준다.
 */
export function relativeTime(iso: string, now: number = Date.now()): string | null {
  const diff = now - new Date(iso).getTime()
  if (diff < 0) return '방금 전'
  if (diff < MINUTE) return '방금 전'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}분 전`
  if (diff < DAY) return `${Math.floor(diff / HOUR)}시간 전`
  const days = Math.floor(diff / DAY)
  return days <= 7 ? `${days}일 전` : null
}

/** 목록용: 7일 안은 상대 시간, 그 뒤는 날짜 */
export const formatPostDate = (iso: string, now?: number) => relativeTime(iso, now) ?? formatDate(iso)

/** 글 상세용: 7일 안은 상대 시간, 그 뒤는 날짜와 시간 */
export const formatPostDateTime = (iso: string, now?: number) => relativeTime(iso, now) ?? formatDateTime(iso)
