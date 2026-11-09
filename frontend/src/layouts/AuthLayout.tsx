import { FileSearch, Quote, ShieldCheck } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Logo } from '@/components/common';

const POINTS = [
  { icon: FileSearch, text: 'Answers retrieved from your own uploaded PDFs' },
  { icon: Quote, text: 'Every claim linked to its exact page' },
  { icon: ShieldCheck, text: 'Your notes stay private to your account' },
];

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <div className="flex flex-col px-6 py-8 sm:px-12">
        <Link to="/" className="w-fit">
          <Logo />
        </Link>
        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-10">
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          <p className="mb-6 mt-1 text-sm text-muted-foreground">{subtitle}</p>
          {children}
        </div>
      </div>
      <div className="hidden items-center justify-center border-l bg-sidebar p-12 lg:flex">
        <div className="max-w-md">
          <p className="text-2xl font-semibold leading-snug tracking-tight">Your lecture notes. Your AI study partner.</p>
          <ul className="mt-8 space-y-4">
            {POINTS.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-sm text-muted-foreground">
                <span className="grid size-9 shrink-0 place-items-center rounded-md border bg-card text-primary">
                  <Icon className="size-4" />
                </span>
                {text}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
