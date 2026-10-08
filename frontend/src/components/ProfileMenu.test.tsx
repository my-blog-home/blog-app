import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/types'
import ProfileMenu from './ProfileMenu'

const admin: Me = { id: 1, nickname: '관리자', blogId: 3, profileColor: '#c9dcfb', role: 'ADMIN', pendingReportCount: 2, email: 'a@example.com' }

// 프로필 메뉴 순서와 닫기 (CR-60, NFR-08)
describe('ProfileMenu', () => {
  afterEach(cleanup)

  it('정해진 순서로 보이고 Esc로 닫으면 버튼에 초점이 돌아온다', () => {
    render(
      <MemoryRouter>
        <ProfileMenu me={admin} newCommentCount={1} onLogout={vi.fn()} />
      </MemoryRouter>,
    )
    const toggle = screen.getByRole('button', { name: /내 메뉴/ })
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    expect(toggle.querySelector('.notify-dot')).not.toBeNull()
    fireEvent.click(toggle)
    expect(toggle.getAttribute('aria-expanded')).toBe('true')
    const items = screen.getAllByRole('menuitem').map((el) => el.textContent?.replace(/\d+$/, ''))
    expect(items).toEqual(['관리자', '내 블로그', '블로그 관리', '내 활동', '마이페이지', '로그아웃'])
    expect(document.activeElement).toBe(screen.getAllByRole('menuitem')[0])
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('menu')).toBeNull()
    expect(document.activeElement).toBe(toggle)
  })

  it('블로그가 없으면 블로그 관리가 없고 바깥을 누르면 닫힌다', () => {
    render(
      <MemoryRouter>
        <ProfileMenu me={{ ...admin, nickname: '회원', role: 'MEMBER', blogId: null, pendingReportCount: null }} newCommentCount={0} onLogout={vi.fn()} />
      </MemoryRouter>,
    )
    fireEvent.click(screen.getByRole('button', { name: '내 메뉴' }))
    expect(screen.queryByText('블로그 관리')).toBeNull()
    expect(screen.queryByText('관리자')).toBeNull()
    fireEvent.mouseDown(document.body)
    expect(screen.queryByRole('menu')).toBeNull()
  })
})
