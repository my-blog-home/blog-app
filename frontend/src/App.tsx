import { Route, Routes } from 'react-router-dom'
import Header from './components/Header'
import LoginModal from './components/LoginModal'
import { CurrentBlogProvider } from './layout/CurrentBlog'
import { PreviewBanner, usePreview, VisitorPreviewScope } from './layout/VisitorPreview'
import AdminLayout from './pages/admin/AdminLayout'
import AdminMembersPage from './pages/admin/AdminMembersPage'
import AdminNoticesPage from './pages/admin/AdminNoticesPage'
import AdminReportsPage from './pages/admin/AdminReportsPage'
import AdminSummaryPage from './pages/admin/AdminSummaryPage'
import ActivityPage from './pages/ActivityPage'
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
import NoticeDetailPage from './pages/NoticeDetailPage'
import NoticesPage from './pages/NoticesPage'
import UserProfilePage from './pages/UserProfilePage'
import DashboardPage from './pages/manage/DashboardPage'
import ManageCategoriesPage from './pages/manage/ManageCategoriesPage'
import ManageCommentsPage from './pages/manage/ManageCommentsPage'
import ManageLayout from './pages/manage/ManageLayout'
import ManagePostsPage from './pages/manage/ManagePostsPage'
import ManageSettingsPage from './pages/manage/ManageSettingsPage'
import StatsPage from './pages/manage/StatsPage'

export default function App() {
  // 방문자 화면 미리보기를 켜고 끌 때 화면을 새로 읽는다 (FR-083)
  const preview = usePreview()
  return (
    <CurrentBlogProvider>
      <VisitorPreviewScope>
      <Header />
      <PreviewBanner />
      <main className="container" key={preview ? 'visitor' : 'me'}>
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
          <Route path="/me/activity" element={<ActivityPage />} />
          <Route path="/tags/:name" element={<TagPage />} />
          <Route path="/users/:userId" element={<UserProfilePage />} />
          <Route path="/notices" element={<NoticesPage />} />
          <Route path="/notices/:noticeId" element={<NoticeDetailPage />} />
          <Route path="/manage" element={<ManageLayout />}>
            <Route index element={<DashboardPage />} />
            <Route path="posts" element={<ManagePostsPage />} />
            <Route path="categories" element={<ManageCategoriesPage />} />
            <Route path="comments" element={<ManageCommentsPage />} />
            <Route path="stats" element={<StatsPage />} />
            <Route path="settings" element={<ManageSettingsPage />} />
          </Route>
          {/* 관리자 화면 (FR-078~082) */}
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<AdminSummaryPage />} />
            <Route path="reports" element={<AdminReportsPage />} />
            <Route path="members" element={<AdminMembersPage />} />
            <Route path="notices" element={<AdminNoticesPage />} />
          </Route>
          <Route path="/password-reset" element={<PasswordResetPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <footer className="footer">
        <div>Ylog · 내 블로그</div>
      </footer>
      <LoginModal />
      </VisitorPreviewScope>
    </CurrentBlogProvider>
  )
}
