// 관리자 화면에서 쓰는 서버 응답 모양 (FR-078~082)

export type ReportStatus = 'PENDING' | 'RESOLVED' | 'REJECTED'
export type ReportStatusFilter = ReportStatus | 'ALL'

export interface ReportAuthor {
  id: number | null
  nickname: string
  withdrawn: boolean
  admin: boolean
  suspended: boolean
}

export interface ReportEntry {
  id: number
  reporterNickname: string | null
  reason: string
  reasonLabel: string
  detail: string | null
  createdAt: string
}

/** 같은 대상(글·댓글)의 신고를 모은 한 줄. exists가 거짓이면 대상이 이미 지워졌다 */
export interface ReportGroup {
  key: string
  targetType: 'POST' | 'COMMENT'
  exists: boolean
  postId: number | null
  commentId: number | null
  postTitle: string | null
  targetText: string
  targetAuthor: ReportAuthor
  status: ReportStatus
  reportCount: number
  reasonSummary: string
  reasons: Record<string, number>
  reporterSummary: string
  reports: ReportEntry[]
  reportIds: number[]
  latestAt: string
  handledBy: string | null
  handledAt: string | null
  handleNote: string | null
  canSuspend: boolean
}

export type StatusCounts = Record<ReportStatusFilter, number>

export interface ReportPage {
  counts: StatusCounts
  totalCount: number
  page: number
  totalPages: number
  items: ReportGroup[]
}

export interface AdminSummary {
  counts: StatusCounts
  noticeCount: number
  latestPending: ReportGroup[]
}

export interface ProcessResult {
  handledCount: number
  deletedCount: number
  suspendedMemberId: number | null
  message: string
}

export type MemberStatus = 'ALL' | 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN'

export interface CurrentSuspension {
  startsAt: string
  endsAt: string | null
  reason: string
}

/** 탈퇴한 회원은 닉네임·이메일이 비어 있다 */
export interface MemberRow {
  id: number
  nickname: string | null
  email: string | null
  joinedAt: string
  withdrawnAt: string | null
  role: 'MEMBER' | 'ADMIN'
  status: Exclude<MemberStatus, 'ALL'>
  blog: { id: number; name: string } | null
  postCount: number
  reportCount: number
  resolvedReportCount: number
  suspensionCount: number
  suspension: CurrentSuspension | null
  canSuspend: boolean
}

export interface MemberPage {
  counts: Record<MemberStatus, number>
  totalCount: number
  page: number
  totalPages: number
  items: MemberRow[]
}

export interface SuspensionHistory {
  id: number
  reason: string
  startsAt: string
  endsAt: string | null
  permanent: boolean
  active: boolean
  liftedAt: string | null
  createdBy: string | null
  liftedBy: string | null
  reportId: number | null
}

/** 정지 기간: 3·7·30일, null은 영구 (FR-080) */
export const SUSPEND_OPTIONS: { value: number | null; label: string }[] = [
  { value: 3, label: '3일' },
  { value: 7, label: '7일' },
  { value: 30, label: '30일' },
  { value: null, label: '영구' },
]

export const REPORT_STATUS_LABEL: Record<ReportStatusFilter, string> = {
  PENDING: '처리 대기',
  RESOLVED: '처리 완료',
  REJECTED: '반려',
  ALL: '전체',
}

export const MEMBER_STATUS_LABEL: Record<MemberStatus, string> = {
  ALL: '전체',
  ACTIVE: '활동 중',
  SUSPENDED: '정지',
  WITHDRAWN: '탈퇴',
}
