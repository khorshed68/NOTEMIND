package com.notemind.ai;

import com.notemind.ai.LlmClient.ChatTurn;
import com.notemind.dto.ChatDtos.SourceRef;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final Pattern BENGALI = Pattern.compile("\\p{IsBengali}");
    private static final int MAX_REWRITE_LENGTH = 400;
    private static final int MAX_EXTRACT_LENGTH = 600;
    private static final int MAX_REFUSAL_LENGTH = 450;
    private static final int REFUSAL_OPENING = 170;
    private static final Pattern LABEL = Pattern.compile("\\[S\\d+\\]");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d{2,}\\b");
    private static final Pattern REFUSAL_PHRASE = Pattern.compile(
            "(do|does|did) not (explicitly |directly |specifically )?(mention|contain|state|provide|include|specify|say|cover)"
                    + "|not (explicitly |directly |specifically )?(mentioned|found|specified|stated|provided|covered)"
                    + "|(cannot|can't|unable to) (provide|find|answer|determine)|no information");
    private static final Pattern REFUSAL_SUBJECT = Pattern.compile("excerpt|context|passage|notes|provided|answer");

    private final RetrievalService retrieval;
    private final LlmClient llm;
    private final CitationValidator citations;

    public RagService(RetrievalService retrieval, LlmClient llm, CitationValidator citations) {
        this.retrieval = retrieval;
        this.llm = llm;
        this.citations = citations;
    }

    public enum Mode {
        /** The LLM composes an answer from the passages. */
        GENERATE,
        /** The answer is already final: nothing relevant was found, or no LLM is configured. */
        FINAL
    }

    /** Result of the retrieval phase. {@code finalAnswer} is set only in {@link Mode#FINAL}. */
    public record Prepared(Mode mode, String question, List<ChatTurn> history, List<RetrievedChunk> chunks,
                           List<SourceRef> retrievedSources, CitationValidator.Result finalAnswer) {
    }

    public Prepared prepare(RetrievalScope scope, String question, List<ChatTurn> history) {
        if (!retrieval.hasSearchableDocuments(scope)) {
            return finalAnswer(question, history, List.of(), new CitationValidator.Result(
                    "There are no processed documents to search yet. Upload a PDF, or wait for processing to finish, "
                            + "and then ask again.", List.of()));
        }
        List<RetrievedChunk> chunks = retrieval.retrieve(scope, searchQuery(question, history));
        if (chunks.isEmpty()) {
            return finalAnswer(question, history, chunks, notFound(question));
        }
        if (!llm.isAvailable()) {
            return finalAnswer(question, history, chunks, extractive(chunks));
        }
        return new Prepared(Mode.GENERATE, question, history, chunks, describe(chunks), null);
    }

    public CitationValidator.Result generate(Prepared prepared) {
        String raw = llm.complete(RagPrompts.SYSTEM_PROMPT, prepared.history(),
                RagPrompts.answerPrompt(prepared.question(), prepared.chunks()));
        return finish(raw, prepared);
    }

    public Flux<String> stream(Prepared prepared) {
        return llm.stream(RagPrompts.SYSTEM_PROMPT, prepared.history(),
                RagPrompts.answerPrompt(prepared.question(), prepared.chunks()));
    }

    /** Validates the model's complete output, whether it was received whole or streamed. */
    public CitationValidator.Result finish(String raw, Prepared prepared) {
        if (raw == null || raw.isBlank() || raw.contains(RagPrompts.NOT_FOUND_SENTINEL) || isRefusal(raw)
                || hasUngroundedNumber(raw, prepared.question(), prepared.chunks())) {
            return notFound(prepared.question());
        }
        return citations.validate(raw, prepared.chunks());
    }

    /**
     * Models often decline in their own words instead of using the marker. Only a short reply that
     * opens by saying the supplied material lacks the answer counts, so real answers are untouched.
     */
    static boolean isRefusal(String answer) {
        String text = answer.strip();
        if (text.length() > MAX_REFUSAL_LENGTH) {
            return false;
        }
        String opening = text.substring(0, Math.min(text.length(), REFUSAL_OPENING)).toLowerCase(java.util.Locale.ROOT);
        return REFUSAL_PHRASE.matcher(opening).find() && REFUSAL_SUBJECT.matcher(opening).find();
    }

    /**
     * A short answer stating a figure found in neither the passages nor the question came from the
     * model's own memory, the classic way a fact gets invented and then cited. Longer explanations
     * are not rejected on this basis, since one stray number should not discard a whole answer.
     */
    static boolean hasUngroundedNumber(String answer, String question, List<RetrievedChunk> chunks) {
        String text = LABEL.matcher(answer).replaceAll(" ").strip();
        if (text.length() > MAX_REFUSAL_LENGTH) {
            return false;
        }
        StringBuilder known = new StringBuilder(question);
        chunks.forEach(chunk -> known.append(' ').append(chunk.content()));
        var available = new java.util.HashSet<String>();
        NUMBER.matcher(known).results().forEach(match -> available.add(match.group()));
        return NUMBER.matcher(text).results().anyMatch(match -> !available.contains(match.group()));
    }

    public CitationValidator.Result notFound(String question) {
        return new CitationValidator.Result(
                BENGALI.matcher(question).find() ? RagPrompts.NOT_FOUND_MESSAGE_BN : RagPrompts.NOT_FOUND_MESSAGE,
                List.of());
    }

    /**
     * Rewrites follow-ups ("explain that again") and non-English questions into a standalone English
     * query, because embedding the raw text would retrieve the wrong passages. Falls back to the
     * original text if the model is unavailable or fails.
     */
    private String searchQuery(String question, List<ChatTurn> history) {
        boolean needsRewrite = !history.isEmpty() || BENGALI.matcher(question).find();
        if (!needsRewrite || !llm.isAvailable()) {
            return question;
        }
        try {
            StringBuilder conversation = new StringBuilder();
            for (ChatTurn turn : history) {
                conversation.append(turn.fromUser() ? "Student: " : "Assistant: ").append(turn.text()).append('\n');
            }
            conversation.append("Student (latest): ").append(question);
            String rewritten = llm.complete(RagPrompts.REWRITE_SYSTEM_PROMPT, List.of(), conversation.toString());
            if (rewritten == null || rewritten.isBlank() || rewritten.length() > MAX_REWRITE_LENGTH) {
                return question;
            }
            return rewritten.strip();
        } catch (RuntimeException ex) {
            log.warn("Query rewrite failed; searching with the original question: {}", ex.getMessage());
            return question;
        }
    }

    /** With no LLM configured, show the real passages rather than inventing a synthesized answer. */
    private CitationValidator.Result extractive(List<RetrievedChunk> chunks) {
        StringBuilder text = new StringBuilder(
                "**No AI model is configured on this server**, so this is not a written answer. "
                        + "These are the most relevant passages from your notes:\n");
        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i).content().replaceAll("\\s+", " ").strip();
            if (content.length() > MAX_EXTRACT_LENGTH) {
                content = content.substring(0, MAX_EXTRACT_LENGTH).strip() + "…";
            }
            text.append("\n> ").append(content).append(" [S").append(i + 1).append("]\n");
        }
        return citations.validate(text.toString(), chunks);
    }

    private Prepared finalAnswer(String question, List<ChatTurn> history, List<RetrievedChunk> chunks,
                                 CitationValidator.Result result) {
        return new Prepared(Mode.FINAL, question, history, chunks, describe(chunks), result);
    }

    private List<SourceRef> describe(List<RetrievedChunk> chunks) {
        List<SourceRef> sources = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            sources.add(citations.toSource(i + 1, chunks.get(i), false));
        }
        return sources;
    }
}
