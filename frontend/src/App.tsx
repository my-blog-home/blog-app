import { Route, Routes } from 'react-router-dom'
import Header from './components/Header'
import LoginModal from './components/LoginModal'
import BlogPage from './pages/BlogPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import MyPage from './pages/MyPage'
import PasswordResetPage from './pages/PasswordResetPage'
import NotFoundPage from './pages/NotFoundPage'
import PostDetailPage from './pages/PostDetailPage'
import PostEditorPage from './pages/PostEditorPage'
import SearchPage from './pages/SearchPage'
import SignupPage from './pages/SignupPage'
import TagPage from './pages/TagPage'

export default function App() {
  return (
    <>
      <Header />
      <main className="container">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/blogs/:blogId" element={<BlogPage />} />
          <Route path="/posts/:postId" element={<PostDetailPage />} />
          <Route path="/posts/:postId/edit" element={<PostEditorPage />} />
          <Route path="/write" element={<PostEditorPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/me" element={<MyPage />} />
          <Route path="/tags/:name" element={<TagPage />} />
          <Route path="/password-reset" element={<PasswordResetPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <LoginModal />
    </>
  )
}
