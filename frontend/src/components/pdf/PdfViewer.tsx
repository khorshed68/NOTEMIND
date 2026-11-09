import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, Loader2, Minus, Plus, X } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState, type ComponentProps } from 'react';
import { Document, Page, pdfjs } from 'react-pdf';
import 'react-pdf/dist/Page/TextLayer.css';
import { ErrorState } from '@/components/common';
import { Button } from '@/components/ui/button';
import { documentApi } from '@/services/endpoints';
import { errorMessage } from '@/services/api';
import { findHighlightRanges, renderHighlightedItem, type HighlightRanges } from '@/utils/pdfHighlight';

pdfjs.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).toString();

type TextContent = Parameters<NonNullable<ComponentProps<typeof Page>['onGetTextSuccess']>>[0];

const MIN_ZOOM = 0.6;
const MAX_ZOOM = 2.5;

export interface PdfTarget {
  documentId: string;
  documentName: string;
  page: number;
  highlight?: string;
  /** Changes on each citation click, so re-clicking the same source re-navigates. */
  nonce?: number;
}

interface PdfViewerProps {
  target: PdfTarget;
  onClose?: () => void;
  /** Provided when the viewer is opened from an answer with several sources. */
  sourceNav?: { index: number; total: number; onPrev: () => void; onNext: () => void };
}

export function PdfViewer({ target, onClose, sourceNav }: PdfViewerProps) {
  const [pageCount, setPageCount] = useState(0);
  const [page, setPage] = useState(target.page);
  const [zoom, setZoom] = useState(1);
  const [width, setWidth] = useState(0);
  const [pageInput, setPageInput] = useState(String(target.page));
  const [ranges, setRanges] = useState<HighlightRanges>(new Map());
  const containerRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const file = useQuery({
    queryKey: ['pdf', target.documentId],
    queryFn: () => documentApi.preview(target.documentId),
    staleTime: 5 * 60_000,
    gcTime: 10 * 60_000,
  });

  const fileUrl = useMemo(() => (file.data ? URL.createObjectURL(file.data) : null), [file.data]);
  useEffect(() => () => {
    if (fileUrl) URL.revokeObjectURL(fileUrl);
  }, [fileUrl]);

  useEffect(() => {
    setPage(target.page);
    setPageInput(String(target.page));
    setRanges(new Map());
  }, [target.documentId, target.page, target.nonce]);

  useEffect(() => {
    const element = containerRef.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const go = useCallback(
    (next: number) => {
      const bounded = Math.min(Math.max(1, next), pageCount || next);
      setPage(bounded);
      setPageInput(String(bounded));
      scrollRef.current?.scrollTo({ top: 0 });
    },
    [pageCount],
  );

  // Only the cited page carries the highlight; browsing elsewhere shows clean pages.
  const highlight = page === target.page ? target.highlight : undefined;

  const onTextLoaded = useCallback(
    ({ items }: TextContent) => {
      // Items keep their original indexes because the text layer reports positions against this array.
      const strings = items.map((item) => ('str' in item ? item.str : ''));
      setRanges(highlight ? findHighlightRanges(strings, highlight) : new Map());
    },
    [highlight],
  );

  const customTextRenderer = useCallback(
    ({ str, itemIndex }: { str: string; itemIndex: number }) => renderHighlightedItem(str, ranges.get(itemIndex)),
    [ranges],
  );

  const scrollToHighlight = useCallback(() => {
    scrollRef.current?.querySelector('mark.nm-highlight')?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, []);

  return (
    <div className="flex h-full flex-col bg-sidebar">
      <header className="flex h-12 shrink-0 items-center gap-2 border-b px-3">
        <p className="min-w-0 flex-1 truncate text-sm font-medium" title={target.documentName}>
          {target.documentName}
        </p>
        {sourceNav && sourceNav.total > 1 && (
          <div className="flex items-center gap-0.5 text-xs text-muted-foreground">
            <Button variant="ghost" size="icon-sm" aria-label="Previous source" onClick={sourceNav.onPrev}>
              <ChevronsLeft />
            </Button>
            <span className="tabular-nums">
              Source {sourceNav.index + 1}/{sourceNav.total}
            </span>
            <Button variant="ghost" size="icon-sm" aria-label="Next source" onClick={sourceNav.onNext}>
              <ChevronsRight />
            </Button>
          </div>
        )}
        {onClose && (
          <Button variant="ghost" size="icon-sm" aria-label="Close viewer" onClick={onClose}>
            <X />
          </Button>
        )}
      </header>

      <div className="flex h-11 shrink-0 items-center justify-between gap-2 border-b px-3">
        <div className="flex items-center gap-1 text-sm">
          <Button variant="ghost" size="icon-sm" aria-label="Previous page" disabled={page <= 1} onClick={() => go(page - 1)}>
            <ChevronLeft />
          </Button>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              const parsed = Number.parseInt(pageInput, 10);
              if (!Number.isNaN(parsed)) go(parsed);
            }}
          >
            <input
              aria-label="Page number"
              className="h-7 w-10 rounded border border-input bg-card text-center text-sm tabular-nums outline-none focus:border-ring"
              value={pageInput}
              onChange={(event) => setPageInput(event.target.value)}
              onBlur={() => setPageInput(String(page))}
            />
          </form>
          <span className="text-muted-foreground tabular-nums">/ {pageCount || '–'}</span>
          <Button variant="ghost" size="icon-sm" aria-label="Next page" disabled={!pageCount || page >= pageCount} onClick={() => go(page + 1)}>
            <ChevronRight />
          </Button>
        </div>
        <div className="flex items-center gap-1 text-sm">
          <Button variant="ghost" size="icon-sm" aria-label="Zoom out" disabled={zoom <= MIN_ZOOM} onClick={() => setZoom((z) => Math.max(MIN_ZOOM, +(z - 0.2).toFixed(1)))}>
            <Minus />
          </Button>
          <span className="w-10 text-center text-xs tabular-nums text-muted-foreground">{Math.round(zoom * 100)}%</span>
          <Button variant="ghost" size="icon-sm" aria-label="Zoom in" disabled={zoom >= MAX_ZOOM} onClick={() => setZoom((z) => Math.min(MAX_ZOOM, +(z + 0.2).toFixed(1)))}>
            <Plus />
          </Button>
        </div>
      </div>

      <div ref={scrollRef} className="scroll-thin min-h-0 flex-1 overflow-auto p-3">
        <div ref={containerRef} className="mx-auto">
          {file.isError && <ErrorState message={file.error ? errorMessage(file.error) : 'This document could not be loaded.'} onRetry={() => file.refetch()} />}
          {file.isLoading && <Spinner />}
          {fileUrl && width > 0 && (
            <Document
              file={fileUrl}
              loading={<Spinner />}
              error={<ErrorState message="This PDF could not be displayed." />}
              onLoadError={(error) => console.error('PDF failed to load:', error)}
              onSourceError={(error) => console.error('PDF source error:', error)}
              onLoadSuccess={({ numPages }) => {
                setPageCount(numPages);
                if (page > numPages) go(numPages);
              }}
            >
              <div className="mx-auto w-fit overflow-hidden rounded shadow-pop">
                <Page
                  key={`${target.documentId}-${page}-${target.nonce ?? 0}`}
                  pageNumber={page}
                  width={Math.round((width - 2) * zoom)}
                  renderAnnotationLayer={false}
                  loading={<Spinner />}
                  onGetTextSuccess={onTextLoaded}
                  customTextRenderer={customTextRenderer}
                  onRenderTextLayerSuccess={scrollToHighlight}
                />
              </div>
            </Document>
          )}
        </div>
      </div>
    </div>
  );
}

function Spinner() {
  return (
    <div className="grid place-items-center py-20 text-muted-foreground" aria-label="Loading document">
      <Loader2 className="size-5 animate-spin" />
    </div>
  );
}
