/** 글 썸네일: 본문의 첫 이미지, 없으면 분류 이름을 담은 그라데이션 칸 */
export default function Thumb({ url, label }: { url: string | null; label: string }) {
  return (
    <figure className="thumb">
      {url ? <img src={url} alt="" loading="lazy" /> : <span className="thumb-placeholder">{label}</span>}
    </figure>
  )
}
