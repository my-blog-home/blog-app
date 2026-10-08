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
import DashboardPage from './pages/manage/DashboardPage'
import ManageCategoriesPage from './pages/manage/ManageCategoriesPage'
import ManageCommentsPage from './pages/manage/ManageCommentsPage'
import ManageLayout from './pages/manage/ManageLayout'
import ManagePostsPage from './pages/manage/ManagePostsPage'
import ManageSettingsPage from './pages/manage/ManageSettingsPage'
import StatsPage from './pages/manage/StatsPage'

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
          <Route path="/manage" element={<ManageLayout />}>
            <Route index element={<DashboardPage />} />
            <Route path="posts" element={<ManagePostsPage />} />
            <Route path="categories" element={<ManageCategoriesPage />} />
            <Route path="comments" element={<ManageCommentsPage />} />
            <Route path="stats" element={<StatsPage />} />
            <Route path="settings" element={<ManageSettingsPage />} />
          </Route>
          <Route path="/password-reset" element={<PasswordResetPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <LoginModal />
    </>
  )
}
