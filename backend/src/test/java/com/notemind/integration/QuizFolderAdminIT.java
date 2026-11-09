package com.notemind.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.notemind.ai.EmbeddingClient;
import com.notemind.ai.RetrievalScope;
import com.notemind.entity.Enums.DocumentStatus;
import com.notemind.entity.Enums.Role;
import com.notemind.repository.ChunkRepository;
import com.notemind.repository.UserRepository;
import com.notemind.support.AbstractIntegrationTest;
import com.notemind.support.TestPdfs;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class QuizFolderAdminIT extends AbstractIntegrationTest {

    private static final String TEXT = "Dijkstra's algorithm finds shortest paths in a weighted graph with non negative edges.";
    private static final String QUIZ_JSON = """
            ```json
            [
              {"type":"MCQ","question":"What does Dijkstra find?","options":["Shortest paths","Sorting","Hashing","Parsing"],
               "correctAnswer":"Shortest paths","explanation":"From the notes.","passage":1},
              {"type":"TRUE_FALSE","question":"Edges must be non negative.","options":[],"correctAnswer":"true",
               "explanation":"Stated in the notes.","passage":1},
              {"type":"SHORT_ANSWER","question":"What kind of graph?","options":[],"correctAnswer":"weighted graph",
               "explanation":"Stated in the notes.","passage":1},
              {"type":"MCQ","question":"Answer not among options","options":["A","B"],"correctAnswer":"C",
               "explanation":"","passage":1},
              {"type":"MCQ","question":"Points at a passage that does not exist","options":["A","B"],"correctAnswer":"A",
               "explanation":"","passage":99}
            ]
            ```""";

    @Autowired
    private UserRepository users;

    @Autowired
    private ChunkRepository chunks;

    @Autowired
    private EmbeddingClient embeddings;

    private void promote(Session session, Role role) {
        var user = users.findById(session.id()).orElseThrow();
        user.setRole(role);
        users.saveAndFlush(user);
    }

    private JsonNode generateQuiz(Session user, UUID document) throws Exception {
        var result = mvc.perform(post("/api/documents/" + document + "/quiz").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5,\"difficulty\":\"EASY\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void quizDropsUngroundedQuestionsAndHidesAnswersUntilGraded() throws Exception {
        when(llm.complete(anyString(), anyList(), anyString())).thenReturn(QUIZ_JSON);
        Session user = register();
        UUID course = createCourse(user, "Algorithms");
        UUID document = uploadAndAwaitReady(user, course, "graphs.pdf", TestPdfs.withPages(TEXT));

        JsonNode quiz = generateQuiz(user, document);

        assertThat(quiz.path("questions")).hasSize(3);
        assertThat(quiz.toString()).doesNotContain("correctAnswer", "explanation");

        Map<String, String> answers = new HashMap<>();
        answers.put(quiz.path("questions").get(0).path("id").asText(), "Shortest paths");
        answers.put(quiz.path("questions").get(1).path("id").asText(), "False");
        answers.put(quiz.path("questions").get(2).path("id").asText(), "a weighted graph");

        var result = mvc.perform(post("/api/quizzes/" + quiz.path("id").asText() + "/attempts")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("answers", answers))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andReturn();
        JsonNode graded = json.readTree(result.getResponse().getContentAsString()).path("questions");
        assertThat(graded.get(1).path("correct").asBoolean()).isFalse();
        assertThat(graded.get(1).path("correctAnswer").asText()).isEqualTo("True");
        assertThat(graded.get(0).path("pageNumber").asInt()).isEqualTo(1);
        assertThat(graded.get(0).path("documentName").asText()).isEqualTo("graphs.pdf");
    }

    @Test
    void quizzesArePrivateToTheirOwner() throws Exception {
        when(llm.complete(anyString(), anyList(), anyString())).thenReturn(QUIZ_JSON);
        Session owner = register();
        Session other = register();
        UUID course = createCourse(owner, "Algorithms");
        UUID document = uploadAndAwaitReady(owner, course, "graphs.pdf", TestPdfs.withPages(TEXT));
        String quizId = generateQuiz(owner, document).path("id").asText();

        mvc.perform(get("/api/quizzes/" + quizId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/documents/" + document + "/quiz").header("Authorization", other.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5,\"difficulty\":\"EASY\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void quizWithoutAnLlmExplainsWhatIsNeeded() throws Exception {
        when(llm.isAvailable()).thenReturn(false);
        Session user = register();
        UUID course = createCourse(user, "Algorithms");
        UUID document = uploadAndAwaitReady(user, course, "graphs.pdf", TestPdfs.withPages(TEXT));

        mvc.perform(post("/api/documents/" + document + "/quiz").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5,\"difficulty\":\"EASY\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void quizCountIsValidated() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "Algorithms");
        mvc.perform(post("/api/courses/" + course + "/quiz").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":500,\"difficulty\":\"EASY\"}"))
                .andExpect(status().isBadRequest());
    }

    private UUID createFolder(Session owner) throws Exception {
        var result = mvc.perform(post("/api/folders").header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Section A\",\"courseName\":\"Networks\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private UUID uploadToFolder(Session owner, UUID folder) throws Exception {
        var result = mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "shared.pdf", "application/pdf", TestPdfs.withPages(TEXT)))
                        .param("folderId", folder.toString())
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
        assertThat(awaitTerminal(id)).isEqualTo(DocumentStatus.READY);
        return id;
    }

    private void addMember(Session owner, UUID folder, Session member) throws Exception {
        mvc.perform(post("/api/folders/" + folder + "/members").header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", member.email(), "permission", "VIEW"))))
                .andExpect(status().isCreated());
    }

    @Test
    void studentsCannotCreateFolders() throws Exception {
        Session student = register();
        mvc.perform(post("/api/folders").header("Authorization", student.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"courseName\":\"Y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void knowingAFolderIdGrantsNoAccessUntilShared() throws Exception {
        Session rep = register();
        promote(rep, Role.CLASS_REP);
        Session student = register();
        UUID folder = createFolder(rep);
        UUID document = uploadToFolder(rep, folder);
        float[] query = embeddings.embed(TEXT);
        RetrievalScope scope = new RetrievalScope(student.id(), null, folder, List.of());

        mvc.perform(get("/api/folders/" + folder).header("Authorization", student.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/" + document + "/preview").header("Authorization", student.bearer()))
                .andExpect(status().isNotFound());
        assertThat(chunks.search(scope, query, TEXT, 10)).isEmpty();

        addMember(rep, folder, student);

        mvc.perform(get("/api/folders/" + folder).header("Authorization", student.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/documents/" + document + "/preview").header("Authorization", student.bearer()))
                .andExpect(status().isOk());
        assertThat(chunks.search(scope, query, TEXT, 10)).isNotEmpty();

        mvc.perform(delete("/api/folders/" + folder + "/members/" + student.id()).header("Authorization", rep.bearer()))
                .andExpect(status().isNoContent());
        assertThat(chunks.search(scope, query, TEXT, 10)).isEmpty();
    }

    @Test
    void viewMembersCannotChangeSharedMaterial() throws Exception {
        Session rep = register();
        promote(rep, Role.CLASS_REP);
        Session student = register();
        UUID folder = createFolder(rep);
        UUID document = uploadToFolder(rep, folder);
        addMember(rep, folder, student);

        mvc.perform(delete("/api/documents/" + document).header("Authorization", student.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", TestPdfs.withPages(TEXT)))
                        .param("folderId", folder.toString())
                        .header("Authorization", student.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanManageUsersButCannotDisableThemselves() throws Exception {
        Session admin = register();
        promote(admin, Role.ADMIN);
        Session student = register();

        assertThat(getJson("/api/admin/users?q=" + student.email(), admin).path("totalElements").asLong()).isEqualTo(1);
        assertThat(getJson("/api/admin/stats", admin).path("totalUsers").asLong()).isGreaterThanOrEqualTo(2);

        mvc.perform(patch("/api/admin/users/" + student.id() + "/status").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/api/auth/me").header("Authorization", student.bearer())).andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/admin/users/" + admin.id() + "/status").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/admin/users/" + student.id() + "/role").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"CLASS_REP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLASS_REP"));
    }

    @Test
    void adminsCannotReadStudentsPrivateDocuments() throws Exception {
        Session admin = register();
        promote(admin, Role.ADMIN);
        Session student = register();
        UUID course = createCourse(student, "Private");
        UUID document = uploadAndAwaitReady(student, course, "p.pdf", TestPdfs.withPages(TEXT));

        mvc.perform(get("/api/documents/" + document + "/preview").header("Authorization", admin.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void dashboardReportsRealCountsPerUser() throws Exception {
        when(llm.complete(anyString(), anyList(), anyString())).thenReturn("Shortest [S1].");
        Session user = register();
        UUID course = createCourse(user, "Algorithms");
        uploadAndAwaitReady(user, course, "graphs.pdf", TestPdfs.withPages(TEXT, TEXT + " again here"));
        UUID chat = createChat(user, course);
        mvc.perform(post("/api/chats/" + chat + "/messages").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Dijkstra shortest paths weighted graph\"}"))
                .andExpect(status().isOk());

        JsonNode stats = getJson("/api/dashboard/stats", user);
        assertThat(stats.path("courses").asInt()).isEqualTo(1);
        assertThat(stats.path("documents").asInt()).isEqualTo(1);
        assertThat(stats.path("pages").asInt()).isEqualTo(2);
        assertThat(stats.path("questions").asInt()).isEqualTo(1);
        assertThat(stats.path("chats").asInt()).isEqualTo(1);
        assertThat(stats.path("questionsPerDay")).hasSize(14);
        assertThat(getJson("/api/dashboard/activity", user)).isNotEmpty();
    }
}
