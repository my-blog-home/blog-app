import { afterEach, describe, expect, it, vi } from 'vitest'
import { applyTheme, currentTheme, THEME_KEY } from './theme'

describe('화면 모드', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('바꾸면 화면에 적용하고 기억한다', () => {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', { getItem: (k: string) => store.get(k) ?? null, setItem: (k: string, v: string) => store.set(k, v) })
    applyTheme('dark')
    expect(currentTheme()).toBe('dark')
    expect(document.documentElement.style.colorScheme).toBe('dark')
    expect(store.get(THEME_KEY)).toBe('dark')
    applyTheme('light')
    expect(currentTheme()).toBe('light')
  })

  it('저장소를 못 써도 화면은 바뀐다', () => {
    vi.stubGlobal('localStorage', { setItem: () => { throw new Error('막힘') } })
    applyTheme('dark')
    expect(currentTheme()).toBe('dark')
  })
})
