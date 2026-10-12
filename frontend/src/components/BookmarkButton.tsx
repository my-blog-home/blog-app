import { useState } from 'react'
import { ApiError, put } from '../api/client'
import { useAuth } from '../auth/AuthContext'

/** 북마크: 나중에 읽을 글로 저장한다. 숫자는 보이지 않고 나만 본다 */
export default function BookmarkButton({ postId, initial }: { postId: number; initial: boolean }) {
  const { requireLogin } = useAuth()
  const [saved, setSaved] = useState(initial)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const toggle = async () => {
    if (busy || !requireLogin()) return
    setBusy(true)
    try {
      const res = await put<{ bookmarked: boolean }>(`/api/posts/${postId}/bookmark`)
      setSaved(res.bookmarked)
      setMessage(res.bookmarked ? '북마크에 저장했어요' : '북마크에서 뺐어요')
      setTimeout(() => setMessage(null), 2000)
    } catch (e) {
      if (e instanceof ApiError) setMessage(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <button className={`like ${saved ? 'on bookmark-on' : ''}`} onClick={toggle} disabled={busy} aria-pressed={saved}>
        <span aria-hidden="true">{saved ? '★' : '☆'}</span> {saved ? '북마크됨' : '북마크'}
      </button>
      {message && (
        <span className="muted small" role="status">
          {message}
        </span>
      )}
    </>
  )
}
