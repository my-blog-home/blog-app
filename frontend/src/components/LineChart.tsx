import { useId, useMemo, useState } from 'react'

export interface Series {
  name: string
  color: string
  values: number[]
}

/**
 * 일별 선 그래프. 2px 선, 한 축, 마우스를 올리면 그날의 숫자를 보여 준다 (BM-02-2, BM-06-2).
 * 색은 dataviz 기준 팔레트의 범주 1·2번 (파랑, 주황), 글자는 글자 색으로만 쓴다.
 */
export default function LineChart({ labels, series, height = 200 }: { labels: string[]; series: Series[]; height?: number }) {
  const [hover, setHover] = useState<number | null>(null)
  const [showTable, setShowTable] = useState(false)
  const titleId = useId()
  const width = 640
  const pad = { top: 12, right: 12, bottom: 24, left: 36 }
  const max = Math.max(1, ...series.flatMap((s) => s.values))
  const niceMax = useMemo(() => {
    const step = Math.pow(10, Math.floor(Math.log10(max)))
    return Math.ceil(max / step) * step
  }, [max])
  const innerW = width - pad.left - pad.right
  const innerH = height - pad.top - pad.bottom
  const x = (i: number) => pad.left + (labels.length <= 1 ? innerW / 2 : (i * innerW) / (labels.length - 1))
  const y = (v: number) => pad.top + innerH - (v / niceMax) * innerH
  const ticks = [0, niceMax / 2, niceMax]
  const labelEvery = Math.ceil(labels.length / 6)

  const onMove = (e: React.PointerEvent<SVGRectElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const ratio = (e.clientX - rect.left) / rect.width
    setHover(Math.max(0, Math.min(labels.length - 1, Math.round(ratio * (labels.length - 1)))))
  }

  return (
    <figure className="chart">
      {series.length > 1 && (
        <div className="legend">
          {series.map((s) => (
            <span key={s.name}>
              <i style={{ background: s.color }} /> {s.name}
            </span>
          ))}
        </div>
      )}
      <div className="chart-plot">
        <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-labelledby={titleId}>
          <title id={titleId}>{series.map((s) => s.name).join(', ')} 일별 그래프</title>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} className="grid" />
              <text x={pad.left - 6} y={y(t)} className="axis" textAnchor="end" dominantBaseline="middle">
                {t}
              </text>
            </g>
          ))}
          {labels.map((l, i) =>
            i % labelEvery === 0 || i === labels.length - 1 ? (
              <text key={l} x={x(i)} y={height - 6} className="axis" textAnchor="middle">
                {l}
              </text>
            ) : null,
          )}
          {hover !== null && <line x1={x(hover)} x2={x(hover)} y1={pad.top} y2={pad.top + innerH} className="crosshair" />}
          {series.map((s) => (
            <g key={s.name}>
              <polyline fill="none" stroke={s.color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" points={s.values.map((v, i) => `${x(i)},${y(v)}`).join(' ')} />
              {hover !== null && <circle cx={x(hover)} cy={y(s.values[hover])} r={4} fill={s.color} stroke="var(--bg)" strokeWidth={2} />}
            </g>
          ))}
          <rect x={pad.left} y={pad.top} width={innerW} height={innerH} fill="transparent" onPointerMove={onMove} onPointerLeave={() => setHover(null)} />
        </svg>
        {hover !== null && (
          <div className="tooltip" style={{ left: `${(x(hover) / width) * 100}%` }}>
            <strong>{labels[hover]}</strong>
            {series.map((s) => (
              <span key={s.name}>
                <i style={{ background: s.color }} /> {s.name} {s.values[hover].toLocaleString()}
              </span>
            ))}
          </div>
        )}
      </div>
      <button className="link small" onClick={() => setShowTable(!showTable)}>
        {showTable ? '표 닫기' : '표로 보기'}
      </button>
      {showTable && (
        <table className="data-table">
          <thead>
            <tr>
              <th>날짜</th>
              {series.map((s) => (
                <th key={s.name}>{s.name}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {labels.map((l, i) => (
              <tr key={l}>
                <td>{l}</td>
                {series.map((s) => (
                  <td key={s.name}>{s.values[i]}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </figure>
  )
}

export const SERIES_COLORS = ['#2a78d6', '#eb6834']
