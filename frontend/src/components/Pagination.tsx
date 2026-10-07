/** 현재 페이지를 강조하고, 처음·끝에서는 이전·다음을 막는다 (CF-10-2, 3) */
export default function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  const pages = Array.from({ length: totalPages }, (_, i) => i + 1)
  return (
    <nav className="pagination" aria-label="페이지">
      <button disabled={page <= 1} onClick={() => onChange(page - 1)}>
        이전
      </button>
      {pages.map((p) => (
        <button key={p} className={p === page ? 'current' : ''} aria-current={p === page ? 'page' : undefined} onClick={() => onChange(p)}>
          {p}
        </button>
      ))}
      <button disabled={page >= totalPages} onClick={() => onChange(page + 1)}>
        다음
      </button>
    </nav>
  )
}
