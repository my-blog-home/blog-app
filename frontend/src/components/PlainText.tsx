/** 글자만 있는 내용을 빈 줄로 문단을 나누고 줄바꿈은 그대로 보여 준다. HTML로 해석하지 않는다 (FR-077) */
export function paragraphs(text: string): string[][] {
  return text
    .trim()
    .split(/\n\s*\n/)
    .map((para) => para.split('\n').map((line) => line.trimEnd()))
    .filter((lines) => lines.some((line) => line.trim() !== ''))
}

export default function PlainText({ text }: { text: string }) {
  return (
    <div className="plain-text">
      {paragraphs(text).map((lines, i) => (
        <p key={i}>
          {lines.map((line, j) => (
            <span key={j}>
              {j > 0 && <br />}
              {line}
            </span>
          ))}
        </p>
      ))}
    </div>
  )
}
