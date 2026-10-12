import { describe, expect, it } from 'vitest'
import { formatPostDate, formatPostDateTime, relativeTime } from './format'

const now = new Date('2026-10-12T10:00:00Z').getTime()
const ago = (ms: number) => new Date(now - ms).toISOString()
const MIN = 60_000
const HOUR = 60 * MIN
const DAY = 24 * HOUR

// 7일 안의 글은 상대 시간으로, 그 뒤는 날짜로 보여 준다
describe('relativeTime', () => {
  it('1분 안은 방금 전', () => expect(relativeTime(ago(30_000), now)).toBe('방금 전'))
  it('1시간 안은 N분 전', () => expect(relativeTime(ago(5 * MIN), now)).toBe('5분 전'))
  it('하루 안은 N시간 전', () => expect(relativeTime(ago(3 * HOUR), now)).toBe('3시간 전'))
  it('1~7일은 N일 전', () => {
    expect(relativeTime(ago(DAY + HOUR), now)).toBe('1일 전')
    expect(relativeTime(ago(7 * DAY + HOUR), now)).toBe('7일 전')
  })
  it('8일부터는 상대 시간을 쓰지 않는다', () => expect(relativeTime(ago(8 * DAY), now)).toBeNull())
})

describe('formatPostDate / formatPostDateTime', () => {
  it('오래된 글은 목록에 날짜, 상세에 날짜와 시간', () => {
    const old = '2026-09-01T03:04:00Z'
    expect(formatPostDate(old, now)).toBe('2026. 09. 01.')
    expect(formatPostDateTime(old, now)).toContain('2026. 09. 01.')
    expect(formatPostDateTime(old, now)).toMatch(/\d{2}:\d{2}/)
  })
  it('최근 글은 둘 다 상대 시간', () => {
    expect(formatPostDate(ago(2 * DAY), now)).toBe('2일 전')
    expect(formatPostDateTime(ago(2 * HOUR), now)).toBe('2시간 전')
  })
})
