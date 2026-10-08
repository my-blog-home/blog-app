import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

// 지금 보고 있는 블로그. 블로그 화면과 글 상세가 알려 주고, 햄버거 메뉴가 그 블로그의 분류를 보여 준다 (FR-068)
interface CurrentBlogState {
  blogId: number | null
  setBlogId: (id: number | null) => void
}

const CurrentBlogContext = createContext<CurrentBlogState>({ blogId: null, setBlogId: () => {} })

export function CurrentBlogProvider({ children }: { children: ReactNode }) {
  const [blogId, setBlogId] = useState<number | null>(null)
  return <CurrentBlogContext.Provider value={{ blogId, setBlogId }}>{children}</CurrentBlogContext.Provider>
}

export function useCurrentBlog(): number | null {
  return useContext(CurrentBlogContext).blogId
}

/** 화면이 열려 있는 동안 지금 보는 블로그를 알린다. 화면을 떠나면 지운다 */
export function useMarkCurrentBlog(blogId: number | null | undefined) {
  const { setBlogId } = useContext(CurrentBlogContext)
  useEffect(() => {
    setBlogId(blogId ?? null)
    return () => setBlogId(null)
  }, [blogId, setBlogId])
}
