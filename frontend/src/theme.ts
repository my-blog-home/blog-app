import { useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'

/** 고른 화면 모드를 이 브라우저에 기억하는 키. index.html의 첫 그림 전 스크립트와 같다 */
export const THEME_KEY = 'ylog.theme'
const CHANGED = 'ylog:theme'

export function currentTheme(): Theme {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

/** 화면에 적용하고 기억한다. 저장소를 못 쓰면 이번 화면에서만 바뀐다 */
export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
  try {
    localStorage.setItem(THEME_KEY, theme)
  } catch {
    // 기억하지 못해도 화면은 바뀐다
  }
  window.dispatchEvent(new Event(CHANGED))
}

/** 머리글과 햄버거 메뉴의 전환 버튼이 같은 상태를 보게 한다 */
export function useTheme(): [Theme, () => void] {
  const [theme, setTheme] = useState<Theme>(currentTheme)
  useEffect(() => {
    const sync = () => setTheme(currentTheme())
    window.addEventListener(CHANGED, sync)
    return () => window.removeEventListener(CHANGED, sync)
  }, [])
  return [theme, () => applyTheme(theme === 'dark' ? 'light' : 'dark')]
}
