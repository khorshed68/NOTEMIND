package com.notemind.ai;

import com.notemind.dto.ChatDtos.SendMessageRequest;
import com.notemind.dto.ChatDtos.SendMessageResponse;
import com.notemind.entity.Chat;
import com.notemind.exception.ApiException;
import com.notemind.service.ChatService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/**
 * Runs one question over Server-Sent Events.
 * Event order: {@code status}, {@code sources}, any number of {@code token}, then {@code done} or {@code error}.
 */
@Service
public class ChatStreamer {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamer.class);
    private static final long TIMEOUT_MS = 280_000;

    private final ChatService chats;
    private final RagService rag;
    private final TaskExecutor executor;

    public ChatStreamer(ChatService chats, RagService rag, @Qualifier("aiExecutor") TaskExecutor executor) {
        this.chats = chats;
        this.rag = rag;
        this.executor = executor;
    }

    public SseEmitter stream(UUID userId, UUID chatId, SendMessageRequest request) {
        Chat chat = chats.requireChat(userId, chatId);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Session session = new Session(chat, request, emitter);
        emitter.onTimeout(session::stop);
        emitter.onError(error -> session.stop());
        emitter.onCompletion(session::stop);
        executor.execute(session::run);
        return emitter;
    }

    /** All state for one streamed answer; exactly one of finish, fail or stop takes effect. */
    private final class Session {
        private final Chat chat;
        private final SendMessageRequest request;
        private final SseEmitter emitter;
        private final String question;
        private final StringBuilder raw = new StringBuilder();
        private final AtomicBoolean settled = new AtomicBoolean();
        private final SentinelGate gate = new SentinelGate();
        private volatile Disposable subscription;
        private volatile RagService.Prepared prepared;

        Session(Chat chat, SendMessageRequest request, SseEmitter emitter) {
            this.chat = chat;
            this.request = request;
            this.emitter = emitter;
            this.question = request.content().strip();
        }

        void run() {
            try {
                send("status", Map.of("stage", "searching"));
                prepared = rag.prepare(chats.scopeOf(chat, request), question, chats.recentHistory(chat.getId()));
                send("sources", prepared.retrievedSources());
                if (prepared.mode() == RagService.Mode.FINAL) {
                    send("token", Map.of("text", prepared.finalAnswer().text()));
                    finish(prepared.finalAnswer());
                    return;
                }
                send("status", Map.of("stage", "generating"));
                subscription = rag.stream(prepared).subscribe(this::onToken, this::fail, this::onComplete);
                if (settled.get()) {
                    subscription.dispose();
                }
            } catch (RuntimeException ex) {
                fail(ex);
            }
        }

        private void onToken(String token) {
            raw.append(token);
            String visible = gate.accept(token);
            if (!visible.isEmpty()) {
                send("token", Map.of("text", visible));
            }
        }

        private void onComplete() {
            finish(rag.finish(raw.toString(), prepared));
        }

        private void finish(CitationValidator.Result result) {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            try {
                SendMessageResponse saved = chats.persistExchange(chat, question, result.text(), result.sources());
                emitter.send(SseEmitter.event().name("done").data(saved, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException | IllegalStateException ex) {
                log.debug("Client left before the final event", ex);
            }
        }

        private void fail(Throwable error) {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            log.warn("Streaming answer failed for chat {}: {}", chat.getId(), error.toString());
            String message = error instanceof ApiException api ? api.getMessage()
                    : "The AI service is unavailable right now. Please try again in a moment.";
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", message),
                        MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException | IllegalStateException ex) {
                log.debug("Client left before the error event", ex);
            }
        }

        /** Client disconnected or pressed Stop: cancel the model call and keep whatever was generated. */
        void stop() {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            Disposable active = subscription;
            if (active != null) {
                active.dispose();
            }
            if (prepared != null && !raw.isEmpty() && !raw.toString().isBlank()) {
                CitationValidator.Result partial = rag.finish(raw.toString(), prepared);
                chats.persistExchange(chat, question, partial.text(),
                        partial.sources() == null ? List.of() : partial.sources());
            }
        }

        private void send(String name, Object data) {
            if (settled.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                stop();
            }
        }
    }
}
