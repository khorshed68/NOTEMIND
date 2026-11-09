package com.notemind.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notemind.entity.Document;
import com.notemind.entity.Enums.DocumentStatus;
import com.notemind.support.AbstractIntegrationTest;
import com.notemind.support.TestPdfs;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class CourseAndDocumentIT extends AbstractIntegrationTest {

    private static final String PAGE_ONE = "Lexical analysis is the first phase of a compiler and it produces tokens.";
    private static final String PAGE_TWO = "Syntax analysis builds a parse tree from the stream of tokens using a grammar.";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void courseCrud() throws Exception {
        Session user = register();
        UUID id = createCourse(user, "Compiler Design");

        mvc.perform(put("/api/courses/" + id).header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Compilers\",\"courseCode\":\"CSE 3101\",\"description\":\"Fall\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Compilers"));

        assertThat(getJson("/api/courses", user).path("totalElements").asLong()).isEqualTo(1);

        mvc.perform(delete("/api/courses/" + id).header("Authorization", user.bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/api/courses/" + id).header("Authorization", user.bearer())).andExpect(status().isNotFound());
    }

    @Test
    void courseValidationRejectsBlankName() throws Exception {
        Session user = register();
        mvc.perform(post("/api/courses").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void uploadedPdfIsProcessedWithCorrectPageNumbers() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "Compilers");
        UUID id = uploadAndAwaitReady(user, course, "Lecture 1.pdf", TestPdfs.withPages(PAGE_ONE, PAGE_TWO));

        Document document = documents.findById(id).orElseThrow();
        assertThat(document.getPageCount()).isEqualTo(2);
        assertThat(document.getFilename()).isEqualTo("Lecture 1.pdf");
        assertThat(document.getStorageKey()).doesNotContain("Lecture");

        var pages = jdbc.queryForList("SELECT page_number FROM chunks WHERE document_id = ? ORDER BY chunk_index",
                Integer.class, id);
        assertThat(pages).containsExactly(1, 2);
        String secondPage = jdbc.queryForObject("SELECT content FROM chunks WHERE document_id = ? AND page_number = 2",
                String.class, id);
        assertThat(secondPage).contains("parse tree");
    }

    @Test
    void responsesNeverExposeStoragePaths() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "Compilers");
        UUID id = upload(user, course, "a.pdf", TestPdfs.withPages(PAGE_ONE));
        awaitTerminal(id);

        String body = getJson("/api/documents/" + id, user).toString();

        assertThat(body).doesNotContain("storageKey", documents.findById(id).orElseThrow().getStorageKey());
    }

    @Test
    void pdfPreviewAndPageTextAreServedToTheOwner() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "Compilers");
        UUID id = uploadAndAwaitReady(user, course, "a.pdf", TestPdfs.withPages(PAGE_ONE, PAGE_TWO));

        mvc.perform(get("/api/documents/" + id + "/preview").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().doesNotExist("Content-Disposition"));
        assertThat(getJson("/api/documents/" + id + "/pages/2", user).path("text").asText()).contains("parse tree");
        mvc.perform(get("/api/documents/" + id + "/pages/99").header("Authorization", user.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTextFileRenamedToPdfIsRejected() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "C");
        mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "just text".getBytes()))
                        .param("courseId", course.toString())
                        .header("Authorization", user.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not a valid PDF")));
    }

    @Test
    void aCorruptedPdfIsRejected() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "C");
        mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "bad.pdf", "application/pdf", "%PDF-1.7 garbage garbage".getBytes()))
                        .param("courseId", course.toString())
                        .header("Authorization", user.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("corrupted")));
    }

    @Test
    void anEmptyUploadIsRejected() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "C");
        mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]))
                        .param("courseId", course.toString())
                        .header("Authorization", user.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadMustTargetExactlyOneParent() throws Exception {
        Session user = register();
        mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", TestPdfs.withPages(PAGE_ONE)))
                        .header("Authorization", user.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pathsInFilenamesAreStripped() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "C");
        UUID id = upload(user, course, "../../etc/passwd.pdf", TestPdfs.withPages(PAGE_ONE));
        assertThat(documents.findById(id).orElseThrow().getFilename()).isEqualTo("passwd.pdf");
    }

    @Test
    void aPdfWithoutTextFailsWithAUsefulMessage() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "C");
        UUID id = upload(user, course, "scan.pdf", TestPdfs.withPages(""));

        assertThat(awaitTerminal(id)).isEqualTo(DocumentStatus.FAILED);
        assertThat(documents.findById(id).orElseThrow().getErrorMessage()).contains("no extractable text");
    }

    @Test
    void retryOnlyWorksForFailedDocumentsAndDoesNotDuplicate() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "C");
        UUID ready = uploadAndAwaitReady(user, course, "ok.pdf", TestPdfs.withPages(PAGE_ONE));
        mvc.perform(post("/api/documents/" + ready + "/retry").header("Authorization", user.bearer()))
                .andExpect(status().isConflict());

        Document document = documents.findById(ready).orElseThrow();
        document.setStatus(DocumentStatus.FAILED);
        documents.saveAndFlush(document);
        mvc.perform(post("/api/documents/" + ready + "/retry").header("Authorization", user.bearer()))
                .andExpect(status().isAccepted());

        assertThat(awaitTerminal(ready)).isEqualTo(DocumentStatus.READY);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chunks WHERE document_id = ?", Integer.class, ready))
                .isEqualTo(1);
    }

    @Test
    void deletingADocumentRemovesItsChunksButNothingElse() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "C");
        UUID first = uploadAndAwaitReady(user, course, "a.pdf", TestPdfs.withPages(PAGE_ONE));
        UUID second = uploadAndAwaitReady(user, course, "b.pdf", TestPdfs.withPages(PAGE_TWO));

        mvc.perform(delete("/api/documents/" + first).header("Authorization", user.bearer()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM chunks WHERE document_id = ?", Integer.class, first)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chunks WHERE document_id = ?", Integer.class, second)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courses WHERE id = ?", Integer.class, course)).isEqualTo(1);
    }

    @Test
    void deletingACourseCascadesToDocumentsAndChats() throws Exception {
        Session user = register();
        UUID course = createCourse(user, "C");
        UUID doc = uploadAndAwaitReady(user, course, "a.pdf", TestPdfs.withPages(PAGE_ONE));
        UUID chat = createChat(user, course);

        mvc.perform(delete("/api/courses/" + course).header("Authorization", user.bearer()))
                .andExpect(status().isNoContent());

        assertThat(documents.existsById(doc)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chats WHERE id = ?", Integer.class, chat)).isZero();
    }
}
