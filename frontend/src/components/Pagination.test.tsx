import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Pagination, { pageWindow } from './Pagination'

// 페이지 번호는 5개씩, 현재 페이지를 가운데에, 처음·끝에서는 이전·다음이 눌리지 않는다 (BR-09)
describe('pageWindow', () => {
  it('페이지가 5개 이하이면 모두 보인다', () => {
    expect(pageWindow(1, 1)).toEqual([1])
    expect(pageWindow(2, 3)).toEqual([1, 2, 3])
    expect(pageWindow(5, 5)).toEqual([1, 2, 3, 4, 5])
  })

  it('현재 페이지를 가운데에 둔다', () => {
    expect(pageWindow(5, 20)).toEqual([3, 4, 5, 6, 7])
    expect(pageWindow(10, 20)).toEqual([8, 9, 10, 11, 12])
  })

  it('처음·끝 근처에서는 창을 안쪽으로 민다', () => {
    expect(pageWindow(1, 20)).toEqual([1, 2, 3, 4, 5])
    expect(pageWindow(2, 20)).toEqual([1, 2, 3, 4, 5])
    expect(pageWindow(19, 20)).toEqual([16, 17, 18, 19, 20])
    expect(pageWindow(20, 20)).toEqual([16, 17, 18, 19, 20])
  })

  it('범위를 벗어난 페이지는 끝에 맞춘다', () => {
    expect(pageWindow(99, 7)).toEqual([3, 4, 5, 6, 7])
    expect(pageWindow(0, 0)).toEqual([])
  })
})

describe('Pagination', () => {
  afterEach(cleanup)

  it('첫 쪽에서는 이전이, 마지막 쪽에서는 다음이 눌리지 않는다', () => {
    const { rerender } = render(<Pagination page={1} totalPages={8} onChange={() => {}} />)
    expect(screen.getByText('이전').closest('button')!.disabled).toBe(true)
    expect(screen.getByText('다음').closest('button')!.disabled).toBe(false)
    rerender(<Pagination page={8} totalPages={8} onChange={() => {}} />)
    expect(screen.getByText('이전').closest('button')!.disabled).toBe(false)
    expect(screen.getByText('다음').closest('button')!.disabled).toBe(true)
  })

  it('번호는 5개만 보이고 현재 페이지가 표시된다', () => {
    const onChange = vi.fn()
    render(<Pagination page={6} totalPages={12} onChange={onChange} />)
    const numbers = screen.getAllByRole('button').map((b) => b.textContent).filter((t) => /^\d+$/.test(t ?? ''))
    expect(numbers).toEqual(['4', '5', '6', '7', '8'])
    expect(screen.getByText('6').getAttribute('aria-current')).toBe('page')
    fireEvent.click(screen.getByText('다음'))
    expect(onChange).toHaveBeenCalledWith(7)
  })

  it('한 쪽뿐이면 보이지 않는다', () => {
    const { container } = render(<Pagination page={1} totalPages={1} onChange={() => {}} />)
    expect(container.innerHTML).toBe('')
  })
})
