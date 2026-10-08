// 서버 호출 도우미. GET 외 요청에는 CSRF 토큰을 헤더로 보낸다 (NF-11)
// 방문자 화면 미리보기 중에는 모든 요청에 X-Ylog-View: visitor를 붙인다 (FR-083)
import { PREVIEW_HEADER, PREVIEW_VALUE, previewActive } from '../preview'

export interface FieldError {
  field: string
  message: string
}

export class ApiError extends Error {
  status: number
  code: string
  fieldErrors: FieldError[]
  details: Record<string, unknown>

  constructor(status: number, body: { code?: string; message?: string; fieldErrors?: FieldError[]; details?: Record<string, unknown> }) {
    super(body.message ?? '잠시 뒤 다시 시도해 주세요')
    this.status = status
    this.code = body.code ?? 'UNKNOWN'
    this.fieldErrors = body.fieldErrors ?? []
    this.details = body.details ?? {}
  }

  fieldMessage(field: string): string | undefined {
    return this.fieldErrors.find((f) => f.field === field)?.message
  }
}

function csrfToken(): string | undefined {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith('XSRF-TOKEN='))
    ?.split('=')[1]
}

async function ensureCsrf(): Promise<string | undefined> {
  let token = csrfToken()
  if (!token) {
    await fetch('/api/config/limits', { credentials: 'same-origin' })
    token = csrfToken()
  }
  return token ? decodeURIComponent(token) : undefined
}

// 실제 로그인 상태를 알아야 하는 요청(내 정보, 관리 메뉴의 숫자)은 미리보기 중에도 그대로 보낸다
const ACCOUNT_API = /^\/api\/(auth|manage|admin)\//

export async function api<T>(method: string, url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (previewActive() && !ACCOUNT_API.test(url)) headers[PREVIEW_HEADER] = PREVIEW_VALUE
  if (method !== 'GET') {
    const token = await ensureCsrf()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  const response = await fetch(url, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (response.status === 204) return undefined as T
  const text = await response.text()
  const data = text ? JSON.parse(text) : {}
  if (!response.ok) throw new ApiError(response.status, data)
  return data as T
}

export const get = <T>(url: string) => api<T>('GET', url)
export const post = <T>(url: string, body?: unknown) => api<T>('POST', url, body ?? {})
export const put = <T>(url: string, body?: unknown) => api<T>('PUT', url, body ?? {})
export const patch = <T>(url: string, body?: unknown) => api<T>('PATCH', url, body ?? {})
export const del = <T>(url: string, body?: unknown) => api<T>('DELETE', url, body)
