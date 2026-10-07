import { Route, Routes } from 'react-router-dom'
import Header from './components/Header'
import LoginModal from './components/LoginModal'
import BlogPage from './pages/BlogPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import PostDetailPage from './pages/PostDetailPage'
import PostEditorPage from './pages/PostEditorPage'
import SearchPage from './pages/SearchPage'
import SignupPage from './pages/SignupPage'

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
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <LoginModal />
    </>
  )
}
