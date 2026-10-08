import { useEffect, useRef } from 'react'

/**
 * 창(모달) 접근성 (NFR-08): 열면 창 안의 첫 입력칸(없으면 첫 버튼)에 초점을 두고, Esc로 닫으며,
 * 닫히면 창을 열기 전 초점이 있던 곳으로 되돌린다.
 */
export function useDialog<T extends HTMLElement>(onClose: () => void) {
  const ref = useRef<T>(null)
  const closeRef = useRef(onClose)
  closeRef.current = onClose

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const dialog = ref.current
    const first =
      dialog?.querySelector<HTMLElement>('input:not([disabled]), textarea:not([disabled]), select:not([disabled])') ??
      dialog?.querySelector<HTMLElement>('button:not([disabled]), a[href]')
    first?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeRef.current()
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      if (previous && document.contains(previous)) previous.focus()
    }
  }, [])

  return ref
}
