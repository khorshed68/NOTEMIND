import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { GraduationCap, Sparkles } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { EmptyState, ErrorState, ListSkeleton, PageHeader } from '@/components/common';
import { Button } from '@/components/ui/button';
import { Badge, Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { FieldError, Input, Label } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { useCourses, useDocuments } from '@/hooks/queries';
import { errorMessage } from '@/services/api';
import { quizApi } from '@/services/endpoints';
import type { Difficulty, QuestionType } from '@/types';
import { cn } from '@/utils/cn';
import { plural, timeAgo } from '@/utils/format';

const TYPES: { value: QuestionType; label: string }[] = [
  { value: 'MCQ', label: 'Multiple choice' },
  { value: 'TRUE_FALSE', label: 'True / false' },
  { value: 'SHORT_ANSWER', label: 'Short answer' },
];

const LEVELS: Difficulty[] = ['EASY', 'MEDIUM', 'HARD'];
const ALL_DOCS = 'all';

export function QuizCenterPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const courses = useCourses();
  const quizzes = useQuery({ queryKey: ['quizzes'], queryFn: quizApi.list });

  const [courseId, setCourseId] = useState<string>();
  const [documentId, setDocumentId] = useState(ALL_DOCS);
  const [count, setCount] = useState('10');
  const [difficulty, setDifficulty] = useState<Difficulty>('MEDIUM');
  const [types, setTypes] = useState<QuestionType[]>(['MCQ', 'TRUE_FALSE', 'SHORT_ANSWER']);

  const activeCourse = courseId ?? courses.data?.content[0]?.id;
  const documents = useDocuments({ courseId: activeCourse }, !!activeCourse);
  const ready = documents.data?.content.filter((document) => document.status === 'READY') ?? [];
  const parsedCount = Number.parseInt(count, 10);
  const countValid = parsedCount >= 1 && parsedCount <= 25;

  const generate = useMutation({
    mutationFn: () => {
      const options = { questionCount: parsedCount, difficulty, types };
      return documentId === ALL_DOCS ? quizApi.forCourse(activeCourse!, options) : quizApi.forDocument(documentId, options);
    },
    onSuccess: (quiz) => {
      queryClient.invalidateQueries({ queryKey: ['quizzes'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      navigate(`/quizzes/${quiz.id}`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <>
      <PageHeader title="Quiz Center" description="Practice with questions written from your own notes." />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[360px_minmax(0,1fr)]">
        <Card className="h-fit">
          <CardHeader>
            <CardTitle>New quiz</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1.5">
              <Label>Course</Label>
              <Select
                ariaLabel="Course"
                value={activeCourse}
                placeholder="Select a course"
                options={(courses.data?.content ?? []).map((course) => ({ value: course.id, label: course.name }))}
                onChange={(value) => {
                  setCourseId(value);
                  setDocumentId(ALL_DOCS);
                }}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Material</Label>
              <Select
                ariaLabel="Material"
                value={documentId}
                options={[{ value: ALL_DOCS, label: 'Entire course' }, ...ready.map((document) => ({ value: document.id, label: document.filename }))]}
                onChange={setDocumentId}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="quiz-count">Number of questions</Label>
              <Input id="quiz-count" type="number" min={1} max={25} value={count} onChange={(event) => setCount(event.target.value)} aria-invalid={!countValid} />
              {!countValid && <FieldError message="Choose between 1 and 25 questions." />}
            </div>
            <div className="space-y-1.5">
              <Label>Difficulty</Label>
              <div className="grid grid-cols-3 gap-1 rounded-md bg-muted p-1" role="radiogroup" aria-label="Difficulty">
                {LEVELS.map((level) => (
                  <button
                    key={level}
                    role="radio"
                    aria-checked={difficulty === level}
                    onClick={() => setDifficulty(level)}
                    className={cn('rounded py-1 text-[13px] font-medium capitalize text-muted-foreground', difficulty === level && 'bg-card text-foreground shadow-card')}
                  >
                    {level.toLowerCase()}
                  </button>
                ))}
              </div>
            </div>
            <fieldset className="space-y-1.5">
              <legend className="text-[13px] font-medium">Question types</legend>
              {TYPES.map((type) => (
                <label key={type.value} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4 accent-[hsl(var(--primary))]"
                    checked={types.includes(type.value)}
                    onChange={(event) =>
                      setTypes((current) => (event.target.checked ? [...current, type.value] : current.filter((item) => item !== type.value)))
                    }
                  />
                  {type.label}
                </label>
              ))}
            </fieldset>
            <Button
              className="w-full"
              loading={generate.isPending}
              disabled={!activeCourse || ready.length === 0 || !countValid || types.length === 0}
              onClick={() => generate.mutate()}
            >
              {!generate.isPending && <Sparkles />}
              {generate.isPending ? 'Writing questions…' : 'Generate quiz'}
            </Button>
            {activeCourse && documents.isSuccess && ready.length === 0 && (
              <p className="text-[13px] text-muted-foreground">This course has no processed documents yet.</p>
            )}
          </CardContent>
        </Card>

        <section>
          <h2 className="mb-3 text-sm font-semibold">Your quizzes</h2>
          {quizzes.isLoading && <ListSkeleton />}
          {quizzes.isError && <ErrorState message={errorMessage(quizzes.error)} onRetry={() => quizzes.refetch()} />}
          {quizzes.data?.content.length === 0 && (
            <EmptyState icon={GraduationCap} title="No quizzes yet" description="Generate one from a course or a single document." />
          )}
          {!!quizzes.data?.content.length && (
            <ul className="divide-y rounded-lg border bg-card shadow-card">
              {quizzes.data.content.map((quiz) => (
                <li key={quiz.id}>
                  <Link to={`/quizzes/${quiz.id}`} className="flex items-center justify-between gap-3 px-4 py-3 hover:bg-accent/50">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{quiz.title}</p>
                      <p className="text-[13px] text-muted-foreground">
                        {plural(quiz.questionCount, 'question')} · {timeAgo(quiz.createdAt)}
                      </p>
                    </div>
                    {quiz.lastScore != null ? (
                      <Badge tone="primary" className="tabular-nums">{quiz.lastScore}/{quiz.lastTotal}</Badge>
                    ) : (
                      <Badge>Not taken</Badge>
                    )}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
