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

/**
 * Drives the real Spring AI OpenAI client against a local stub that speaks the OpenAI wire format.
 * Proves configuration, request building and SSE parsing; it says nothing about a real model's quality.
 */
@SpringBootTest(properties = {
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.chat.options.model=stub-model"
})
class OpenAiProviderWireIT extends AbstractIntegrationTest {

    private static final HttpServer SERVER = startServer();

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                boolean stream = body.contains("\"stream\":true");
                String response = stream ? streamBody() : completionBody();
                exchange.getResponseHeaders().add("Content-Type", stream ? "text/event-stream" : "application/json");
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
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

    private static String completionBody() {
        return """
                {"id":"1","object":"chat.completion","created":1,"model":"stub-model","choices":[
                {"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"Tokens [S1]."}}],
                "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """;
    }

    private static String streamBody() {
        StringBuilder sse = new StringBuilder();
        for (String token : List.of("Tok", "ens ", "[S1].")) {
            sse.append("data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"stub-model\",")
                    .append("\"choices\":[{\"index\":0,\"delta\":{\"content\":\"").append(token)
                    .append("\"},\"finish_reason\":null}]}\n\n");
        }
        return sse.append("data: [DONE]\n\n").toString();
    }

    @DynamicPropertySource
    static void openAiProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        SERVER.stop(0);
    }

    // The base class mocks LlmClient, so the real adapter is built directly on the auto-configured model.
    @Autowired
    private ObjectProvider<ChatModel> chatModel;

    @Test
    void theConfiguredProviderIsActive() {
        assertThat(chatModel.getIfAvailable()).isInstanceOf(org.springframework.ai.openai.OpenAiChatModel.class);
    }

    @Test
    void completesThroughTheOpenAiProtocol() {
        SpringAiLlmClient client = new SpringAiLlmClient(chatModel);
        assertThat(client.isAvailable()).isTrue();
        assertThat(client.complete("system", List.of(), "question")).isEqualTo("Tokens [S1].");
    }

    @Test
    void streamsThroughTheOpenAiProtocol() {
        String text = new SpringAiLlmClient(chatModel).stream("system", List.of(), "question")
                .collect(Collectors.joining()).block();
        assertThat(text).isEqualTo("Tokens [S1].");
    }
}
