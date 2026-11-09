import { getAccessToken, refreshSession } from './api';
import type { SendMessageResponse, SourceRef } from '@/types';

export type StreamStage = 'searching' | 'generating';

export interface StreamHandlers {
  onStage: (stage: StreamStage) => void;
  onSources: (sources: SourceRef[]) => void;
  onToken: (text: string) => void;
  onDone: (result: SendMessageResponse) => void;
  onError: (message: string) => void;
}

interface SseEvent {
  event: string;
  data: string;
}

/** Splits a text/event-stream buffer into complete events plus the unfinished remainder. */
export function parseSseBuffer(buffer: string): { events: SseEvent[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n');
  const blocks = normalized.split('\n\n');
  const rest = blocks.pop() ?? '';
  const events: SseEvent[] = [];
  for (const block of blocks) {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    }
    if (data.length) events.push({ event, data: data.join('\n') });
  }
  return { events, rest };
}

function dispatch({ event, data }: SseEvent, handlers: StreamHandlers) {
  const payload = JSON.parse(data);
  switch (event) {
    case 'status':
      handlers.onStage(payload.stage);
      break;
    case 'sources':
      handlers.onSources(payload);
      break;
    case 'token':
      handlers.onToken(payload.text);
      break;
    case 'done':
      handlers.onDone(payload);
      break;
    case 'error':
      handlers.onError(payload.message);
      break;
  }
}

async function open(chatId: string, body: unknown, signal: AbortSignal): Promise<Response> {
  return fetch(`/api/chats/${chatId}/messages/stream`, {
    method: 'POST',
    credentials: 'include',
    signal,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `Bearer ${getAccessToken()}`,
    },
    body: JSON.stringify(body),
  });
}

/** Uses fetch rather than EventSource, which can neither POST nor send an Authorization header. */
export async function streamMessage(
  chatId: string,
  content: string,
  documentIds: string[],
  handlers: StreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  const body = { content, documentIds };
  let response = await open(chatId, body, signal);
  if (response.status === 401) {
    await refreshSession();
    response = await open(chatId, body, signal);
  }
  if (!response.ok || !response.body) {
    const detail = await response.json().catch(() => null);
    handlers.onError(detail?.message ?? 'Could not get an answer. Please try again.');
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const parsed = parseSseBuffer(buffer);
    buffer = parsed.rest;
    parsed.events.forEach((event) => dispatch(event, handlers));
  }
}
