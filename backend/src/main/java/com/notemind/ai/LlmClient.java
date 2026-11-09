package com.notemind.ai;

import java.util.List;
import reactor.core.publisher.Flux;

/** The application's only view of a chat model; providers are selected purely by configuration. */
public interface LlmClient {

    boolean isAvailable();

    String complete(String system, List<ChatTurn> history, String user);

    Flux<String> stream(String system, List<ChatTurn> history, String user);

    record ChatTurn(boolean fromUser, String text) {
    }
}
