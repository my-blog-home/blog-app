import { describe, expect, it } from 'vitest'
import { passwordChecks } from './SignupPage'

// 비밀번호 규칙을 입력 중에 바로 보여 준다 (CF-01-4, CF-01-6)
describe('passwordChecks', () => {
  const ok = (pw: string) => passwordChecks(pw).every((c) => c.ok)

  it('영문·숫자·특수문자를 포함한 8자 이상을 통과시킨다', () => {
    expect(ok('abc123!@')).toBe(true)
    expect(ok('abcd1234!@')).toBe(true)
  })

  it('규칙을 어기면 통과시키지 않는다', () => {
    expect(ok('abc12345')).toBe(false) // 특수문자 없음
    expect(ok('ab1!')).toBe(false) // 너무 짧음
    expect(ok('abcde12345!')).toBe(true) // 11자도 된다
    expect(ok('a1!' + 'x'.repeat(62))).toBe(false) // 65자는 기술적 한도를 넘는다
    expect(ok('abc 123!@')).toBe(false) // 공백
  })
})
