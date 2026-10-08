import { useNavigate } from 'react-router-dom'

/** "< 뒤로": 앱 안에서 넘어왔으면 이전 화면으로, 주소로 바로 들어왔으면 fallback으로 간다 */
export default function BackButton({ fallback }: { fallback: string }) {
  const navigate = useNavigate()
  const back = () => {
    const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0
    if (idx > 0) navigate(-1)
    else navigate(fallback)
  }
  return (
    <button type="button" className="back-btn" onClick={back}>
      <span aria-hidden="true">‹</span> 뒤로
    </button>
  )
}
