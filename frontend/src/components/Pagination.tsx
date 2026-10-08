/** 한 번에 보이는 페이지 번호 수 (BR-09) */
export const PAGE_WINDOW = 5

/**
 * 보일 페이지 번호: 최대 5개, 되도록 현재 페이지를 가운데에 둔다. 처음·끝 근처에서는 창을 안쪽으로 민다 (BR-09)
 */
export function pageWindow(page: number, totalPages: number, size = PAGE_WINDOW): number[] {
  if (totalPages <= 0) return []
  const current = Math.min(Math.max(1, page), totalPages)
  const count = Math.min(size, totalPages)
  let start = current - Math.floor(count / 2)
  start = Math.max(1, Math.min(start, totalPages - count + 1))
  return Array.from({ length: count }, (_, i) => start + i)
}

/** 현재 페이지를 강조하고, 처음·끝에서는 이전·다음을 막는다 (CF-10-2, 3, BR-09) */
export default function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  const pages = pageWindow(page, totalPages)
  return (
    <nav className="pagination" aria-label="페이지">
      <button disabled={page <= 1} onClick={() => onChange(page - 1)} aria-label="이전 페이지">
        이전
      </button>
      {pages.map((p) => (
        <button
          key={p}
          className={p === page ? 'current' : ''}
          aria-current={p === page ? 'page' : undefined}
          aria-label={`${p}페이지`}
          onClick={() => onChange(p)}
        >
          {p}
        </button>
      ))}
      <button disabled={page >= totalPages} onClick={() => onChange(page + 1)} aria-label="다음 페이지">
        다음
      </button>
    </nav>
  )
}
