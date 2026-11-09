import { ArrowRight, BookOpenCheck, Brain, FileSearch, FileText, FolderOpen, GraduationCap, Languages, Quote, Search, ShieldCheck, Sparkles, Upload } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Logo } from '@/components/common';
import { Button } from '@/components/ui/button';

const FEATURES = [
  { icon: Search, title: 'Semantic and keyword search', text: 'Finds the right passages by meaning and by exact terms, across one PDF or a whole course.' },
  { icon: Quote, title: 'Page-level citations', text: 'Click a citation and the PDF opens at that page with the source passage highlighted.' },
  { icon: ShieldCheck, title: 'Grounded answers', text: 'If your notes do not cover something, NoteMind says so instead of making it up.' },
  { icon: GraduationCap, title: 'Quizzes from your notes', text: 'Multiple-choice, true/false and short-answer questions, each tied to a source page.' },
  { icon: FolderOpen, title: 'Shared class folders', text: 'Class representatives share course material with exactly the students they choose.' },
  { icon: Languages, title: 'Bangla and English', text: 'Ask in either language and get the answer in the language you used.' },
];

const STEPS = [
  { icon: Upload, title: 'Upload your PDFs', text: 'Lecture slides, handouts and notes. Scanned pages are read with OCR.' },
  { icon: Brain, title: 'NoteMind indexes them', text: 'Text is extracted page by page and indexed in the background.' },
  { icon: Sparkles, title: 'Ask anything', text: 'Get a clear answer with sources you can open and check.' },
];

const BENEFITS = [
  'Stop scrolling through hundreds of slides the night before an exam',
  'Check every answer against the original page in one click',
  'Revise with practice questions made from your own course material',
  'Keep each course, its documents and its conversations organised',
];

function ProductPreview() {
  return (
    <div className="overflow-hidden rounded-xl border bg-card shadow-pop" role="img" aria-label="Illustration of the NoteMind chat screen beside a PDF with a highlighted passage">
      <div className="grid md:grid-cols-[minmax(0,1fr)_0.85fr]">
        <div className="space-y-4 p-5 sm:p-6">
          <div className="ml-auto w-fit max-w-[85%] rounded-2xl rounded-br-sm bg-primary px-4 py-2 text-sm text-primary-foreground">
            What does the lexical analyzer produce?
          </div>
          <div className="text-sm leading-7">
            The lexical analyzer reads source characters and groups them into a stream of <strong>tokens</strong>
            <span className="mx-1 rounded bg-primary-soft px-1.5 py-0.5 text-xs font-medium text-primary">p. 12</span>
            which the parser then consumes.
          </div>
          <div className="flex items-start gap-3 rounded-lg border p-3">
            <FileText className="mt-0.5 size-4 shrink-0 text-primary" />
            <div className="min-w-0">
              <p className="truncate text-[13px] font-medium">Compiler Design.pdf — Page 12</p>
              <p className="line-clamp-1 text-[13px] text-muted-foreground">Lexical analysis is the first phase of a compiler…</p>
            </div>
          </div>
        </div>
        <div className="hidden border-l bg-sidebar p-5 md:block">
          <div className="mx-auto space-y-2 rounded bg-white p-5 shadow-card">
            <div className="h-2.5 w-2/3 rounded bg-slate-300" />
            <div className="h-1.5 rounded bg-slate-200" />
            <div className="h-1.5 w-11/12 rounded bg-slate-200" />
            <div className="rounded bg-amber-300/60 p-1">
              <div className="h-1.5 rounded bg-slate-400/70" />
              <div className="mt-1.5 h-1.5 w-4/5 rounded bg-slate-400/70" />
            </div>
            <div className="h-1.5 rounded bg-slate-200" />
            <div className="h-1.5 w-3/4 rounded bg-slate-200" />
            <div className="h-1.5 w-5/6 rounded bg-slate-200" />
          </div>
        </div>
      </div>
    </div>
  );
}

export function LandingPage() {
  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-30 border-b bg-background/90 backdrop-blur">
        <nav className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4 md:px-8" aria-label="Site">
          <Logo />
          <div className="hidden items-center gap-6 text-sm text-muted-foreground md:flex">
            <a href="#features" className="hover:text-foreground">Features</a>
            <a href="#how" className="hover:text-foreground">How it works</a>
            <a href="#benefits" className="hover:text-foreground">Benefits</a>
          </div>
          <div className="flex items-center gap-1.5">
            <Button asChild variant="ghost" size="sm"><Link to="/login">Sign in</Link></Button>
            <Button asChild size="sm"><Link to="/register">Get Started</Link></Button>
          </div>
        </nav>
      </header>

      <section className="mx-auto max-w-6xl px-4 pb-16 pt-16 text-center md:px-8 md:pt-24">
        <h1 className="mx-auto max-w-3xl text-4xl font-semibold tracking-tight sm:text-5xl">Your Notes. Your Knowledge. One AI Assistant.</h1>
        <p className="mx-auto mt-5 max-w-xl text-lg text-muted-foreground">
          Upload your lecture notes, ask anything, and get accurate answers backed by page-level sources.
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Button asChild size="lg"><Link to="/register">Get Started <ArrowRight /></Link></Button>
          <Button asChild size="lg" variant="outline"><a href="#features">Explore Features</a></Button>
        </div>
        <div className="mx-auto mt-14 max-w-4xl text-left">
          <ProductPreview />
        </div>
      </section>

      <section id="features" className="border-t bg-sidebar py-20">
        <div className="mx-auto max-w-6xl px-4 md:px-8">
          <h2 className="text-center text-2xl font-semibold tracking-tight">Built for how students actually revise</h2>
          <div className="mt-10 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map(({ icon: Icon, title, text }) => (
              <div key={title} className="rounded-lg border bg-card p-5 shadow-card">
                <Icon className="size-5 text-primary" />
                <h3 className="mt-3 text-sm font-semibold">{title}</h3>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">{text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section id="how" className="py-20">
        <div className="mx-auto max-w-6xl px-4 md:px-8">
          <h2 className="text-center text-2xl font-semibold tracking-tight">How it works</h2>
          <ol className="mt-10 grid grid-cols-1 gap-8 md:grid-cols-3">
            {STEPS.map(({ icon: Icon, title, text }, index) => (
              <li key={title} className="text-center">
                <span className="mx-auto grid size-11 place-items-center rounded-full bg-primary-soft text-primary"><Icon className="size-5" /></span>
                <h3 className="mt-4 text-sm font-semibold">{index + 1}. {title}</h3>
                <p className="mx-auto mt-1 max-w-xs text-sm leading-6 text-muted-foreground">{text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section id="benefits" className="border-t bg-sidebar py-20">
        <div className="mx-auto grid max-w-6xl items-center gap-10 px-4 md:grid-cols-2 md:px-8">
          <div>
            <FileSearch className="size-6 text-primary" />
            <h2 className="mt-3 text-2xl font-semibold tracking-tight">Less searching. More understanding.</h2>
            <p className="mt-2 text-muted-foreground">NoteMind answers from your course material, so you always know where an answer came from.</p>
          </div>
          <ul className="space-y-3">
            {BENEFITS.map((benefit) => (
              <li key={benefit} className="flex gap-3 rounded-lg border bg-card p-4 text-sm shadow-card">
                <BookOpenCheck className="size-4 shrink-0 translate-y-0.5 text-primary" />
                {benefit}
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="py-20 text-center">
        <h2 className="text-2xl font-semibold tracking-tight">Ready for your next exam?</h2>
        <p className="mt-2 text-muted-foreground">Create a course, upload a PDF and ask your first question.</p>
        <Button asChild size="lg" className="mt-6"><Link to="/register">Get Started <ArrowRight /></Link></Button>
      </section>

      <footer className="border-t py-8">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 text-sm text-muted-foreground md:px-8">
          <Logo />
          <p>Your Lecture Notes. Your AI Study Partner.</p>
        </div>
      </footer>
    </div>
  );
}
