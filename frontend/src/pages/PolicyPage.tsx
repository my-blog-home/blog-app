import { Link } from 'react-router-dom'
import MarkdownView from '../components/MarkdownView'
import { fillPlaceholders } from '../legal/config'
import operation from '../legal/operation.md?raw'
import privacy from '../legal/privacy.md?raw'
import terms from '../legal/terms.md?raw'
import { useDocumentMeta } from '../meta'

export const POLICIES = {
  privacy: { title: '개인정보처리방침', text: privacy },
  terms: { title: '이용약관', text: terms },
  operation: { title: '운영정책', text: operation },
} as const

export type PolicyKey = keyof typeof POLICIES

/** 개인정보처리방침·이용약관·운영정책. 로그인하지 않아도 볼 수 있다 */
export default function PolicyPage({ kind }: Readonly<{ kind: PolicyKey }>) {
  const policy = POLICIES[kind]
  useDocumentMeta(policy.title)
  return (
    <article className="reading policy">
      <nav className="policy-tabs" aria-label="정책 문서">
        {(Object.keys(POLICIES) as PolicyKey[]).map((key) => (
          <Link key={key} to={`/policies/${key}`} className={key === kind ? 'current' : ''} aria-current={key === kind ? 'page' : undefined}>
            {POLICIES[key].title}
          </Link>
        ))}
      </nav>
      <MarkdownView source={fillPlaceholders(policy.text)} />
    </article>
  )
}
