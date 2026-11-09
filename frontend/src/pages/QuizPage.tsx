import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Check, ChevronLeft, ChevronRight, FileText, RotateCcw, X } from 'lucide-react';
import { lazy, Suspense, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ErrorState, ListSkeleton } from '@/components/common';
import { Button } from '@/components/ui/button';
import { Badge, Card, Progress } from '@/components/ui/card';
import { Textarea } from '@/components/ui/input';
import { errorMessage } from '@/services/api';
import { quizApi } from '@/services/endpoints';
import type { AttemptResult, GradedQuestion, QuizQuestion } from '@/types';
import { cn } from '@/utils/cn';
import { plural } from '@/utils/format';

const PdfViewer = lazy(() => import('@/components/pdf/PdfViewer').then((module) => ({ default: module.PdfViewer })));

const TYPE_LABEL = { MCQ: 'Multiple choice', TRUE_FALSE: 'True / false', SHORT_ANSWER: 'Short answer' };

function Results({ result, onRetake }: { result: AttemptResult; onRetake: () => void }) {
  const [source, setSource] = useState<GradedQuestion | null>(null);
  const percent = result.total ? Math.round((result.score / result.total) * 100) : 0;
  return (
    <>
      <Card className="flex flex-wrap items-center justify-between gap-4 p-5">
        <div>
          <p className="text-[13px] text-muted-foreground">Your score</p>
          <p className="text-3xl font-semibold tabular-nums tracking-tight">
            {result.score}/{result.total} <span className="text-lg font-normal text-muted-foreground">{percent}%</span>
          </p>
        </div>
        <Button variant="outline" onClick={onRetake}><RotateCcw /> Retake quiz</Button>
      </Card>
      <ol className="mt-4 space-y-3">
        {result.questions.map((question, index) => (
          <li key={question.id}>
            <Card className="p-4">
              <div className="flex items-start gap-3">
                <span className={cn('mt-0.5 grid size-5 shrink-0 place-items-center rounded-full', question.correct ? 'bg-success/15 text-success' : 'bg-destructive/15 text-destructive')}>
                  {question.correct ? <Check className="size-3" aria-label="Correct" /> : <X className="size-3" aria-label="Incorrect" />}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">{index + 1}. {question.question}</p>
                  <p className="mt-2 text-[13px]"><span className="text-muted-foreground">Your answer: </span>{question.yourAnswer || <em>No answer</em>}</p>
                  {!question.correct && <p className="text-[13px]"><span className="text-muted-foreground">Correct answer: </span>{question.correctAnswer}</p>}
                  {question.explanation && <p className="mt-2 text-[13px] leading-5 text-muted-foreground">{question.explanation}</p>}
                  <button className="mt-2 inline-flex items-center gap-1 text-[13px] font-medium text-primary hover:underline" onClick={() => setSource(question)}>
                    <FileText className="size-3.5" /> {question.documentName} — Page {question.pageNumber}
                  </button>
                </div>
              </div>
            </Card>
          </li>
        ))}
      </ol>
      {source && (
        <div className="fixed inset-y-0 right-0 z-40 w-full max-w-2xl border-l bg-background shadow-pop">
          <Suspense fallback={null}>
            <PdfViewer target={{ documentId: source.documentId, documentName: source.documentName, page: source.pageNumber }} onClose={() => setSource(null)} />
          </Suspense>
        </div>
      )}
    </>
  );
}

function QuestionView({ question, answer, onAnswer }: { question: QuizQuestion; answer: string; onAnswer: (value: string) => void }) {
  if (question.type === 'SHORT_ANSWER') {
    return <Textarea aria-label="Your answer" placeholder="Type your answer" value={answer} onChange={(event) => onAnswer(event.target.value)} />;
  }
  return (
    <div className="space-y-2" role="radiogroup" aria-label={question.question}>
      {question.options.map((option) => (
        <button
          key={option}
          role="radio"
          aria-checked={answer === option}
          onClick={() => onAnswer(option)}
          className={cn('w-full rounded-lg border p-3 text-left text-sm transition-colors hover:border-primary/50', answer === option && 'border-primary bg-primary-soft font-medium')}
        >
          {option}
        </button>
      ))}
    </div>
  );
}

export function QuizPage() {
  const { quizId = '' } = useParams();
  const queryClient = useQueryClient();
  const quiz = useQuery({ queryKey: ['quiz', quizId], queryFn: () => quizApi.get(quizId) });
  const latest = useQuery({ queryKey: ['quiz', quizId, 'latest'], queryFn: () => quizApi.latest(quizId) });
  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [retaking, setRetaking] = useState(false);

  const submit = useMutation({
    mutationFn: () => quizApi.submit(quizId, answers),
    onSuccess: (result) => {
      queryClient.setQueryData(['quiz', quizId, 'latest'], result);
      queryClient.invalidateQueries({ queryKey: ['quizzes'] });
      setRetaking(false);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (quiz.isError) return <ErrorState message={errorMessage(quiz.error)} onRetry={() => quiz.refetch()} />;
  if (quiz.isLoading || latest.isLoading || !quiz.data) return <ListSkeleton />;

  const result = latest.data && !retaking ? latest.data : null;
  const questions = quiz.data.questions;
  const current = questions[index];
  const answered = questions.filter((question) => answers[question.id]?.trim()).length;

  return (
    <div className="mx-auto max-w-2xl">
      <Link to="/quizzes" className="mb-3 inline-flex items-center gap-1 text-[13px] text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-3.5" /> Quiz Center
      </Link>
      <h1 className="mb-1 text-xl font-semibold tracking-tight">{quiz.data.title}</h1>
      <p className="mb-5 text-sm text-muted-foreground">{plural(questions.length, 'question')}</p>

      {result ? (
        <Results
          result={result}
          onRetake={() => {
            setAnswers({});
            setIndex(0);
            setRetaking(true);
          }}
        />
      ) : (
        <>
          <div className="mb-4 flex items-center gap-3">
            <Progress value={(answered / questions.length) * 100} className="flex-1" />
            <span className="text-xs tabular-nums text-muted-foreground">{answered}/{questions.length} answered</span>
          </div>
          <nav className="mb-4 flex flex-wrap gap-1.5" aria-label="Questions">
            {questions.map((question, i) => (
              <button
                key={question.id}
                aria-label={`Question ${i + 1}`}
                aria-current={i === index ? 'step' : undefined}
                onClick={() => setIndex(i)}
                className={cn(
                  'size-8 rounded-md border text-[13px] tabular-nums transition-colors hover:border-primary/50',
                  answers[question.id]?.trim() && 'bg-primary-soft text-primary',
                  i === index && 'border-primary font-semibold',
                )}
              >
                {i + 1}
              </button>
            ))}
          </nav>
          <Card className="p-5">
            <Badge className="mb-3">{TYPE_LABEL[current.type]}</Badge>
            <h2 className="mb-4 text-[15px] font-medium leading-6">{current.question}</h2>
            <QuestionView
              question={current}
              answer={answers[current.id] ?? ''}
              onAnswer={(value) => setAnswers((previous) => ({ ...previous, [current.id]: value }))}
            />
          </Card>
          <div className="mt-4 flex items-center justify-between">
            <Button variant="outline" disabled={index === 0} onClick={() => setIndex(index - 1)}><ChevronLeft /> Previous</Button>
            {index < questions.length - 1 ? (
              <Button variant="outline" onClick={() => setIndex(index + 1)}>Next <ChevronRight /></Button>
            ) : (
              <Button loading={submit.isPending} onClick={() => submit.mutate()}>Submit quiz</Button>
            )}
          </div>
        </>
      )}
    </div>
  );
}
