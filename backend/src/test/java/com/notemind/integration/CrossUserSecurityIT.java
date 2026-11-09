package com.notemind.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notemind.ai.EmbeddingClient;
import com.notemind.ai.RetrievalScope;
import com.notemind.ai.RetrievedChunk;
import com.notemind.repository.ChunkRepository;
import com.notemind.support.AbstractIntegrationTest;
import com.notemind.support.TestPdfs;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** Proves the multi-tenant isolation rules at the HTTP layer and in the vector search SQL itself. */
class CrossUserSecurityIT extends AbstractIntegrationTest {

    private static final String SECRET_TEXT =
            "The quantum flux capacitor stores temporal energy inside the zeta resonance chamber of the reactor.";

    @Autowired
    private ChunkRepository chunks;

    @Autowired
    private EmbeddingClient embeddings;

    private Session alice;
    private Session bob;
    private UUID bobCourse;
    private UUID bobDocument;
    private UUID bobChat;

    @BeforeEach
    void setUp() throws Exception {
        alice = register();
        bob = register();
        bobCourse = createCourse(bob, "Bob's Physics");
        bobDocument = uploadAndAwaitReady(bob, bobCourse, "secret.pdf", TestPdfs.withPages(SECRET_TEXT));
        bobChat = createChat(bob, bobCourse);
    }

    @Test
    void userCannotReadAnotherUsersDocumentMetadata() throws Exception {
        mvc.perform(get("/api/documents/" + bobDocument).header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/" + bobDocument + "/status").header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotDownloadAnotherUsersPdfOrPageText() throws Exception {
        mvc.perform(get("/api/documents/" + bobDocument + "/preview").header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/" + bobDocument + "/pages/1").header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanDownloadTheirPdf() throws Exception {
        mvc.perform(get("/api/documents/" + bobDocument + "/preview").header("Authorization", bob.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedUsersCannotDownloadPdfs() throws Exception {
        mvc.perform(get("/api/documents/" + bobDocument + "/preview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documents/" + bobDocument + "/preview").header("Authorization", "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotDeleteOrRetryAnotherUsersDocument() throws Exception {
        mvc.perform(delete("/api/documents/" + bobDocument).header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/documents/" + bobDocument + "/retry").header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
        assertThat(documents.existsById(bobDocument)).isTrue();
    }

    @Test
    void documentListsOnlyContainOwnDocuments() throws Exception {
        assertThat(getJson("/api/documents", alice).path("totalElements").asLong()).isZero();
        assertThat(getJson("/api/documents?courseId=" + bobCourse, alice).path("totalElements").asLong()).isZero();
    }

    @Test
    void userCannotOpenOrModifyAnotherUsersCourse() throws Exception {
        mvc.perform(get("/api/courses/" + bobCourse).header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/courses/" + bobCourse).header("Authorization", alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/courses/" + bobCourse).header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotUploadIntoAnotherUsersCourse() throws Exception {
        mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", TestPdfs.withPages("hello there world")))
                        .param("courseId", bobCourse.toString())
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotOpenAnotherUsersChat() throws Exception {
        String base = "/api/chats/" + bobChat;
        mvc.perform(get(base).header("Authorization", alice.bearer())).andExpect(status().isNotFound());
        mvc.perform(get(base + "/messages").header("Authorization", alice.bearer())).andExpect(status().isNotFound());
        mvc.perform(delete(base).header("Authorization", alice.bearer())).andExpect(status().isNotFound());
        mvc.perform(post(base + "/messages").header("Authorization", alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(base + "/messages/stream").header("Authorization", alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotCreateChatInAnotherUsersCourse() throws Exception {
        mvc.perform(post("/api/chats").header("Authorization", alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("courseId", bobCourse.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void vectorSearchNeverReturnsAnotherUsersChunks() {
        float[] query = embeddings.embed(SECRET_TEXT);

        assertThat(search(new RetrievalScope(bob.id(), null, null, List.of()), query)).isNotEmpty();
        assertThat(search(new RetrievalScope(alice.id(), null, null, List.of()), query)).isEmpty();
    }

    @Test
    void passingAnotherUsersIdsToTheSearchStillReturnsNothing() {
        float[] query = embeddings.embed(SECRET_TEXT);

        assertThat(search(new RetrievalScope(alice.id(), bobCourse, null, List.of()), query)).isEmpty();
        assertThat(search(new RetrievalScope(alice.id(), null, null, List.of(bobDocument)), query)).isEmpty();
        assertThat(chunks.sample(new RetrievalScope(alice.id(), null, null, List.of(bobDocument)), 10)).isEmpty();
    }

    @Test
    void retrievalIsConfinedToTheSelectedDocuments() throws Exception {
        UUID other = uploadAndAwaitReady(bob, bobCourse, "other.pdf",
                TestPdfs.withPages("The zeta resonance chamber also appears in this unrelated second file about reactors."));
        float[] query = embeddings.embed("zeta resonance chamber");

        List<RetrievedChunk> onlyOther = search(new RetrievalScope(bob.id(), bobCourse, null, List.of(other)), query);

        assertThat(onlyOther).isNotEmpty().allMatch(chunk -> chunk.documentId().equals(other));
    }

    @Test
    void aFailedOrUnprocessedDocumentIsNeverSearched() throws Exception {
        documents.findById(bobDocument).ifPresent(document -> {
            document.setStatus(com.notemind.entity.Enums.DocumentStatus.FAILED);
            documents.saveAndFlush(document);
        });

        assertThat(search(new RetrievalScope(bob.id(), null, null, List.of()), embeddings.embed(SECRET_TEXT))).isEmpty();
    }

    @Test
    void chatRequestNamingAnotherUsersDocumentCannotPullItsContent() throws Exception {
        UUID aliceCourse = createCourse(alice, "Alice");
        uploadAndAwaitReady(alice, aliceCourse, "mine.pdf",
                TestPdfs.withPages("Completely different material about botany and photosynthesis in leaves."));
        UUID aliceChat = createChat(alice, aliceCourse);

        mvc.perform(post("/api/chats/" + aliceChat + "/messages").header("Authorization", alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("content", SECRET_TEXT,
                                "documentIds", List.of(bobDocument.toString())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage.sources.length()").value(0));
    }

    @Test
    void studentsCannotUseAdminEndpoints() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", alice.bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats").header("Authorization", alice.bearer())).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/users/" + bob.id() + "/status")
                        .header("Authorization", alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUsersCannotReachProtectedApis() throws Exception {
        for (String path : List.of("/api/courses", "/api/documents", "/api/chats", "/api/auth/me", "/api/admin/users")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    private List<RetrievedChunk> search(RetrievalScope scope, float[] query) {
        return chunks.search(scope, query, SECRET_TEXT, 20);
    }
}
