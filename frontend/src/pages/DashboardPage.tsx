import { useQuery } from '@tanstack/react-query';
import { BookOpen, FileText, GraduationCap, Layers, MessageSquare, MessagesSquare, Plus, Upload, type LucideIcon } from 'lucide-react';
import { lazy, Suspense, useState } from 'react';
import { Link } from 'react-router-dom';
import { EmptyState, ErrorState, ListSkeleton, PageHeader, StatCard } from '@/components/common';
import { CourseDialog } from '@/components/courses/CourseDialog';
import { StatusBadge } from '@/components/documents/DocumentList';
import { UploadDialog } from '@/components/documents/UploadDialog';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle, Skeleton } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Select } from '@/components/ui/select';
import { useAuth } from '@/contexts/AuthContext';
import { useChats, useCourses, useDocuments } from '@/hooks/queries';
import { errorMessage } from '@/services/api';
import { dashboardApi } from '@/services/endpoints';
import type { ActivityItem } from '@/types';
import { timeAgo } from '@/utils/format';
import { CourseCard } from '@/pages/CoursesPage';

const QuestionsChart = lazy(() => import('@/components/dashboard/QuestionsChart').then((module) => ({ default: module.QuestionsChart })));

const ACTIVITY: Record<ActivityItem['type'], { icon: LucideIcon; verb: string }> = {
  DOCUMENT: { icon: FileText, verb: 'Uploaded' },
  CHAT: { icon: MessageSquare, verb: 'Chat' },
  QUIZ: { icon: GraduationCap, verb: 'Quiz' },
  COURSE: { icon: BookOpen, verb: 'Created course' },
};

const activityLink = (item: ActivityItem) =>
  item.type === 'CHAT' ? `/chat/${item.id}` : item.type === 'QUIZ' ? `/quizzes/${item.id}` : item.type === 'COURSE' ? `/courses/${item.id}` : item.parentId ? `/courses/${item.parentId}` : '/documents';

export function DashboardPage() {
  const { user } = useAuth();
  const stats = useQuery({ queryKey: ['dashboard', 'stats'], queryFn: dashboardApi.stats });
  const activity = useQuery({ queryKey: ['dashboard', 'activity'], queryFn: dashboardApi.activity });
  const courses = useCourses();
  const documents = useDocuments();
  const chats = useChats();
  const [creating, setCreating] = useState(false);
  const [picking, setPicking] = useState(false);
  const [uploadCourse, setUploadCourse] = useState<string>();

  const firstName = user?.name.split(' ')[0];
  const courseList = courses.data?.content ?? [];

  return (
    <>
      <PageHeader
        title={`Welcome back, ${firstName}`}
        description="Here is where your studying stands."
        actions={
          <>
            <Button variant="outline" onClick={() => setCreating(true)}>
              <Plus /> Create course
            </Button>
            <Button onClick={() => setPicking(true)} disabled={courseList.length === 0}>
              <Upload /> Quick upload
            </Button>
          </>
        }
      />

      {stats.isError && <ErrorState message={errorMessage(stats.error)} onRetry={() => stats.refetch()} />}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        {stats.isLoading &&
          Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-[88px]" />)}
        {stats.data && (
          <>
            <StatCard icon={FileText} label="Documents" value={stats.data.documents} />
            <StatCard icon={Layers} label="Pages processed" value={stats.data.pages} />
            <StatCard icon={MessageSquare} label="Questions asked" value={stats.data.questions} />
            <StatCard icon={MessagesSquare} label="Chats" value={stats.data.chats} />
            <StatCard icon={GraduationCap} label="Quizzes" value={stats.data.quizzes} />
          </>
        )}
      </div>

      {courses.isSuccess && courseList.length === 0 && (
        <EmptyState
          className="mt-6"
          icon={BookOpen}
          title="Start by creating a course"
          description="Courses keep your lecture notes and conversations together."
          action={<Button onClick={() => setCreating(true)}><Plus /> Create course</Button>}
        />
      )}

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0 space-y-6">
          {stats.data && stats.data.questions > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>Questions asked, last 14 days</CardTitle>
              </CardHeader>
              <CardContent className="pt-3">
                <Suspense fallback={<Skeleton className="h-44" />}>
                  <QuestionsChart data={stats.data.questionsPerDay} />
                </Suspense>
              </CardContent>
            </Card>
          )}

          {courseList.length > 0 && (
            <section>
              <div className="mb-3 flex items-center justify-between">
                <h2 className="text-sm font-semibold">Recent courses</h2>
                <Link to="/courses" className="text-[13px] text-primary hover:underline">View all</Link>
              </div>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                {courseList.slice(0, 4).map((course) => (
                  <CourseCard key={course.id} course={course} />
                ))}
              </div>
            </section>
          )}

          {!!documents.data?.content.length && (
            <Card>
              <CardHeader>
                <CardTitle>Recently uploaded</CardTitle>
                <Link to="/documents" className="text-[13px] text-primary hover:underline">View all</Link>
              </CardHeader>
              <CardContent className="pt-2">
                <ul className="divide-y">
                  {documents.data.content.slice(0, 5).map((document) => (
                    <li key={document.id} className="flex items-center justify-between gap-3 py-2.5">
                      <div className="min-w-0">
                        <p className="truncate text-sm">{document.filename}</p>
                        <p className="text-xs text-muted-foreground">{timeAgo(document.createdAt)}</p>
                      </div>
                      <StatusBadge status={document.status} />
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          )}
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Recent chats</CardTitle>
              <Link to="/chat" className="text-[13px] text-primary hover:underline">New chat</Link>
            </CardHeader>
            <CardContent className="pt-2">
              {chats.isLoading && <ListSkeleton rows={2} />}
              {chats.data?.content.length === 0 && <p className="py-2 text-sm text-muted-foreground">Your conversations will appear here.</p>}
              <ul>
                {chats.data?.content.slice(0, 5).map((chat) => (
                  <li key={chat.id}>
                    <Link to={`/chat/${chat.id}`} className="-mx-2 block rounded-md px-2 py-1.5 hover:bg-accent">
                      <span className="block truncate text-sm">{chat.title}</span>
                      <span className="text-xs text-muted-foreground">{timeAgo(chat.updatedAt)}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Recent activity</CardTitle>
            </CardHeader>
            <CardContent className="pt-2">
              {activity.data?.length === 0 && <p className="py-2 text-sm text-muted-foreground">Nothing yet.</p>}
              <ul className="space-y-1">
                {activity.data?.slice(0, 7).map((item) => {
                  const { icon: Icon, verb } = ACTIVITY[item.type];
                  return (
                    <li key={`${item.type}-${item.id}`}>
                      <Link to={activityLink(item)} className="-mx-2 flex items-center gap-2.5 rounded-md px-2 py-1.5 hover:bg-accent">
                        <Icon className="size-4 shrink-0 text-muted-foreground" />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm">{verb}: {item.title}</span>
                          <span className="text-xs text-muted-foreground">{timeAgo(item.at)}</span>
                        </span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </CardContent>
          </Card>
        </div>
      </div>

      <CourseDialog open={creating} onOpenChange={setCreating} />
      <Dialog open={picking} onOpenChange={setPicking}>
        <DialogContent title="Upload to which course?">
          <Select
            ariaLabel="Course"
            placeholder="Choose a course"
            options={courseList.map((course) => ({ value: course.id, label: course.name }))}
            onChange={(id) => {
              setPicking(false);
              setUploadCourse(id);
            }}
          />
        </DialogContent>
      </Dialog>
      <UploadDialog open={!!uploadCourse} onOpenChange={(open) => !open && setUploadCourse(undefined)} scope={{ courseId: uploadCourse }} />
    </>
  );
}
