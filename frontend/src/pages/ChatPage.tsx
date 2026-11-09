import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronDown, FileText, Menu, MessageSquare, MoreHorizontal, Pencil, Plus, Sparkles, Trash2, Upload } from 'lucide-react';
import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { AssistantMessage, StreamingMessage, UserBubble } from '@/components/chat/ChatMessage';
import { Composer } from '@/components/chat/Composer';
import { ConfirmDialog, EmptyState, ErrorState, ListSkeleton } from '@/components/common';
import type { PdfTarget } from '@/components/pdf/PdfViewer';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { useChats, useCourses, useDocuments, useFolders } from '@/hooks/queries';
import { useChatStream, type PendingExchange } from '@/hooks/useChatStream';
import { errorMessage } from '@/services/api';
import { chatApi, type DocumentScope } from '@/services/endpoints';
import type { Chat, Message, Page, SendMessageResponse, SourceRef } from '@/types';
import { cn } from '@/utils/cn';
import { timeAgo } from '@/utils/format';

const PdfViewer = lazy(() => import('@/components/pdf/PdfViewer').then((module) => ({ default: module.PdfViewer })));

const SUGGESTIONS = [
  'Explain this topic in simple terms.',
  'Summarize chapter 2.',
  'What are the important exam topics?',
  'Explain the difference between these two concepts.',
];

const scopeKey = (scope: { courseId?: string | null; folderId?: string | null }) => (scope.courseId ? `course:${scope.courseId}` : scope.folderId ? `folder:${scope.folderId}` : '');

function parseScope(key: string): DocumentScope {
  const [type, id] = key.split(':');
  return type === 'course' ? { courseId: id } : type === 'folder' ? { folderId: id } : {};
}

function ChatHistory({
  chats,
  activeId,
  onRename,
  onDelete,
}: {
  chats: Chat[];
  activeId?: string;
  onRename: (chat: Chat) => void;
  onDelete: (chat: Chat) => void;
}) {
  return (
    <ul className="space-y-0.5">
      {chats.map((chat) => (
        <li key={chat.id} className="group relative">
          <Link
            to={`/chat/${chat.id}`}
            className={cn('block rounded-md py-1.5 pl-2.5 pr-8 hover:bg-accent', chat.id === activeId && 'bg-primary-soft hover:bg-primary-soft')}
          >
            <span className={cn('block truncate text-sm', chat.id === activeId && 'font-medium text-primary')}>{chat.title}</span>
            <span className="text-xs text-muted-foreground">{timeAgo(chat.updatedAt)}</span>
          </Link>
          <div className="absolute right-1 top-1.5">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon-sm" className="size-7 opacity-60 hover:opacity-100" aria-label={`Actions for ${chat.title}`}>
                  <MoreHorizontal />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent>
                <DropdownMenuItem onSelect={() => onRename(chat)}>
                  <Pencil /> Rename
                </DropdownMenuItem>
                <DropdownMenuItem className="text-destructive" onSelect={() => onDelete(chat)}>
                  <Trash2 /> Delete
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </li>
      ))}
    </ul>
  );
}

function RenameDialog({ chat, onClose }: { chat: Chat | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [title, setTitle] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => setTitle(chat?.title ?? ''), [chat]);

  const save = async () => {
    if (!chat || !title.trim()) return;
    setBusy(true);
    try {
      await chatApi.rename(chat.id, title.trim());
      queryClient.invalidateQueries({ queryKey: ['chats'] });
      queryClient.invalidateQueries({ queryKey: ['chat', chat.id] });
      onClose();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={!!chat} onOpenChange={(open) => !open && onClose()}>
      <DialogContent title="Rename chat">
        <form
          onSubmit={(event) => {
            event.preventDefault();
            save();
          }}
          className="space-y-4"
        >
          <Input aria-label="Chat title" value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} autoFocus />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
            <Button type="submit" loading={busy}>Save</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function ChatPage() {
  const { chatId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const courses = useCourses();
  const folders = useFolders();
  const chat = useQuery({ queryKey: ['chat', chatId], queryFn: () => chatApi.get(chatId!), enabled: !!chatId });

  const [chosenScope, setChosenScope] = useState('');
  const paramScope = searchParams.get('course')
    ? `course:${searchParams.get('course')}`
    : searchParams.get('folder')
      ? `folder:${searchParams.get('folder')}`
      : '';
  const chatScope = chat.data ? scopeKey(chat.data) : '';
  const firstCourse = courses.data?.content[0]?.id;
  const activeScope = chatScope || chosenScope || paramScope || (firstCourse ? `course:${firstCourse}` : '');
  const scope = useMemo(() => parseScope(activeScope), [activeScope]);

  const documents = useDocuments(scope, !!activeScope);
  const chats = useChats(scope, !!activeScope);
  const messages = useQuery({
    queryKey: ['messages', chatId],
    queryFn: () => chatApi.messages(chatId!),
    enabled: !!chatId,
    staleTime: Infinity,
  });

  const [selectedDocs, setSelectedDocs] = useState<string[]>([]);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [renaming, setRenaming] = useState<Chat | null>(null);
  const [deleting, setDeleting] = useState<Chat | null>(null);
  const [viewer, setViewer] = useState<{ target: PdfTarget; list: SourceRef[]; index: number } | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const stickToBottom = useRef(true);

  useEffect(() => setSelectedDocs([]), [activeScope]);
  useEffect(() => setHistoryOpen(false), [chatId]);

  const appendToCache = useCallback(
    (id: string, added: Message[]) => {
      queryClient.setQueryData<Page<Message>>(['messages', id], (current) => {
        const content = [...(current?.content ?? []), ...added];
        return { content, page: 0, size: 200, totalElements: content.length, totalPages: 1 };
      });
    },
    [queryClient],
  );

  const refreshLists = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['chats'] });
    queryClient.invalidateQueries({ queryKey: ['chat'] });
    queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  }, [queryClient]);

  const onComplete = useCallback(
    (id: string, result: SendMessageResponse) => {
      appendToCache(id, [result.userMessage, result.assistantMessage]);
      refreshLists();
    },
    [appendToCache, refreshLists],
  );

  // The server stores the partial answer when it sees the disconnect; this keeps it on screen meanwhile.
  const onStopped = useCallback(
    (id: string, exchange: PendingExchange) => {
      const now = new Date().toISOString();
      const labels = new Set([...exchange.text.matchAll(/\[(S\d+)\]/g)].map((match) => match[1]));
      appendToCache(id, [
        { id: `local-u-${now}`, chatId: id, role: 'USER', content: exchange.question, sources: [], createdAt: now },
        {
          id: `local-a-${now}`,
          chatId: id,
          role: 'ASSISTANT',
          content: exchange.text,
          sources: exchange.sources.filter((source) => labels.has(source.label)).map((source) => ({ ...source, cited: true })),
          createdAt: now,
        },
      ]);
      refreshLists();
    },
    [appendToCache, refreshLists],
  );

  const { pending, send, stop, dismissError } = useChatStream({ onComplete, onStopped });
  const busy = !!pending && !pending.error;

  const handleSend = async (text: string) => {
    dismissError();
    stickToBottom.current = true;
    try {
      let id = chatId;
      if (!id) {
        const created = await chatApi.create(scope);
        id = created.id;
        queryClient.setQueryData<Page<Message>>(['messages', id], { content: [], page: 0, size: 200, totalElements: 0, totalPages: 1 });
        navigate(`/chat/${id}`, { replace: true });
      }
      await send(id, text, selectedDocs);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  const openSource = useCallback((source: SourceRef, list: SourceRef[]) => {
    setViewer({
      list,
      index: Math.max(0, list.findIndex((item) => item.label === source.label)),
      target: { documentId: source.documentId, documentName: source.documentName, page: source.pageNumber, highlight: source.snippet, nonce: Date.now() },
    });
  }, []);

  const stepSource = (delta: number) => {
    setViewer((current) => {
      if (!current) return current;
      const index = (current.index + delta + current.list.length) % current.list.length;
      const source = current.list[index];
      return {
        ...current,
        index,
        target: { documentId: source.documentId, documentName: source.documentName, page: source.pageNumber, highlight: source.snippet, nonce: Date.now() },
      };
    });
  };

  const messageList = messages.data?.content ?? [];
  useEffect(() => {
    if (stickToBottom.current && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messageList.length, pending?.text, pending?.stage]);

  const removeChat = async (target: Chat) => {
    try {
      await chatApi.remove(target.id);
      queryClient.invalidateQueries({ queryKey: ['chats'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      if (target.id === chatId) navigate(`/chat?${scopeKey(scope).replace(':', '=')}`, { replace: true });
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  const scopeOptions = [
    ...(courses.data?.content ?? []).map((course) => ({
      value: `course:${course.id}`,
      label: course.courseCode ? `${course.courseCode} — ${course.name}` : course.name,
    })),
    ...(folders.data ?? []).map((folder) => ({ value: `folder:${folder.id}`, label: `Shared: ${folder.name}` })),
  ];

  const readyDocs = documents.data?.content.filter((document) => document.status === 'READY') ?? [];
  const processing = documents.data?.content.filter((document) => document.status === 'UPLOADED' || document.status === 'PROCESSING').length ?? 0;
  const noScopes = (courses.isSuccess && folders.isSuccess) && scopeOptions.length === 0;
  const conversationEmpty = messageList.length === 0 && !pending && !messages.isLoading;
  const docLabel = selectedDocs.length === 0 ? `All documents (${readyDocs.length})` : `${selectedDocs.length} of ${readyDocs.length} documents`;

  const sidebar = (
    <div className="flex h-full flex-col gap-3 bg-sidebar p-3">
      <Select
        ariaLabel="Course"
        value={activeScope || undefined}
        placeholder="Select a course"
        options={scopeOptions}
        disabled={!!chatId && !!chat.data}
        onChange={(value) => {
          setChosenScope(value);
          navigate('/chat');
        }}
      />
      {chatId && chat.data && <p className="-mt-1.5 px-0.5 text-xs text-muted-foreground">Start a new chat to switch course.</p>}
      <Button
        variant="outline"
        className="w-full justify-start"
        onClick={() => {
          setChosenScope(activeScope);
          navigate('/chat');
          setHistoryOpen(false);
        }}
      >
        <Plus /> New chat
      </Button>
      <div className="scroll-thin min-h-0 flex-1 overflow-y-auto">
        <p className="mb-1 px-2.5 text-xs font-medium text-muted-foreground">Recent conversations</p>
        {chats.isLoading && <ListSkeleton rows={3} />}
        {chats.data?.content.length === 0 && <p className="px-2.5 py-2 text-[13px] text-muted-foreground">No chats yet in this course.</p>}
        {chats.data && <ChatHistory chats={chats.data.content} activeId={chatId} onRename={setRenaming} onDelete={setDeleting} />}
      </div>
    </div>
  );

  return (
    <div className="flex h-full">
      <aside className="hidden w-64 shrink-0 border-r lg:block">{sidebar}</aside>

      {historyOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button aria-label="Close chat list" className="absolute inset-0 bg-black/40" onClick={() => setHistoryOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-72 shadow-pop">{sidebar}</div>
        </div>
      )}

      <section className="flex min-w-0 flex-1 flex-col" aria-label="Conversation">
        <header className="flex h-12 shrink-0 items-center gap-2 border-b px-3">
          <Button variant="ghost" size="icon-sm" className="lg:hidden" aria-label="Open chat list" onClick={() => setHistoryOpen(true)}>
            <Menu />
          </Button>
          <h1 className="min-w-0 flex-1 truncate text-sm font-medium">{chat.data?.title ?? 'New chat'}</h1>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm" disabled={readyDocs.length === 0}>
                <FileText /> <span className="max-w-40 truncate">{docLabel}</span> <ChevronDown />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent className="max-h-80 w-72 overflow-y-auto">
              <DropdownMenuLabel>Search in</DropdownMenuLabel>
              <DropdownMenuCheckboxItem checked={selectedDocs.length === 0} onSelect={(event) => event.preventDefault()} onCheckedChange={() => setSelectedDocs([])}>
                All documents in this course
              </DropdownMenuCheckboxItem>
              <DropdownMenuSeparator />
              {readyDocs.map((document) => (
                <DropdownMenuCheckboxItem
                  key={document.id}
                  checked={selectedDocs.includes(document.id)}
                  onSelect={(event) => event.preventDefault()}
                  onCheckedChange={(checked) =>
                    setSelectedDocs((current) => (checked ? [...current, document.id] : current.filter((id) => id !== document.id)))
                  }
                >
                  <span className="truncate">{document.filename}</span>
                </DropdownMenuCheckboxItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </header>

        <div
          ref={scrollRef}
          onScroll={(event) => {
            const element = event.currentTarget;
            stickToBottom.current = element.scrollHeight - element.scrollTop - element.clientHeight < 80;
          }}
          className="scroll-thin min-h-0 flex-1 overflow-y-auto"
        >
          <div className="mx-auto flex min-h-full max-w-3xl flex-col px-4 py-6">
            {noScopes && (
              <EmptyState
                icon={MessageSquare}
                className="my-auto"
                title="Create a course first"
                description="Chats belong to a course. Create one and upload some notes to begin."
                action={<Button asChild><Link to="/courses">Go to courses</Link></Button>}
              />
            )}
            {messages.isError && <ErrorState message={errorMessage(messages.error)} onRetry={() => messages.refetch()} />}
            {messages.isLoading && <ListSkeleton rows={3} />}

            {conversationEmpty && !noScopes && activeScope && (
              <div className="my-auto py-10 text-center">
                <div className="mx-auto grid size-12 place-items-center rounded-full bg-primary-soft text-primary">
                  <Sparkles className="size-5" />
                </div>
                {readyDocs.length > 0 ? (
                  <>
                    <h2 className="mt-4 text-lg font-semibold tracking-tight">What would you like to learn?</h2>
                    <p className="mt-1 text-sm text-muted-foreground">
                      Answers come only from your {readyDocs.length} processed {readyDocs.length === 1 ? 'document' : 'documents'}, with page references.
                    </p>
                    <div className="mx-auto mt-6 grid max-w-xl gap-2 sm:grid-cols-2">
                      {SUGGESTIONS.map((suggestion) => (
                        <button
                          key={suggestion}
                          onClick={() => handleSend(suggestion)}
                          className="rounded-lg border bg-card p-3 text-left text-sm shadow-card transition-colors hover:border-primary/50 hover:bg-primary-soft/40"
                        >
                          {suggestion}
                        </button>
                      ))}
                    </div>
                  </>
                ) : (
                  <>
                    <h2 className="mt-4 text-lg font-semibold tracking-tight">
                      {processing > 0 ? 'Your notes are being processed' : 'No processed documents yet'}
                    </h2>
                    <p className="mt-1 text-sm text-muted-foreground">
                      {processing > 0 ? 'You can start asking as soon as they are ready.' : 'Upload a PDF to this course to start asking questions.'}
                    </p>
                    {scope.courseId && processing === 0 && (
                      <Button asChild className="mt-4">
                        <Link to={`/courses/${scope.courseId}`}><Upload /> Upload notes</Link>
                      </Button>
                    )}
                  </>
                )}
              </div>
            )}

            <div className="space-y-6">
              {messageList.map((message) =>
                message.role === 'USER' ? (
                  <UserBubble key={message.id} text={message.content} at={message.createdAt} />
                ) : (
                  <AssistantMessage key={message.id} text={message.content} sources={message.sources} at={message.createdAt} onOpenSource={openSource} />
                ),
              )}
              {pending && (
                <>
                  <UserBubble text={pending.question} />
                  <StreamingMessage
                    text={pending.text}
                    sources={pending.sources}
                    stage={pending.stage}
                    error={pending.error}
                    onOpenSource={openSource}
                    onDismissError={dismissError}
                  />
                </>
              )}
            </div>
          </div>
        </div>

        <div className="mx-auto w-full max-w-3xl shrink-0 px-4 pb-4">
          <Composer onSend={handleSend} onStop={stop} busy={busy} disabled={!activeScope || readyDocs.length === 0} />
          <p className="mt-1.5 text-center text-xs text-muted-foreground">Check important answers against the cited page.</p>
        </div>
      </section>

      {viewer && (
        <aside className="fixed inset-0 z-40 bg-background xl:static xl:z-auto xl:w-[44%] xl:shrink-0 xl:border-l" aria-label="Source document">
          <Suspense fallback={null}>
            <PdfViewer
              target={viewer.target}
              onClose={() => setViewer(null)}
              sourceNav={{ index: viewer.index, total: viewer.list.length, onPrev: () => stepSource(-1), onNext: () => stepSource(1) }}
            />
          </Suspense>
        </aside>
      )}

      <RenameDialog chat={renaming} onClose={() => setRenaming(null)} />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete this chat?"
        description={`"${deleting?.title}" and its messages will be permanently removed.`}
        onConfirm={() => (deleting ? removeChat(deleting) : undefined)}
      />
    </div>
  );
}
