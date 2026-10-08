import type { NoticeItem } from '../api/types'

export const NOTICE_TYPE_LABEL = { NOTICE: '공지', GUIDE: '이용법' } as const

/** 공지 종류 표시. 릴리스 노트는 공지 안에서 "업데이트"로 구분한다 */
export default function NoticeBadge({ notice }: { notice: Pick<NoticeItem, 'type' | 'releaseVersion'> }) {
  if (notice.releaseVersion) return <span className="badge release-badge">업데이트</span>
  return <span className={notice.type === 'NOTICE' ? 'badge notice-badge' : 'badge guide-badge'}>{NOTICE_TYPE_LABEL[notice.type]}</span>
}
