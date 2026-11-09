package com.notemind.ai;

import com.notemind.exception.ApiException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class SpringAiLlmClient implements LlmClient {

    private final ObjectProvider<ChatModel> chatModel;

    public SpringAiLlmClient(ObjectProvider<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public boolean isAvailable() {
        return chatModel.getIfAvailable() != null;
    }

    @Override
    public String complete(String system, List<ChatTurn> history, String user) {
        ChatResponse response = model().call(new Prompt(messages(system, history, user)));
        return text(response);
    }

    @Override
    public Flux<String> stream(String system, List<ChatTurn> history, String user) {
        return model().stream(new Prompt(messages(system, history, user)))
                .map(SpringAiLlmClient::text)
                .filter(token -> !token.isEmpty());
    }

    private ChatModel model() {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw ApiException.aiUnavailable("No AI chat model is configured on this server.");
        }
        return model;
    }

    /** System instructions, history and retrieved text are always distinct messages, never concatenated. */
    private static List<Message> messages(String system, List<ChatTurn> history, String user) {
        List<Message> messages = new ArrayList<>(history.size() + 2);
        messages.add(new SystemMessage(system));
        for (ChatTurn turn : history) {
            messages.add(turn.fromUser() ? new UserMessage(turn.text()) : new AssistantMessage(turn.text()));
        }
        messages.add(new UserMessage(user));
        return messages;
    }

    private static String text(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
