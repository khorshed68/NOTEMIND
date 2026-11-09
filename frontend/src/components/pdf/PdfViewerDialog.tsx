import * as DialogPrimitive from '@radix-ui/react-dialog';
import { lazy, Suspense } from 'react';
import type { DocumentItem } from '@/types';

const PdfViewer = lazy(() => import('./PdfViewer').then((module) => ({ default: module.PdfViewer })));

/** Slide-over used outside the chat screen. PDF.js is loaded only when a document is opened. */
export function PdfViewerDialog({ document, onClose }: { document: DocumentItem | null; onClose: () => void }) {
  return (
    <DialogPrimitive.Root open={!!document} onOpenChange={(open) => !open && onClose()}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/40" />
        <DialogPrimitive.Content className="fixed inset-y-0 right-0 z-50 w-full max-w-2xl border-l bg-background shadow-pop">
          <DialogPrimitive.Title className="sr-only">{document?.filename ?? 'Document'}</DialogPrimitive.Title>
          <DialogPrimitive.Description className="sr-only">PDF viewer</DialogPrimitive.Description>
          {document && (
            <Suspense fallback={null}>
              <PdfViewer target={{ documentId: document.id, documentName: document.filename, page: 1 }} onClose={onClose} />
            </Suspense>
          )}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
