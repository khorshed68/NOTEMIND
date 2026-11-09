package com.notemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.notemind.support.AbstractIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Drives the real Spring AI Anthropic client against a local stub of the Messages API wire format. */
@SpringBootTest(properties = {
        "spring.ai.model.chat=anthropic",
        "spring.ai.anthropic.api-key=test-key",
        "spring.ai.anthropic.chat.options.model=stub-model"
})
class AnthropicProviderWireIT extends AbstractIntegrationTest {

    private static final HttpServer SERVER = startServer();

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/messages", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                boolean stream = body.contains("\"stream\":true");
                byte[] bytes = (stream ? streamBody() : messageBody()).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", stream ? "text/event-stream" : "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String messageBody() {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"stub-model","stop_reason":"end_turn",
                "content":[{"type":"text","text":"Tokens [S1]."}],"usage":{"input_tokens":1,"output_tokens":1}}
                """;
    }

    private static String event(String name, String data) {
        return "event: " + name + "\ndata: " + data + "\n\n";
    }

    private static String streamBody() {
        StringBuilder sse = new StringBuilder(event("message_start",
                "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                        + "\"model\":\"stub-model\",\"content\":[],\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}}"));
        sse.append(event("content_block_start",
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"));
        for (String token : List.of("Tok", "ens ", "[S1].")) {
            sse.append(event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,"
                    + "\"delta\":{\"type\":\"text_delta\",\"text\":\"" + token + "\"}}"));
        }
        sse.append(event("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}"));
        sse.append(event("message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},"
                + "\"usage\":{\"output_tokens\":3}}"));
        return sse.append(event("message_stop", "{\"type\":\"message_stop\"}")).toString();
    }

    @DynamicPropertySource
    static void anthropicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.anthropic.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        SERVER.stop(0);
    }

    @Autowired
    private ObjectProvider<ChatModel> chatModel;

    @Test
    void theConfiguredProviderIsActive() {
        assertThat(chatModel.getIfAvailable()).isInstanceOf(org.springframework.ai.anthropic.AnthropicChatModel.class);
    }

    @Test
    void completesThroughTheAnthropicProtocol() {
        assertThat(new SpringAiLlmClient(chatModel).complete("system", List.of(), "question")).isEqualTo("Tokens [S1].");
    }

    @Test
    void streamsThroughTheAnthropicProtocol() {
        String text = new SpringAiLlmClient(chatModel).stream("system", List.of(), "question")
                .collect(Collectors.joining()).block();
        assertThat(text).isEqualTo("Tokens [S1].");
    }
}
