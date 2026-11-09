package com.notemind.dto;

import com.notemind.entity.Chat;
import com.notemind.entity.Enums.MessageRole;
import com.notemind.entity.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record CreateChatRequest(UUID courseId, UUID folderId, @Size(max = 200) String title) {
    }

    public record RenameChatRequest(@NotBlank(message = "Title is required") @Size(max = 200) String title) {
    }

    /** {@code documentIds} empty or null means "search every document in the chat's course or folder". */
    public record SendMessageRequest(
            @NotBlank(message = "Message is required") @Size(max = 4000) String content,
            @Size(max = 200) List<UUID> documentIds) {
    }

    public record SourceRef(String label, UUID documentId, String documentName, int pageNumber, UUID chunkId,
                            String snippet, double similarity, boolean cited) {
    }

    public record ChatDto(UUID id, UUID courseId, UUID folderId, String title, Instant createdAt, Instant updatedAt) {
        public static ChatDto from(Chat chat) {
            return new ChatDto(chat.getId(), chat.getCourseId(), chat.getFolderId(), chat.getTitle(),
                    chat.getCreatedAt(), chat.getUpdatedAt());
        }
    }

    public record MessageDto(UUID id, UUID chatId, MessageRole role, String content, List<SourceRef> sources,
                             Instant createdAt) {
        public static MessageDto from(Message message) {
            return new MessageDto(message.getId(), message.getChatId(), message.getRole(), message.getContent(),
                    message.getSources() == null ? List.of() : message.getSources(), message.getCreatedAt());
        }
    }

    public record SendMessageResponse(MessageDto userMessage, MessageDto assistantMessage) {
    }
}
