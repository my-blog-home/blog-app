import { useState } from 'react'
import { ApiError, put } from '../api/client'
import { useAuth } from '../auth/AuthContext'

/** 좋아요: 누르면 켜지고 다시 누르면 꺼진다. 자기 글에는 누를 수 없다 (CF-19) */
export default function LikeButton({ postId, initialCount, initialLiked, isMine }: { postId: number; initialCount: number; initialLiked: boolean; isMine: boolean }) {
  const { requireLogin } = useAuth()
  const [count, setCount] = useState(initialCount)
  const [liked, setLiked] = useState(initialLiked)
  const [busy, setBusy] = useState(false)

  const toggle = async () => {
    if (busy || !requireLogin()) return
    setBusy(true)
    try {
      const state = await put<{ liked: boolean; likeCount: number }>(`/api/posts/${postId}/like`)
      setLiked(state.liked)
      setCount(state.likeCount)
    } catch (e) {
      if (e instanceof ApiError) alert(e.fieldMessage('like') ?? e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <button className={`like ${liked ? 'on' : ''}`} onClick={toggle} disabled={isMine || busy} aria-pressed={liked} title={isMine ? '내 글에는 누를 수 없습니다' : undefined}>
      {liked ? '♥' : '♡'} 좋아요 {count}
    </button>
  )
}
