package com.notemind.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.ai.EmbeddingClient;
import com.notemind.ai.LlmClient;
import com.notemind.entity.Enums.DocumentStatus;
import com.notemind.repository.DocumentRepository;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Runs against a real PostgreSQL + pgvector container, with only the AI providers replaced. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.TestAiConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final int TEST_DIMENSION = 384;
    protected static final String PASSWORD = "Sup3rSecret!";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("notemind.storage.root-dir",
                () -> uncheckedTemp().toString());
    }

    private static java.nio.file.Path uncheckedTemp() {
        try {
            return Files.createTempDirectory("notemind-it");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @TestConfiguration
    static class TestAiConfig {
        @Bean
        @Primary
        EmbeddingClient testEmbeddingClient() {
            return new HashingEmbeddingClient(TEST_DIMENSION);
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected DocumentRepository documents;

    @MockitoBean
    protected LlmClient llm;

    @BeforeEach
    void defaultLlm() {
        reset(llm);
        when(llm.isAvailable()).thenReturn(true);
        when(llm.complete(anyString(), anyList(), anyString())).thenReturn("Unstubbed answer.");
    }

    /** A freshly registered user with a valid access token. */
    protected record Session(UUID id, String email, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    protected Session register() throws Exception {
        String email = "user" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID() + "@example.com";
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Test User", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return new Session(UUID.fromString(body.path("user").path("id").asText()), email,
                body.path("accessToken").asText());
    }

    protected UUID createCourse(Session session, String name) throws Exception {
        var result = mvc.perform(post("/api/courses").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "courseCode", "CSE 3101"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    protected UUID upload(Session session, UUID courseId, String filename, byte[] pdf) throws Exception {
        var result = mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", filename, "application/pdf", pdf))
                        .param("courseId", courseId.toString())
                        .header("Authorization", session.bearer()))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    protected UUID uploadAndAwaitReady(Session session, UUID courseId, String filename, byte[] pdf) throws Exception {
        UUID id = upload(session, courseId, filename, pdf);
        assertThat(awaitTerminal(id)).isEqualTo(DocumentStatus.READY);
        return id;
    }

    protected DocumentStatus awaitTerminal(UUID documentId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            DocumentStatus status = documents.findById(documentId).orElseThrow().getStatus();
            if (status == DocumentStatus.READY || status == DocumentStatus.FAILED) {
                return status;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Document did not finish processing in time");
    }

    protected UUID createChat(Session session, UUID courseId) throws Exception {
        var result = mvc.perform(post("/api/chats").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("courseId", courseId.toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    protected JsonNode getJson(String path, Session session) throws Exception {
        var result = mvc.perform(get(path).header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
