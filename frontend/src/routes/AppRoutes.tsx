import { FileQuestion } from 'lucide-react';
import { lazy, Suspense } from 'react';
import { Link, Route, Routes } from 'react-router-dom';
import { EmptyState } from '@/components/common';
import { Button } from '@/components/ui/button';
import { AppLayout } from '@/layouts/AppLayout';
import { LandingPage } from '@/pages/LandingPage';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { ProtectedRoute, PublicOnlyRoute } from './guards';

// Public pages stay in the entry bundle; the application screens load on demand.
const AdminPage = lazy(() => import('@/pages/AdminPage').then((m) => ({ default: m.AdminPage })));
const ChatPage = lazy(() => import('@/pages/ChatPage').then((m) => ({ default: m.ChatPage })));
const CourseDetailPage = lazy(() => import('@/pages/CourseDetailPage').then((m) => ({ default: m.CourseDetailPage })));
const CoursesPage = lazy(() => import('@/pages/CoursesPage').then((m) => ({ default: m.CoursesPage })));
const DashboardPage = lazy(() => import('@/pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const DocumentsPage = lazy(() => import('@/pages/DocumentsPage').then((m) => ({ default: m.DocumentsPage })));
const FolderDetailPage = lazy(() => import('@/pages/FolderDetailPage').then((m) => ({ default: m.FolderDetailPage })));
const FoldersPage = lazy(() => import('@/pages/FoldersPage').then((m) => ({ default: m.FoldersPage })));
const QuizCenterPage = lazy(() => import('@/pages/QuizCenterPage').then((m) => ({ default: m.QuizCenterPage })));
const QuizPage = lazy(() => import('@/pages/QuizPage').then((m) => ({ default: m.QuizPage })));
const SettingsPage = lazy(() => import('@/pages/SettingsPage').then((m) => ({ default: m.SettingsPage })));

function NotFound() {
  return (
    <div className="grid min-h-dvh place-items-center p-6">
      <EmptyState
        icon={FileQuestion}
        title="Page not found"
        description="That page does not exist or has moved."
        action={<Button asChild><Link to="/dashboard">Go to dashboard</Link></Button>}
      />
    </div>
  );
}

export function AppRoutes() {
  return (
    <Suspense fallback={null}>
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/courses" element={<CoursesPage />} />
          <Route path="/courses/:courseId" element={<CourseDetailPage />} />
          <Route path="/documents" element={<DocumentsPage />} />
          <Route path="/chat/:chatId?" element={<ChatPage />} />
          <Route path="/quizzes" element={<QuizCenterPage />} />
          <Route path="/quizzes/:quizId" element={<QuizPage />} />
          <Route path="/folders" element={<FoldersPage />} />
          <Route path="/folders/:folderId" element={<FolderDetailPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route element={<ProtectedRoute roles={['ADMIN']} />}>
            <Route path="/admin" element={<AdminPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
    </Suspense>
  );
}
