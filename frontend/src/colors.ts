// 분류 구분 색. 서버가 정해 주는 colorIndex 순서대로 쓴다 (BM-04-3)
export const CATEGORY_COLORS = ['#94a3b8', '#6c74e0', '#4fb3a5', '#f29e4c', '#e57aa5', '#5aa0e0', '#b38be6', '#e0b84a']

export const categoryColor = (index: number) => CATEGORY_COLORS[index % CATEGORY_COLORS.length]

// 프로필 색 6가지. 서버와 같은 순서, 첫 번째가 기본값 (FR-05)
export const PROFILE_COLORS = ['#c9dcfb', '#e2d8f8', '#cdeee4', '#f8d6c6', '#f5e3ad', '#f9dbe8']
