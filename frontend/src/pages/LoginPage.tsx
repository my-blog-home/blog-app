import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import LoginForm from '../components/LoginForm'

/** 로그인 전에 보던 화면으로 돌아간다. 직접 왔다면 첫 화면으로 (CF-02-2) */
export default function LoginPage() {
  const { me } = useAuth()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  const redirect = params.get('redirect')
  const target = redirect && redirect.startsWith('/') && !redirect.startsWith('//') && redirect !== '/login' ? redirect : '/'
  if (me) return <Navigate to={target} replace />
  return (
    <section className="narrow">
      <h1>로그인</h1>
      <p className="muted" style={{ textAlign: 'center', marginTop: -12 }}>다시 오신 걸 환영합니다</p>
      {notice && <p className="notice">{notice}</p>}
      <LoginForm onSuccess={() => navigate(target, { replace: true })} />
    </section>
  )
}
