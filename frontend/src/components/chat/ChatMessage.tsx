import { AlertCircle, FileText, Quote, SearchX } from 'lucide-react';
import { memo, useMemo, type ComponentProps } from 'react';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { SourceRef } from '@/types';
import { formatTime } from '@/utils/format';

const CITE_PREFIX = '#cite-';

/** Turns [S1] markers into links that the Markdown renderer maps to citation chips. */
export function linkCitations(text: string): string {
  return text.replace(/\[S(\d+)\]/g, ` [S$1](${CITE_PREFIX}S$1)`);
}

const NOT_FOUND_PREFIXES = ["I couldn't find this information", 'আপনার আপলোড করা নোটে'];

export function SourceCard({ source, onOpen }: { source: SourceRef; onOpen: (source: SourceRef) => void }) {
  return (
    <button
      onClick={() => onOpen(source)}
      className="flex w-full items-start gap-2.5 rounded-lg border bg-card p-2.5 text-left transition-colors hover:border-primary/50 hover:bg-primary-soft/40"
    >
      <FileText className="mt-0.5 size-4 shrink-0 text-primary" />
      <span className="min-w-0">
        <span className="block truncate text-[13px] font-medium">
          {source.documentName} — Page {source.pageNumber}
        </span>
        <span className="line-clamp-2 text-[13px] leading-5 text-muted-foreground">{source.snippet}</span>
      </span>
    </button>
  );
}

function Answer({ text, sources, onOpen }: { text: string; sources: SourceRef[]; onOpen: (source: SourceRef) => void }) {
  const components = useMemo<ComponentProps<typeof Markdown>['components']>(
    () => ({
      a: ({ href, children }) => {
        if (href?.startsWith(CITE_PREFIX)) {
          const source = sources.find((candidate) => candidate.label === href.slice(CITE_PREFIX.length));
          if (!source) return null;
          return (
            <button
              onClick={() => onOpen(source)}
              title={`${source.documentName} — Page ${source.pageNumber}`}
              aria-label={`Open ${source.documentName} at page ${source.pageNumber}`}
              className="mx-0.5 inline-flex h-5 translate-y-[-1px] items-center rounded bg-primary-soft px-1.5 text-xs font-medium text-primary transition-colors hover:bg-primary hover:text-primary-foreground"
            >
              p. {source.pageNumber}
            </button>
          );
        }
        return (
          <a href={href} target="_blank" rel="noreferrer noopener" className="text-primary underline underline-offset-2">
            {children}
          </a>
        );
      },
    }),
    [sources, onOpen],
  );

  return (
    <div className="prose-chat">
      <Markdown remarkPlugins={[remarkGfm]} components={components}>
        {linkCitations(text)}
      </Markdown>
    </div>
  );
}

function Sources({ sources, onOpen }: { sources: SourceRef[]; onOpen: (source: SourceRef, list: SourceRef[]) => void }) {
  if (sources.length === 0) return null;
  const cited = sources.some((source) => source.cited);
  return (
    <div className="mt-3">
      <p className="mb-1.5 flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
        <Quote className="size-3" />
        {cited ? 'Sources' : 'Related passages (the answer did not cite these directly)'}
      </p>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        {sources.map((source) => (
          <SourceCard key={source.label} source={source} onOpen={(item) => onOpen(item, sources)} />
        ))}
      </div>
    </div>
  );
}

export const UserBubble = memo(function UserBubble({ text, at }: { text: string; at?: string }) {
  return (
    <div className="flex flex-col items-end">
      <div className="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-br-sm bg-primary px-4 py-2 text-[15px] leading-6 text-primary-foreground">
        {text}
      </div>
      {at && <time className="mt-1 text-xs text-muted-foreground">{formatTime(at)}</time>}
    </div>
  );
});

export const AssistantMessage = memo(function AssistantMessage({
  text,
  sources,
  at,
  onOpenSource,
}: {
  text: string;
  sources: SourceRef[];
  at?: string;
  onOpenSource: (source: SourceRef, list: SourceRef[]) => void;
}) {
  const open = useMemo(() => (source: SourceRef) => onOpenSource(source, sources), [onOpenSource, sources]);
  const notFound = NOT_FOUND_PREFIXES.some((prefix) => text.startsWith(prefix));

  return (
    <div>
      {notFound ? (
        <div className="flex gap-3 rounded-lg border bg-card p-3.5 text-sm leading-6">
          <SearchX className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
          <p>{text}</p>
        </div>
      ) : (
        <Answer text={text} sources={sources} onOpen={open} />
      )}
      <Sources sources={sources} onOpen={onOpenSource} />
      {at && <time className="mt-1.5 block text-xs text-muted-foreground">{formatTime(at)}</time>}
    </div>
  );
});

export function StreamingMessage({
  text,
  sources,
  stage,
  error,
  onOpenSource,
  onDismissError,
}: {
  text: string;
  sources: SourceRef[];
  stage: 'searching' | 'generating';
  error?: string;
  onOpenSource: (source: SourceRef, list: SourceRef[]) => void;
  onDismissError: () => void;
}) {
  if (error) {
    return (
      <div role="alert" className="flex items-start gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-3.5 text-sm">
        <AlertCircle className="mt-0.5 size-4 shrink-0 text-destructive" />
        <div className="min-w-0 flex-1">
          <p>{error}</p>
          <button className="mt-1 text-[13px] font-medium text-primary hover:underline" onClick={onDismissError}>
            Dismiss
          </button>
        </div>
      </div>
    );
  }
  const open = (source: SourceRef) => onOpenSource(source, sources);
  return (
    <div>
      {text ? (
        <Answer text={text.replace(/\s*\[S?\d*$/, '')} sources={sources} onOpen={open} />
      ) : (
        <div className="flex items-center gap-2 text-sm text-muted-foreground" role="status">
          <span className="flex gap-1" aria-hidden="true">
            {[0, 150, 300].map((delay) => (
              <span key={delay} className="size-1.5 animate-pulse-dot rounded-full bg-primary" style={{ animationDelay: `${delay}ms` }} />
            ))}
          </span>
          {stage === 'searching' ? 'Searching your notes…' : `Reading ${sources.length} passages…`}
        </div>
      )}
      <span className="sr-only" role="status">
        {text ? 'Generating answer' : ''}
      </span>
    </div>
  );
}
