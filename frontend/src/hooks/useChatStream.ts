import { useCallback, useEffect, useRef, useState } from 'react';
import { errorMessage } from '@/services/api';
import { streamMessage, type StreamStage } from '@/services/chatStream';
import type { SendMessageResponse, SourceRef } from '@/types';

export interface PendingExchange {
  question: string;
  text: string;
  sources: SourceRef[];
  stage: StreamStage;
  error?: string;
}

interface Callbacks {
  onComplete: (chatId: string, result: SendMessageResponse) => void;
  /** The user pressed Stop; the partial answer is kept on screen. */
  onStopped: (chatId: string, exchange: PendingExchange) => void;
}

export function useChatStream({ onComplete, onStopped }: Callbacks) {
  const [pending, setPending] = useState<PendingExchange | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const latest = useRef<PendingExchange | null>(null);
  const chatRef = useRef<string | null>(null);
  const callbacks = useRef({ onComplete, onStopped });
  callbacks.current = { onComplete, onStopped };

  const update = useCallback((change: (current: PendingExchange) => PendingExchange) => {
    if (!latest.current) return;
    latest.current = change(latest.current);
    setPending(latest.current);
  }, []);

  const send = useCallback(
    async (chatId: string, question: string, documentIds: string[]) => {
      const controller = new AbortController();
      controllerRef.current = controller;
      chatRef.current = chatId;
      latest.current = { question, text: '', sources: [], stage: 'searching' };
      setPending(latest.current);
      try {
        await streamMessage(
          chatId,
          question,
          documentIds,
          {
            onStage: (stage) => update((current) => ({ ...current, stage })),
            onSources: (sources) => update((current) => ({ ...current, sources })),
            onToken: (text) => update((current) => ({ ...current, text: current.text + text })),
            onDone: (result) => {
              latest.current = null;
              setPending(null);
              callbacks.current.onComplete(chatId, result);
            },
            onError: (message) => update((current) => ({ ...current, error: message })),
          },
          controller.signal,
        );
      } catch (error) {
        if (controller.signal.aborted) return;
        update((current) => ({ ...current, error: errorMessage(error) }));
      }
    },
    [update],
  );

  const stop = useCallback(() => {
    controllerRef.current?.abort();
    const exchange = latest.current;
    latest.current = null;
    setPending(null);
    if (exchange && exchange.text && chatRef.current) callbacks.current.onStopped(chatRef.current, exchange);
  }, []);

  const dismissError = useCallback(() => {
    latest.current = null;
    setPending(null);
  }, []);

  useEffect(() => () => controllerRef.current?.abort(), []);

  return { pending, send, stop, dismissError };
}
