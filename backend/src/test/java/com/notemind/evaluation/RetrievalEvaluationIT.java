package com.notemind.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.notemind.ai.EmbeddingClient;
import com.notemind.ai.RetrievalScope;
import com.notemind.ai.RetrievedChunk;
import com.notemind.ai.SpringAiEmbeddingClient;
import com.notemind.config.AppProperties;
import com.notemind.document.DocumentProcessingService;
import com.notemind.document.HeaderFooterCleaner;
import com.notemind.document.PageText;
import com.notemind.document.PdfTextExtractor;
import com.notemind.document.TextChunk;
import com.notemind.document.TextChunker;
import com.notemind.entity.Document;
import com.notemind.entity.Enums.DocumentStatus;
import com.notemind.repository.ChunkRepository;
import com.notemind.support.AbstractIntegrationTest;
import com.notemind.support.TestPdfs;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Measures retrieval on evaluation/questions.json through the real pipeline: real PDFs, real
 * extraction and chunking, real local embeddings, real pgvector SQL. Writes evaluation/results.md.
 * Needs the model download, so it runs only on request:
 * mvnw verify -DskipTests=false -Dit.test=RetrievalEvaluationIT -Dnotemind.eval=true
 */
@SpringBootTest(properties = "spring.ai.model.embedding=transformers")
@EnabledIfSystemProperty(named = "notemind.eval", matches = "true")
class RetrievalEvaluationIT extends AbstractIntegrationTest {

    private static final Path ROOT = Path.of("..", "evaluation");
    private static final String PAGE_BREAK = "=== PAGE ===";
    private static final double[] THRESHOLDS = {0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50};

    @Autowired private ObjectProvider<EmbeddingModel> embeddingModel;
    @Autowired private AppProperties properties;
    @Autowired private PdfTextExtractor extractor;
    @Autowired private HeaderFooterCleaner cleaner;
    @Autowired private TextChunker chunker;
    @Autowired private ChunkRepository chunks;

    private record Question(String id, String text, String document, int page, boolean answerable) {
    }

    private record Metrics(double hit1, double hit3, double hit5, double mrr) {
    }

    @Test
    void evaluate() throws Exception {
        EmbeddingClient embeddings = new SpringAiEmbeddingClient(embeddingModel, properties);
        Session user = register();
        UUID course = createCourse(user, "Evaluation");
        Files.createDirectories(ROOT.resolve("corpus"));

        int totalChunks = 0;
        int totalPages = 0;
        List<Path> sources;
        try (Stream<Path> files = Files.list(ROOT.resolve("corpus"))) {
            sources = files.filter(p -> p.toString().endsWith(".txt")).sorted().toList();
        }
        for (Path source : sources) {
            String[] pages = Files.readString(source, StandardCharsets.UTF_8).split("\\R?" + PAGE_BREAK + "\\R?");
            String name = source.getFileName().toString().replace(".txt", ".pdf");
            byte[] pdf = TestPdfs.withPages(pages);
            Path pdfPath = ROOT.resolve("corpus").resolve(name);
            Files.write(pdfPath, pdf);
            totalPages += pages.length;
            totalChunks += ingest(user.id(), course, name, pdf.length, pdfPath, embeddings);
        }

        List<Question> questions = load();
        List<Question> answerable = questions.stream().filter(Question::answerable).toList();
        List<Question> unanswerable = questions.stream().filter(q -> !q.answerable()).toList();
        RetrievalScope scope = new RetrievalScope(user.id(), course, null, List.of());

        List<List<RetrievedChunk>> answerableRuns = new ArrayList<>();
        for (Question question : answerable) {
            answerableRuns.add(chunks.search(scope, embeddings.embed(question.text()), question.text(), 30));
        }
        List<List<RetrievedChunk>> negativeRuns = new ArrayList<>();
        for (Question question : unanswerable) {
            negativeRuns.add(chunks.search(scope, embeddings.embed(question.text()), question.text(), 30));
        }

        Comparator<RetrievedChunk> bySimilarity = Comparator.comparingDouble(RetrievedChunk::similarity).reversed();
        Metrics vector = metrics(answerable, answerableRuns.stream().map(run -> run.stream().sorted(bySimilarity).toList()).toList());
        Metrics hybrid = metrics(answerable, answerableRuns);

        StringBuilder report = new StringBuilder();
        report.append("# Retrieval evaluation results\n\n")
                .append("Generated by `RetrievalEvaluationIT` on ").append(LocalDate.now()).append(". ")
                .append("These numbers are measured output of that test, not estimates.\n\n")
                .append("- Corpus: ").append(sources.size()).append(" PDFs, ").append(totalPages).append(" pages, ")
                .append(totalChunks).append(" chunks\n")
                .append("- Embedding model: local all-MiniLM-L6-v2 (384 dimensions)\n")
                .append("- Questions: ").append(answerable.size()).append(" answerable, ")
                .append(unanswerable.size()).append(" unanswerable\n")
                .append("- Scope: LLM generation is **not** part of this measurement\n\n")
                .append("## Ranking: is the correct page retrieved?\n\n")
                .append("| Method | Hit@1 | Hit@3 | Hit@5 | MRR |\n|---|---|---|---|---|\n");
        row(report, "Vector search only", vector);
        row(report, "Hybrid (vector + keyword, RRF), as shipped", hybrid);

        report.append("\n## Relevance gate: threshold sweep\n\n")
                .append("A question is rejected when no passage clears the threshold (keyword matches use 0.75 x threshold). ")
                .append("Correct page kept means the expected page survives the gate within the top 5.\n\n")
                .append("| Threshold | Correct page kept | Unanswerable rejected |\n|---|---|---|\n");
        for (double threshold : THRESHOLDS) {
            int kept = 0;
            for (int i = 0; i < answerable.size(); i++) {
                List<RetrievedChunk> top = gate(answerableRuns.get(i), threshold);
                if (rank(answerable.get(i), top) > 0) {
                    kept++;
                }
            }
            int rejected = 0;
            for (List<RetrievedChunk> run : negativeRuns) {
                if (gate(run, threshold).isEmpty()) {
                    rejected++;
                }
            }
            report.append(String.format(Locale.ROOT, "| %.2f | %d/%d | %d/%d |%n", threshold, kept, answerable.size(),
                    rejected, unanswerable.size()));
        }

        report.append("\n## Per-question detail (hybrid)\n\n| Question | Expected | Rank of correct page | Top similarity |\n|---|---|---|---|\n");
        for (int i = 0; i < answerable.size(); i++) {
            Question question = answerable.get(i);
            int rank = rank(question, answerableRuns.get(i).stream().limit(5).toList());
            report.append(String.format(Locale.ROOT, "| %s | %s p.%d | %s | %.3f |%n", question.id(), question.document(),
                    question.page(), rank == 0 ? "not in top 5" : String.valueOf(rank), best(answerableRuns.get(i))));
        }
        report.append("\n## Highest similarity for unanswerable questions\n\n| Question | Top similarity |\n|---|---|\n");
        for (int i = 0; i < unanswerable.size(); i++) {
            report.append(String.format(Locale.ROOT, "| %s | %.3f |%n", unanswerable.get(i).id(), best(negativeRuns.get(i))));
        }

        Files.writeString(ROOT.resolve("results.md"), report.toString(), StandardCharsets.UTF_8);
        assertThat(hybrid.hit5()).isGreaterThan(0);
    }

    private int ingest(UUID userId, UUID course, String name, long size, Path pdf, EmbeddingClient embeddings) {
        Document document = new Document();
        document.setCourseId(course);
        document.setUserId(userId);
        document.setFilename(name);
        document.setStorageKey("eval-" + UUID.randomUUID());
        document.setFileSize(size);
        document.setStatus(DocumentStatus.READY);
        document = documents.saveAndFlush(document);
        final String filename = name;

        List<PageText> pages = cleaner.clean(extractor.extract(pdf));
        List<TextChunk> textChunks = chunker.chunk(pages);
        chunks.insertAll(document.getId(), textChunks, embeddings.embedAll(
                textChunks.stream().map(c -> DocumentProcessingService.embeddingInput(filename, c)).toList()));
        return textChunks.size();
    }

    private List<Question> load() throws Exception {
        List<Question> questions = new ArrayList<>();
        for (JsonNode node : json.readTree(ROOT.resolve("questions.json").toFile())) {
            questions.add(new Question(node.path("id").asText(), node.path("question").asText(),
                    node.path("document").asText(), node.path("page").asInt(), node.path("answerable").asBoolean(true)));
        }
        return questions;
    }

    private static List<RetrievedChunk> gate(List<RetrievedChunk> ranked, double threshold) {
        return ranked.stream()
                .filter(c -> c.similarity() >= threshold || (c.keywordHit() && c.similarity() >= threshold * 0.75))
                .limit(5)
                .toList();
    }

    private static int rank(Question question, List<RetrievedChunk> ranked) {
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).documentName().equals(question.document()) && ranked.get(i).pageNumber() == question.page()) {
                return i + 1;
            }
        }
        return 0;
    }

    private static Metrics metrics(List<Question> questions, List<List<RetrievedChunk>> runs) {
        int hit1 = 0;
        int hit3 = 0;
        int hit5 = 0;
        double reciprocal = 0;
        for (int i = 0; i < questions.size(); i++) {
            int rank = rank(questions.get(i), runs.get(i).stream().limit(5).toList());
            hit1 += rank == 1 ? 1 : 0;
            hit3 += rank >= 1 && rank <= 3 ? 1 : 0;
            hit5 += rank >= 1 ? 1 : 0;
            reciprocal += rank == 0 ? 0 : 1.0 / rank;
        }
        double n = questions.size();
        return new Metrics(hit1 / n, hit3 / n, hit5 / n, reciprocal / n);
    }

    private static double best(List<RetrievedChunk> run) {
        return run.stream().mapToDouble(RetrievedChunk::similarity).max().orElse(0);
    }

    private static void row(StringBuilder report, String name, Metrics m) {
        report.append(String.format(Locale.ROOT, "| %s | %.1f%% | %.1f%% | %.1f%% | %.3f |%n", name, m.hit1() * 100,
                m.hit3() * 100, m.hit5() * 100, m.mrr()));
    }
}
