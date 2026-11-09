import { useQueryClient } from '@tanstack/react-query';
import { BookOpen, FileText, MoreHorizontal, Pencil, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { ConfirmDialog, EmptyState, ErrorState, ListSkeleton, PageHeader } from '@/components/common';
import { CourseDialog } from '@/components/courses/CourseDialog';
import { Button } from '@/components/ui/button';
import { Badge, Card } from '@/components/ui/card';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown';
import { useCourses } from '@/hooks/queries';
import { errorMessage } from '@/services/api';
import { courseApi } from '@/services/endpoints';
import type { Course } from '@/types';
import { plural } from '@/utils/format';

export function CourseCard({ course, onEdit, onDelete }: { course: Course; onEdit?: () => void; onDelete?: () => void }) {
  return (
    <Card className="group relative transition-shadow hover:shadow-pop">
      <Link to={`/courses/${course.id}`} className="block rounded-lg p-4">
        <div className="flex items-center gap-2 pr-8">
          <span className="grid size-9 shrink-0 place-items-center rounded-md bg-primary-soft text-primary">
            <BookOpen className="size-4" />
          </span>
          {course.courseCode && <Badge>{course.courseCode}</Badge>}
        </div>
        <h3 className="mt-3 truncate text-sm font-semibold">{course.name}</h3>
        <p className="mt-1 flex items-center gap-1.5 text-[13px] text-muted-foreground">
          <FileText className="size-3.5" />
          {plural(course.documentCount, 'document')} · {plural(course.pageCount, 'page')}
        </p>
      </Link>
      {onEdit && onDelete && (
        <div className="absolute right-2 top-2">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${course.name}`}>
                <MoreHorizontal />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuItem onSelect={onEdit}>
                <Pencil /> Edit
              </DropdownMenuItem>
              <DropdownMenuItem className="text-destructive" onSelect={onDelete}>
                <Trash2 /> Delete
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      )}
    </Card>
  );
}

export function CoursesPage() {
  const courses = useCourses();
  const queryClient = useQueryClient();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Course>();
  const [deleting, setDeleting] = useState<Course>();

  const remove = async (course: Course) => {
    try {
      await courseApi.remove(course.id);
      toast.success('Course deleted');
      ['courses', 'documents', 'chats', 'dashboard'].forEach((key) => queryClient.invalidateQueries({ queryKey: [key] }));
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  return (
    <>
      <PageHeader
        title="My Courses"
        description="Organise your notes and conversations by course."
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus /> New course
          </Button>
        }
      />
      {courses.isLoading && <ListSkeleton />}
      {courses.isError && <ErrorState message={errorMessage(courses.error)} onRetry={() => courses.refetch()} />}
      {courses.data?.content.length === 0 && (
        <EmptyState
          icon={BookOpen}
          title="Create your first course"
          description="Add a course such as Compiler Design, then upload its lecture notes."
          action={
            <Button onClick={() => setCreating(true)}>
              <Plus /> New course
            </Button>
          }
        />
      )}
      {!!courses.data?.content.length && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {courses.data.content.map((course) => (
            <CourseCard key={course.id} course={course} onEdit={() => setEditing(course)} onDelete={() => setDeleting(course)} />
          ))}
        </div>
      )}
      <CourseDialog open={creating} onOpenChange={setCreating} />
      <CourseDialog open={!!editing} onOpenChange={(open) => !open && setEditing(undefined)} course={editing} />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(undefined)}
        title="Delete this course?"
        description={`"${deleting?.name}" and all of its documents and chats will be permanently removed.`}
        onConfirm={() => (deleting ? remove(deleting) : undefined)}
      />
    </>
  );
}
