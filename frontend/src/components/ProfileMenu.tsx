import { useCallback, useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import type { Me } from '../api/types'

/**
 * 프로필 메뉴: 머리글의 아바타를 누르면 열린다 (CR-60).
 * 순서: 관리자(관리자만) → 내 블로그 → 블로그 관리 → 내 활동 → 마이페이지 → 로그아웃.
 * 바깥을 누르거나 Esc로 닫고, 열면 첫 항목에, Esc로 닫으면 아바타 버튼에 초점을 둔다. ↑ ↓ 로 항목을 옮긴다 (NFR-08)
 */
export default function ProfileMenu({ me, newCommentCount, onLogout }: { me: Me; newCommentCount: number; onLogout: () => void }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const toggleRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const menuId = useId()
  const location = useLocation()
  const pending = me.role === 'ADMIN' ? me.pendingReportCount ?? 0 : 0
  const comments = me.blogId ? newCommentCount : 0
  const alerts = pending + comments > 0

  const items = () => [...(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])]

  const close = useCallback((returnFocus: boolean) => {
    setOpen(false)
    if (returnFocus) toggleRef.current?.focus()
  }, [])

  // 페이지를 옮기면 닫는다
  useEffect(() => {
    setOpen(false)
  }, [location.pathname, location.search])

  useEffect(() => {
    if (!open) return
    items()[0]?.focus()
    const onDown = (e: MouseEvent | TouchEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) close(false)
    }
    const onKey = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') close(true)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('touchstart', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('touchstart', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open, close])

  const onMenuKey = (e: KeyboardEvent<HTMLDivElement>) => {
    const list = items()
    const at = list.indexOf(document.activeElement as HTMLElement)
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      const next = e.key === 'ArrowDown' ? (at + 1) % list.length : (at - 1 + list.length) % list.length
      list[next]?.focus()
    } else if (e.key === 'Home' || e.key === 'End') {
      e.preventDefault()
      list[e.key === 'Home' ? 0 : list.length - 1]?.focus()
    } else if (e.key === 'Tab') {
      close(false)
    }
  }

  const onToggleKey = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (e.key === 'ArrowDown' && !open) {
      e.preventDefault()
      setOpen(true)
    }
  }

  const label = ['내 메뉴', comments > 0 && `새 댓글 ${comments}개`, pending > 0 && `처리 대기 신고 ${pending}건`].filter(Boolean).join(', ')

  return (
    <div className="profile-menu" ref={rootRef}>
      <button
        ref={toggleRef}
        type="button"
        className="profile-toggle"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        aria-label={label}
        onClick={() => setOpen((v) => !v)}
        onKeyDown={onToggleKey}
      >
        <span className="avatar sm" style={{ background: me.profileColor }} aria-hidden="true">
          {[...me.nickname][0]}
        </span>
        {alerts && <span className="notify-dot" aria-hidden="true" />}
      </button>
      {open && (
        <div className="dropdown-menu" id={menuId} role="menu" aria-label="내 메뉴" ref={menuRef} onKeyDown={onMenuKey}>
          <div className="menu-head" role="presentation">
            <strong>{me.nickname}</strong>
            <small>{me.email}</small>
          </div>
          {me.role === 'ADMIN' && (
            <Link to="/admin" role="menuitem">
              관리자
              {pending > 0 && (
                <span className="count-badge" aria-label={`처리 대기 신고 ${pending}건`}>
                  {pending}
                </span>
              )}
            </Link>
          )}
          <Link to="/me/blog" role="menuitem">
            내 블로그
          </Link>
          {me.blogId && (
            <Link to="/manage" role="menuitem">
              블로그 관리
              {comments > 0 && (
                <span className="count-badge" aria-label={`새 댓글 ${comments}개`}>
                  {comments}
                </span>
              )}
            </Link>
          )}
          <Link to="/me/activity" role="menuitem">
            내 활동
          </Link>
          <Link to="/me" role="menuitem">
            마이페이지
          </Link>
          <hr role="presentation" />
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false)
              onLogout()
            }}
          >
            로그아웃
          </button>
        </div>
      )}
    </div>
  )
}
