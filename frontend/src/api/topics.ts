import { get } from './client'
import type { Topic } from './types'

// 주제 10개는 고정이므로 한 번 읽어 두고 함께 쓴다. 글 수는 처음 읽은 값이다
let cache: Promise<Topic[]> | null = null

export function loadTopics(refresh = false): Promise<Topic[]> {
  if (!cache || refresh) {
    cache = get<Topic[]>('/api/topics').catch((e) => {
      cache = null
      throw e
    })
  }
  return cache
}
