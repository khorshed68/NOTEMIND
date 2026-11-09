import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { FieldError, Input, Label, Textarea } from '@/components/ui/input';
import { errorMessage } from '@/services/api';
import { courseApi } from '@/services/endpoints';
import type { Course } from '@/types';

const schema = z.object({
  name: z.string().trim().min(1, 'Course name is required').max(160),
  courseCode: z.string().trim().max(40).optional(),
  description: z.string().trim().max(2000).optional(),
});

type FormValues = z.infer<typeof schema>;

export function CourseDialog({
  open,
  onOpenChange,
  course,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  course?: Course;
}) {
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string>();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (open) {
      setFormError(undefined);
      reset({ name: course?.name ?? '', courseCode: course?.courseCode ?? '', description: course?.description ?? '' });
    }
  }, [open, course, reset]);

  const onSubmit = async (values: FormValues) => {
    try {
      if (course) await courseApi.update(course.id, values);
      else await courseApi.create(values);
      queryClient.invalidateQueries({ queryKey: ['courses'] });
      queryClient.invalidateQueries({ queryKey: ['course'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      toast.success(course ? 'Course updated' : 'Course created');
      onOpenChange(false);
    } catch (error) {
      setFormError(errorMessage(error));
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title={course ? 'Edit course' : 'Create a course'} description="Each course keeps its own documents and chats.">
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="course-name">Course name</Label>
            <Input id="course-name" placeholder="Compiler Design" aria-invalid={!!errors.name} {...register('name')} />
            <FieldError message={errors.name?.message} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="course-code">Course code</Label>
            <Input id="course-code" placeholder="CSE 3101" {...register('courseCode')} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="course-description">Description</Label>
            <Textarea id="course-description" placeholder="Optional" {...register('description')} />
          </div>
          <FieldError message={formError} />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={isSubmitting}>
              {course ? 'Save changes' : 'Create course'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
