package com.notemind.service;

import com.notemind.ai.CitationValidator;
import com.notemind.ai.LlmClient.ChatTurn;
import com.notemind.ai.RagService;
import com.notemind.ai.RetrievalScope;
import com.notemind.config.AppProperties;
import com.notemind.dto.ChatDtos.ChatDto;
import com.notemind.dto.ChatDtos.CreateChatRequest;
import com.notemind.dto.ChatDtos.MessageDto;
import com.notemind.dto.ChatDtos.SendMessageRequest;
import com.notemind.dto.ChatDtos.SendMessageResponse;
import com.notemind.dto.ChatDtos.SourceRef;
import com.notemind.dto.PageResponse;
import com.notemind.entity.Chat;
import com.notemind.entity.Enums.MessageRole;
import com.notemind.entity.Message;
import com.notemind.exception.ApiException;
import com.notemind.repository.ChatRepository;
import com.notemind.repository.MessageRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChatService {

    private static final int MAX_HISTORY_CHARS = 1500;
    private static final int MAX_TITLE_LENGTH = 60;
    private static final String DEFAULT_TITLE = "New chat";

    private final ChatRepository chats;
    private final MessageRepository messages;
    private final AccessControlService access;
    private final RagService rag;
    private final AppProperties.Rag config;

    public ChatService(ChatRepository chats, MessageRepository messages, AccessControlService access, RagService rag,
                       AppProperties properties) {
        this.chats = chats;
        this.messages = messages;
        this.access = access;
        this.rag = rag;
        this.config = properties.rag();
    }

    @Transactional
    public ChatDto create(UUID userId, CreateChatRequest request) {
        if ((request.courseId() == null) == (request.folderId() == null)) {
            throw ApiException.badRequest("A chat must belong to exactly one course or shared folder.");
        }
        if (request.courseId() != null) {
            access.requireCourse(userId, request.courseId());
        } else {
            access.requireFolderRead(userId, request.folderId());
        }
        Chat chat = new Chat();
        chat.setUserId(userId);
        chat.setCourseId(request.courseId());
        chat.setFolderId(request.folderId());
        chat.setTitle(StringUtils.hasText(request.title()) ? request.title().trim() : DEFAULT_TITLE);
        return ChatDto.from(chats.saveAndFlush(chat));
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatDto> list(UUID userId, UUID courseId, UUID folderId, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "updatedAt"));
        var result = courseId != null ? chats.findByUserIdAndCourseId(userId, courseId, pageable)
                : folderId != null ? chats.findByUserIdAndFolderId(userId, folderId, pageable)
                : chats.findByUserId(userId, pageable);
        return PageResponse.of(result, ChatDto::from);
    }

    public ChatDto get(UUID userId, UUID chatId) {
        return ChatDto.from(requireChat(userId, chatId));
    }

    @Transactional
    public ChatDto rename(UUID userId, UUID chatId, String title) {
        Chat chat = requireChat(userId, chatId);
        chat.setTitle(title.trim());
        chat.setUpdatedAt(Instant.now());
        return ChatDto.from(chat);
    }

    @Transactional
    public void delete(UUID userId, UUID chatId) {
        chats.delete(requireChat(userId, chatId));
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageDto> messages(UUID userId, UUID chatId, int page, int size) {
        requireChat(userId, chatId);
        var pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.ASC, "createdAt"));
        return PageResponse.of(messages.findByChatId(chatId, pageable), MessageDto::from);
    }

    public SendMessageResponse send(UUID userId, UUID chatId, SendMessageRequest request) {
        Chat chat = requireChat(userId, chatId);
        String question = request.content().strip();
        RagService.Prepared prepared = rag.prepare(scopeOf(chat, request), question, recentHistory(chatId));
        CitationValidator.Result result = prepared.mode() == RagService.Mode.FINAL
                ? prepared.finalAnswer()
                : rag.generate(prepared);
        return persistExchange(chat, question, result.text(), result.sources());
    }

    /** Authorizes up front so a denied request gets a normal HTTP error rather than a broken stream. */
    public Chat requireChat(UUID userId, UUID chatId) {
        return chats.findByIdAndUserId(chatId, userId).orElseThrow(() -> ApiException.notFound("Chat"));
    }

    public RetrievalScope scopeOf(Chat chat, SendMessageRequest request) {
        return new RetrievalScope(chat.getUserId(), chat.getCourseId(), chat.getFolderId(), request.documentIds());
    }

    public List<ChatTurn> recentHistory(UUID chatId) {
        if (config.historyMessages() == 0) {
            return List.of();
        }
        List<Message> latest = messages.findByChatIdOrderByCreatedAtDesc(chatId,
                PageRequest.of(0, config.historyMessages()));
        List<ChatTurn> turns = new ArrayList<>(latest.size());
        for (Message message : latest) {
            String text = message.getContent().replaceAll("\\[S\\d+\\]", "");
            if (text.length() > MAX_HISTORY_CHARS) {
                text = text.substring(0, MAX_HISTORY_CHARS);
            }
            turns.add(new ChatTurn(message.getRole() == MessageRole.USER, text));
        }
        Collections.reverse(turns);
        return turns;
    }

    /** Messages are stored only once an answer exists, so failed requests leave no half-exchange behind. */
    public SendMessageResponse persistExchange(Chat chat, String question, String answer, List<SourceRef> sources) {
        Message user = save(chat.getId(), MessageRole.USER, question, null);
        Message assistant = save(chat.getId(), MessageRole.ASSISTANT, answer, sources);
        Instant now = Instant.now();
        if (DEFAULT_TITLE.equals(chat.getTitle())) {
            chats.updateTitle(chat.getId(), titleFrom(question), now);
        } else {
            chats.touch(chat.getId(), now);
        }
        return new SendMessageResponse(MessageDto.from(user), MessageDto.from(assistant));
    }

    private Message save(UUID chatId, MessageRole role, String content, List<SourceRef> sources) {
        Message message = new Message();
        message.setChatId(chatId);
        message.setRole(role);
        message.setContent(content);
        message.setSources(sources);
        return messages.saveAndFlush(message);
    }

    private static String titleFrom(String question) {
        String flat = question.replaceAll("\\s+", " ").strip();
        return flat.length() <= MAX_TITLE_LENGTH ? flat : flat.substring(0, MAX_TITLE_LENGTH - 1).strip() + "…";
    }
}
