import { useRef, useState } from 'react'
import { ApiError } from '../api/client'

const IMAGE_RULE = '이미지는 5MB 이하의 jpg, png, gif, webp만 올릴 수 있습니다'

async function upload(file: File): Promise<string> {
  const token = decodeURIComponent(document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.split('=')[1] ?? '')
  const form = new FormData()
  form.append('file', file)
  const response = await fetch('/api/images', { method: 'POST', body: form, headers: { 'X-XSRF-TOKEN': token }, credentials: 'same-origin' })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) throw new ApiError(response.status, data)
  return data.url
}

/** 이미지를 올리고 본문에 마크다운 이미지로 넣는다 (CF-22) */
export default function ImageUploadButton({ onUploaded }: { onUploaded: (markdown: string) => void }) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const pick = async (files: FileList | null) => {
    const file = files?.[0]
    if (input.current) input.current.value = ''
    if (!file) return
    if (file.size > 5 * 1024 * 1024 || !/^image\/(jpeg|png|gif|webp)$/.test(file.type)) {
      setError(IMAGE_RULE)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const url = await upload(file)
      onUploaded(`![${file.name.replace(/[[\]]/g, '')}](${url})`)
    } catch (e) {
      setError(e instanceof ApiError ? e.fieldErrors[0]?.message ?? e.message : IMAGE_RULE)
    } finally {
      setBusy(false)
    }
  }

  return (
    <span className="image-upload">
      <button type="button" onClick={() => input.current?.click()} disabled={busy}>
        {busy ? '올리는 중…' : '이미지'}
      </button>
      <input ref={input} type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden onChange={(e) => pick(e.target.files)} />
      {error && <span className="hint error">{error}</span>}
    </span>
  )
}
