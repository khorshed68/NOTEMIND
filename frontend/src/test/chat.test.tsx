import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AssistantMessage, linkCitations } from '@/components/chat/ChatMessage';
import { ChatPage } from '@/pages/ChatPage';
import type { StreamHandlers } from '@/services/chatStream';
import type { DocumentItem, SendMessageResponse, SourceRef } from '@/types';
import { page, renderApp } from './utils';

const api = vi.hoisted(() => ({
  courses: vi.fn(),
  folders: vi.fn(),
  documents: vi.fn(),
  chats: vi.fn(),
  create: vi.fn(),
  stream: vi.fn(),
}));

vi.mock('@/services/endpoints', () => ({
  courseApi: { list: api.courses },
  folderApi: { list: api.folders },
  documentApi: { list: api.documents },
  chatApi: { list: api.chats, create: api.create, get: vi.fn(), messages: vi.fn(), rename: vi.fn(), remove: vi.fn() },
}));
vi.mock('@/services/chatStream', () => ({ streamMessage: api.stream }));
vi.mock('@/components/pdf/PdfViewer', () => ({
  PdfViewer: ({ target }: { target: { documentName: string; page: number; highlight?: string } }) => (
    <div data-testid="viewer">
      {target.documentName} at page {target.page}: {target.highlight}
    </div>
  ),
}));

const source: SourceRef = {
  label: 'S1', documentId: 'd1', documentName: 'Compiler Design.pdf', pageNumber: 12, chunkId: 'k1',
  snippet: 'Lexical analysis is the first phase.', similarity: 0.8, cited: true,
};

describe('citation rendering', () => {
  it('turns markers into links', () => {
    expect(linkCitations('Tokens [S1].')).toBe('Tokens  [S1](#cite-S1).');
  });

  it('shows a clickable page chip and a source card', async () => {
    const open = vi.fn();
    render(<AssistantMessage text="It is the first phase [S1]." sources={[source]} onOpenSource={open} />);

    expect(screen.getByText('Compiler Design.pdf — Page 12')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Open Compiler Design.pdf at page 12' }));
    expect(open).toHaveBeenCalledWith(source, [source]);
  });

  it('opens the source from its card too', async () => {
    const open = vi.fn();
    render(<AssistantMessage text="First phase [S1]." sources={[source]} onOpenSource={open} />);
    await userEvent.click(screen.getByText('Compiler Design.pdf — Page 12'));
    expect(open).toHaveBeenCalledTimes(1);
  });

  it('does not render a chip for a label with no source', () => {
    render(<AssistantMessage text="Claim [S9]." sources={[source]} onOpenSource={vi.fn()} />);
    expect(screen.queryByRole('button', { name: /at page/ })).not.toBeInTheDocument();
  });

  it('labels uncited passages honestly', () => {
    render(<AssistantMessage text="No citation." sources={[{ ...source, cited: false }]} onOpenSource={vi.fn()} />);
    expect(screen.getByText(/did not cite these directly/)).toBeInTheDocument();
  });

  it('presents the not-found message without sources', () => {
    render(
      <AssistantMessage
        text="I couldn't find this information in your uploaded notes. Try uploading additional lecture materials or asking a different question."
        sources={[]}
        onOpenSource={vi.fn()}
      />,
    );
    expect(screen.getByText(/couldn't find this information/)).toBeInTheDocument();
    expect(screen.queryByText('Sources')).not.toBeInTheDocument();
  });
});

describe('ChatPage', () => {
  const doc: DocumentItem = {
    id: 'd1', courseId: 'c1', folderId: null, filename: 'Compiler Design.pdf', fileSize: 1, pageCount: 20, status: 'READY',
    errorMessage: null, canManage: true, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
  };

  const result: SendMessageResponse = {
    userMessage: { id: 'm1', chatId: 'new', role: 'USER', content: 'What is lexing?', sources: [], createdAt: '2026-01-01T00:00:00Z' },
    assistantMessage: { id: 'm2', chatId: 'new', role: 'ASSISTANT', content: 'It makes tokens [S1].', sources: [source], createdAt: '2026-01-01T00:00:00Z' },
  };

  const routes = (
    <Routes>
      <Route path="/chat/:chatId?" element={<ChatPage />} />
    </Routes>
  );

  beforeEach(() => {
    Object.values(api).forEach((mock) => mock.mockReset());
    api.courses.mockResolvedValue(page([{ id: 'c1', name: 'Compilers', courseCode: 'CSE 3101' }]));
    api.folders.mockResolvedValue([]);
    api.documents.mockResolvedValue(page([doc]));
    api.chats.mockResolvedValue(page([]));
    api.create.mockResolvedValue({ id: 'new', courseId: 'c1', folderId: null, title: 'New chat' });
  });

  it('offers suggested questions once documents are ready', async () => {
    renderApp(routes, '/chat');
    expect(await screen.findByText('Summarize chapter 2.')).toBeInTheDocument();
  });

  it('streams an answer, then opens the cited page with its highlight', async () => {
    api.stream.mockImplementation(async (_id: string, _q: string, _docs: string[], handlers: StreamHandlers) => {
      handlers.onStage('searching');
      handlers.onSources([source]);
      handlers.onToken('It makes ');
      handlers.onToken('tokens [S1].');
      handlers.onDone(result);
    });
    renderApp(routes, '/chat');
    await screen.findByText('Summarize chapter 2.');

    await userEvent.type(screen.getByLabelText('Message'), 'What is lexing?{Enter}');

    await waitFor(() => expect(api.create).toHaveBeenCalledWith({ courseId: 'c1' }));
    expect(api.stream).toHaveBeenCalledWith('new', 'What is lexing?', [], expect.anything(), expect.anything());

    await userEvent.click(await screen.findByRole('button', { name: 'Open Compiler Design.pdf at page 12' }));
    expect(await screen.findByTestId('viewer')).toHaveTextContent('Compiler Design.pdf at page 12: Lexical analysis is the first phase.');
  });

  it('shows a loading state, then allows stopping', async () => {
    let captured: AbortSignal | undefined;
    api.stream.mockImplementation((_id: string, _q: string, _d: string[], handlers: StreamHandlers, signal: AbortSignal) => {
      captured = signal;
      handlers.onStage('searching');
      return new Promise(() => undefined);
    });
    renderApp(routes, '/chat');
    await screen.findByText('Summarize chapter 2.');
    await userEvent.type(screen.getByLabelText('Message'), 'Slow question{Enter}');

    expect(await screen.findByText('Searching your notes…')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Stop generating' }));
    expect(captured?.aborted).toBe(true);
  });

  it('surfaces stream errors', async () => {
    api.stream.mockImplementation(async (_id: string, _q: string, _d: string[], handlers: StreamHandlers) => {
      handlers.onError('The AI service is unavailable right now.');
    });
    renderApp(routes, '/chat');
    await screen.findByText('Summarize chapter 2.');
    await userEvent.type(screen.getByLabelText('Message'), 'Hello{Enter}');
    expect(await screen.findByRole('alert')).toHaveTextContent('The AI service is unavailable right now.');
  });

  it('prompts for an upload when no document is processed', async () => {
    api.documents.mockResolvedValue(page([]));
    renderApp(routes, '/chat');
    expect(await screen.findByText('No processed documents yet')).toBeInTheDocument();
    expect(screen.getByLabelText('Message')).toBeDisabled();
  });
});
