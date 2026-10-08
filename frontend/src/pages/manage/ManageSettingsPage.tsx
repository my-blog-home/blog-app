import { useState, type FormEvent } from 'react'
import { ApiError, patch } from '../../api/client'
import { useManage } from './ManageLayout'

/** 블로그 설정: 이름 1~30자, 소개 0~200자 (BM-07, CF-04) */
export default function ManageSettingsPage() {
  const { blog, reloadBlog } = useManage()
  const [name, setName] = useState(blog.name)
  const [description, setDescription] = useState(blog.description ?? '')
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!name.trim()) {
      setMessage({ ok: false, text: '블로그 이름을 입력해 주세요' })
      return
    }
    try {
      await patch(`/api/blogs/${blog.id}`, { name, description })
      await reloadBlog()
      setMessage({ ok: true, text: '저장했습니다' })
    } catch (err) {
      setMessage({ ok: false, text: err instanceof ApiError ? err.fieldErrors[0]?.message ?? err.message : '잠시 뒤 다시 시도해 주세요' })
    }
  }

  return (
    <>
      <h1>설정</h1>
      <form className="form narrow-left" onSubmit={submit}>
        <label>
          블로그 이름
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={30} />
        </label>
        <label>
          소개
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={200} rows={4} />
          <span className="hint muted">{[...description].length}/200</span>
        </label>
        {message && <p className={message.ok ? 'notice' : 'error'}>{message.text}</p>}
        <button className="primary">저장</button>
      </form>
    </>
  )
}
