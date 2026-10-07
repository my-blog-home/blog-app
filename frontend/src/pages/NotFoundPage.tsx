import { Link } from 'react-router-dom'

export default function NotFoundPage({ message = '찾을 수 없습니다' }: { message?: string }) {
  return (
    <div className="empty">
      <p>{message}</p>
      <Link to="/">첫 화면으로</Link>
    </div>
  )
}
