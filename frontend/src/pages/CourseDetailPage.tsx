import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, FileText, Layers, MessageSquare, Sparkles, Upload } from 'lucide-react';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { EmptyState, ErrorState, ListSkeleton, PageHeader, StatCard } from '@/components/common';
import { DocumentList } from '@/components/documents/DocumentList';
import { UploadDialog } from '@/components/documents/UploadDialog';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useChats, useDocuments } from '@/hooks/queries';
import { errorMessage } from '@/services/api';
import { courseApi } from '@/services/endpoints';
import { timeAgo } from '@/utils/format';

export function CourseDetailPage() {
  const { courseId = '' } = useParams();
  const [uploading, setUploading] = useState(false);
  const course = useQuery({ queryKey: ['course', courseId], queryFn: () => courseApi.get(courseId) });
  const documents = useDocuments({ courseId });
  const chats = useChats({ courseId });

  if (course.isError) return <ErrorState message={errorMessage(course.error)} onRetry={() => course.refetch()} />;
  if (course.isLoading || !course.data) return <ListSkeleton />;

  const pages = documents.data?.content.reduce((sum, document) => sum + (document.pageCount ?? 0), 0) ?? course.data.pageCount;
  const count = documents.data?.totalElements ?? course.data.documentCount;

  return (
    <>
      <Link to="/courses" className="mb-3 inline-flex items-center gap-1 text-[13px] text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-3.5" /> My Courses
      </Link>
      <PageHeader
        title={course.data.name}
        description={[course.data.courseCode, course.data.description].filter(Boolean).join(' · ') || undefined}
        actions={
          <>
            <Button variant="outline" onClick={() => setUploading(true)}>
              <Upload /> Upload document
            </Button>
            <Button asChild>
              <Link to={`/chat?course=${courseId}`}>
                <Sparkles /> Ask AI
              </Link>
            </Button>
          </>
        }
      />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard icon={FileText} label="Documents" value={count} />
        <StatCard icon={Layers} label="Pages" value={pages} />
        <StatCard icon={MessageSquare} label="Chats" value={chats.data?.totalElements ?? '–'} />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_300px]">
        <section aria-label="Documents">
          <h2 className="mb-3 text-sm font-semibold">Documents</h2>
          {documents.isLoading && <ListSkeleton rows={3} />}
          {documents.isError && <ErrorState message={errorMessage(documents.error)} onRetry={() => documents.refetch()} />}
          {documents.data?.content.length === 0 && (
            <EmptyState
              icon={Upload}
              title="No documents yet"
              description="Upload lecture slides or notes in PDF format to start asking questions."
              action={<Button onClick={() => setUploading(true)}><Upload /> Upload PDF</Button>}
            />
          )}
          {!!documents.data?.content.length && <DocumentList documents={documents.data.content} />}
        </section>
        <Card className="h-fit">
          <CardHeader>
            <CardTitle>Recent chats</CardTitle>
          </CardHeader>
          <CardContent className="pt-3">
            {chats.data?.content.length === 0 && <p className="text-sm text-muted-foreground">No conversations yet.</p>}
            <ul className="space-y-1">
              {chats.data?.content.slice(0, 6).map((chat) => (
                <li key={chat.id}>
                  <Link to={`/chat/${chat.id}`} className="block rounded-md px-2 py-1.5 hover:bg-accent">
                    <span className="block truncate text-sm">{chat.title}</span>
                    <span className="text-xs text-muted-foreground">{timeAgo(chat.updatedAt)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>
      <UploadDialog open={uploading} onOpenChange={setUploading} scope={{ courseId }} />
    </>
  );
}
