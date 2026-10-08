// 분류 구분 색. 서버가 정해 주는 colorIndex 순서대로 쓴다 (BM-04-3)
export const CATEGORY_COLORS = ['#94a3b8', '#6c74e0', '#4fb3a5', '#f29e4c', '#e57aa5', '#5aa0e0', '#b38be6', '#e0b84a']

export const categoryColor = (index: number) => CATEGORY_COLORS[index % CATEGORY_COLORS.length]
