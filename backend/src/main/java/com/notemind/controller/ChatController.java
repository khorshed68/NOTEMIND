package com.notemind.controller;

import com.notemind.ai.ChatStreamer;
import com.notemind.dto.ChatDtos.ChatDto;
import com.notemind.dto.ChatDtos.CreateChatRequest;
import com.notemind.dto.ChatDtos.MessageDto;
import com.notemind.dto.ChatDtos.RenameChatRequest;
import com.notemind.dto.ChatDtos.SendMessageRequest;
import com.notemind.dto.ChatDtos.SendMessageResponse;
import com.notemind.dto.PageResponse;
import com.notemind.security.AuthenticatedUser;
import com.notemind.service.ChatService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chats;
    private final ChatStreamer streamer;

    public ChatController(ChatService chats, ChatStreamer streamer) {
        this.chats = chats;
        this.streamer = streamer;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatDto create(@AuthenticationPrincipal AuthenticatedUser user,
                          @Valid @RequestBody CreateChatRequest request) {
        return chats.create(user.id(), request);
    }

    @GetMapping
    public PageResponse<ChatDto> list(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestParam(required = false) UUID courseId,
                                      @RequestParam(required = false) UUID folderId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return chats.list(user.id(), courseId, folderId, Math.max(page, 0), Math.max(size, 1));
    }

    @GetMapping("/{id}")
    public ChatDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return chats.get(user.id(), id);
    }

    @PutMapping("/{id}")
    public ChatDto rename(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                          @Valid @RequestBody RenameChatRequest request) {
        return chats.rename(user.id(), id, request.title());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        chats.delete(user.id(), id);
    }

    @GetMapping("/{id}/messages")
    public PageResponse<MessageDto> messages(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "200") int size) {
        return chats.messages(user.id(), id, Math.max(page, 0), Math.max(size, 1));
    }

    @PostMapping("/{id}/messages")
    public SendMessageResponse send(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                    @Valid @RequestBody SendMessageRequest request) {
        return chats.send(user.id(), id, request);
    }

    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                             @Valid @RequestBody SendMessageRequest request, HttpServletResponse response) {
        // Tells the bundled nginx not to buffer, so tokens reach the browser as they are produced.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return streamer.stream(user.id(), id, request);
    }
}
