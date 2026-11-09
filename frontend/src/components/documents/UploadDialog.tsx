import { useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, FileUp, XCircle } from 'lucide-react';
import { useRef, useState, type DragEvent } from 'react';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { errorMessage } from '@/services/api';
import { documentApi, type DocumentScope } from '@/services/endpoints';
import { cn } from '@/utils/cn';

const MAX_BYTES = 20 * 1024 * 1024;

interface UploadItem {
  key: string;
  name: string;
  progress: number;
  state: 'uploading' | 'done' | 'error';
  error?: string;
}

export function UploadDialog({
  open,
  onOpenChange,
  scope,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  scope: DocumentScope;
}) {
  const [items, setItems] = useState<UploadItem[]>([]);
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();

  const patch = (key: string, change: Partial<UploadItem>) =>
    setItems((current) => current.map((item) => (item.key === key ? { ...item, ...change } : item)));

  const uploadFile = async (file: File) => {
    const key = `${file.name}-${file.lastModified}-${Math.random()}`;
    const clientError = !file.name.toLowerCase().endsWith('.pdf')
      ? 'Only PDF files are accepted.'
      : file.size > MAX_BYTES
        ? 'File is larger than 20 MB.'
        : undefined;
    setItems((current) => [...current, { key, name: file.name, progress: 0, state: clientError ? 'error' : 'uploading', error: clientError }]);
    if (clientError) return;
    try {
      await documentApi.upload(file, scope, (progress) => patch(key, { progress }));
      patch(key, { state: 'done', progress: 100 });
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      queryClient.invalidateQueries({ queryKey: ['courses'] });
      queryClient.invalidateQueries({ queryKey: ['folders'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    } catch (error) {
      patch(key, { state: 'error', error: errorMessage(error) });
    }
  };

  const handleFiles = (files: FileList | null) => {
    if (files) Array.from(files).forEach(uploadFile);
  };

  const onDrop = (event: DragEvent) => {
    event.preventDefault();
    setDragging(false);
    handleFiles(event.dataTransfer.files);
  };

  const close = (next: boolean) => {
    onOpenChange(next);
    if (!next) setItems([]);
  };

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent title="Upload lecture notes" description="PDF files up to 20 MB. They are processed in the background.">
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          onDragOver={(event) => {
            event.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          className={cn(
            'flex w-full flex-col items-center gap-2 rounded-lg border border-dashed px-6 py-10 text-center transition-colors hover:bg-accent/50',
            dragging && 'border-primary bg-primary-soft',
          )}
        >
          <FileUp className="size-6 text-primary" />
          <span className="text-sm font-medium">Drop PDFs here, or click to browse</span>
          <span className="text-[13px] text-muted-foreground">You can select several files at once</span>
        </button>
        <input
          ref={inputRef}
          data-testid="file-input"
          type="file"
          accept="application/pdf,.pdf"
          multiple
          hidden
          onChange={(event) => {
            handleFiles(event.target.files);
            event.target.value = '';
          }}
        />

        {items.length > 0 && (
          <ul className="mt-4 space-y-3">
            {items.map((item) => (
              <li key={item.key}>
                <div className="flex items-center justify-between gap-3 text-sm">
                  <span className="truncate">{item.name}</span>
                  {item.state === 'done' && <CheckCircle2 aria-label="Uploaded" className="size-4 shrink-0 text-success" />}
                  {item.state === 'error' && <XCircle aria-label="Failed" className="size-4 shrink-0 text-destructive" />}
                  {item.state === 'uploading' && <span className="shrink-0 text-xs tabular-nums text-muted-foreground">{item.progress}%</span>}
                </div>
                {item.state === 'uploading' && <Progress value={item.progress} className="mt-1.5" />}
                {item.error && <p role="alert" className="mt-1 text-[13px] text-destructive">{item.error}</p>}
              </li>
            ))}
          </ul>
        )}
        <div className="mt-4 flex justify-end">
          <Button variant="outline" onClick={() => close(false)}>
            Done
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
