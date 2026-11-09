import { useMutation, useQueryClient } from '@tanstack/react-query';
import { AlertCircle, CheckCircle2, Clock, Eye, FileText, Loader2, MoreHorizontal, RotateCw, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';
import { ConfirmDialog } from '@/components/common';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/card';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown';
import { PdfViewerDialog } from '@/components/pdf/PdfViewerDialog';
import { errorMessage } from '@/services/api';
import { documentApi } from '@/services/endpoints';
import type { DocumentItem, DocumentStatus } from '@/types';
import { formatBytes, plural, timeAgo } from '@/utils/format';

const STATUS: Record<DocumentStatus, { label: string; tone: 'neutral' | 'primary' | 'success' | 'danger'; icon: typeof Clock; spin?: boolean }> = {
  UPLOADED: { label: 'Queued', tone: 'neutral', icon: Clock },
  PROCESSING: { label: 'Processing', tone: 'primary', icon: Loader2, spin: true },
  READY: { label: 'Ready', tone: 'success', icon: CheckCircle2 },
  FAILED: { label: 'Failed', tone: 'danger', icon: AlertCircle },
};

export function StatusBadge({ status }: { status: DocumentStatus }) {
  const { label, tone, icon: Icon, spin } = STATUS[status];
  return (
    <Badge tone={tone}>
      <Icon className={spin ? 'animate-spin' : undefined} />
      {label}
    </Badge>
  );
}

export function DocumentList({ documents }: { documents: DocumentItem[] }) {
  const queryClient = useQueryClient();
  const [toDelete, setToDelete] = useState<DocumentItem | null>(null);
  const [viewing, setViewing] = useState<DocumentItem | null>(null);

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['documents'] });
    queryClient.invalidateQueries({ queryKey: ['courses'] });
    queryClient.invalidateQueries({ queryKey: ['folders'] });
    queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  };

  const retry = useMutation({
    mutationFn: documentApi.retry,
    onSuccess: refresh,
    onError: (error) => toast.error(errorMessage(error)),
  });

  const remove = async (document: DocumentItem) => {
    try {
      await documentApi.remove(document.id);
      toast.success('Document deleted');
      refresh();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  return (
    <>
      <ul className="divide-y rounded-lg border bg-card shadow-card">
        {documents.map((document) => (
          <li key={document.id} className="flex items-center gap-3 px-4 py-3">
            <div className="grid size-9 shrink-0 place-items-center rounded-md bg-primary-soft text-primary">
              <FileText className="size-4" />
            </div>
            <div className="min-w-0 flex-1">
              <button
                className="block max-w-full truncate text-left text-sm font-medium hover:underline"
                onClick={() => setViewing(document)}
              >
                {document.filename}
              </button>
              <p className="truncate text-[13px] text-muted-foreground">
                {formatBytes(document.fileSize)}
                {document.pageCount != null && ` · ${plural(document.pageCount, 'page')}`} · {timeAgo(document.createdAt)}
              </p>
              {document.status === 'FAILED' && document.errorMessage && (
                <p className="mt-0.5 text-[13px] text-destructive">{document.errorMessage}</p>
              )}
            </div>
            <StatusBadge status={document.status} />
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${document.filename}`}>
                  <MoreHorizontal />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent>
                <DropdownMenuItem onSelect={() => setViewing(document)}>
                  <Eye /> Open
                </DropdownMenuItem>
                {document.status === 'FAILED' && document.canManage && (
                  <DropdownMenuItem onSelect={() => retry.mutate(document.id)}>
                    <RotateCw /> Retry processing
                  </DropdownMenuItem>
                )}
                {document.canManage && (
                  <DropdownMenuItem className="text-destructive" onSelect={() => setToDelete(document)}>
                    <Trash2 /> Delete
                  </DropdownMenuItem>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          </li>
        ))}
      </ul>
      <ConfirmDialog
        open={!!toDelete}
        onOpenChange={(open) => !open && setToDelete(null)}
        title="Delete this document?"
        description={`"${toDelete?.filename}" and its searchable content will be permanently removed.`}
        onConfirm={() => (toDelete ? remove(toDelete) : undefined)}
      />
      <PdfViewerDialog document={viewing} onClose={() => setViewing(null)} />
    </>
  );
}
