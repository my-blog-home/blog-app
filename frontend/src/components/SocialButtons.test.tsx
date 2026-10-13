import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SocialButtons from './SocialButtons'

vi.mock('../api/client', () => ({ get: vi.fn(() => Promise.resolve({ providers: ['github'] })) }))

afterEach(cleanup)

describe('소셜 로그인 버튼', () => {
  it('설정된 제공자는 로그인으로 가고, 나머지는 버튼만 두고 준비 중이라고 알린다', async () => {
    render(<SocialButtons />)
    await waitFor(() => expect(screen.getByText('GitHub로 계속하기').closest('a')?.getAttribute('href')).toBe('/oauth2/authorization/github'))
    const kakao = screen.getByText('카카오로 계속하기')
    expect(kakao.closest('a')).toBeNull()
    expect(kakao.getAttribute('aria-disabled')).toBe('true')
    expect(screen.getByText('Google로 계속하기').closest('a')).toBeNull()
    fireEvent.click(kakao)
    expect(document.getElementById('social-tip-kakao')?.className).toContain('show')
    expect(document.getElementById('social-tip-kakao')?.textContent).toBe('준비 중입니다')
  })
})
