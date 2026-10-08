import { describe, expect, it } from 'vitest'
import { moveId } from './CategoryManager'

// 끌어서 놓은 자리로 분류 순서를 바꾼다 (FR-17, CR-31)
describe('moveId', () => {
  it('빼고 난 목록의 자리에 넣는다', () => {
    expect(moveId([1, 2, 3, 4], 1, 2)).toEqual([2, 3, 1, 4])
    expect(moveId([1, 2, 3, 4], 4, 0)).toEqual([4, 1, 2, 3])
    expect(moveId([1, 2, 3], 2, 9)).toEqual([1, 3, 2])
  })
})
