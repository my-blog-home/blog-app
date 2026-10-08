import { useState, type KeyboardEvent } from 'react'

/** 태그 입력: Enter나 쉼표로 추가, 글마다 5개까지 (CF-20) */
export default function TagInput({ tags, onChange }: { tags: string[]; onChange: (tags: string[]) => void }) {
  const [input, setInput] = useState('')
  const [error, setError] = useState<string | null>(null)

  const add = () => {
    const tag = input.trim().replace(/^#+/, '')
    setInput('')
    if (!tag) return
    if ([...tag].length > 15) {
      setError('태그는 공백과 쉼표 없이 1~15자로 입력해 주세요')
      return
    }
    if (tags.some((t) => t.toLowerCase() === tag.toLowerCase())) return
    if (tags.length >= 5) {
      setError('태그는 글마다 5개까지 붙일 수 있습니다')
      return
    }
    setError(null)
    onChange([...tags, tag])
  }

  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.nativeEvent.isComposing) return
    if (e.key === 'Enter' || e.key === ',' || e.key === ' ') {
      e.preventDefault()
      add()
    }
  }

  return (
    <div className="tag-input">
      <div className="tags">
        {tags.map((t) => (
          <span key={t} className="tag">
            #{t}
            <button type="button" className="link" aria-label={`${t} 태그 지우기`} onClick={() => onChange(tags.filter((x) => x !== t))}>
              ✕
            </button>
          </span>
        ))}
        <input value={input} onChange={(e) => setInput(e.target.value)} onKeyDown={onKey} onBlur={add} placeholder={tags.length < 5 ? '태그 입력 후 Enter' : ''} disabled={tags.length >= 5} aria-label="태그" />
      </div>
      {error && <span className="hint error">{error}</span>}
    </div>
  )
}
