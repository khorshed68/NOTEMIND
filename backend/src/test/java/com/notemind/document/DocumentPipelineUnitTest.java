package com.notemind.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notemind.config.AppProperties;
import com.notemind.exception.ApiException;
import com.notemind.support.TestPdfs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentPipelineUnitTest {

    @TempDir
    Path temp;

    private static AppProperties properties(Path storage) {
        return new AppProperties(new AppProperties.Jwt("x".repeat(40), Duration.ofMinutes(15), Duration.ofDays(14), false),
                new AppProperties.Cors(List.of()), new AppProperties.Storage(storage.toString()),
                new AppProperties.Upload(20_000_000, 3),
                new AppProperties.Processing(1),
                new AppProperties.Rag(384, 32, 900, 150, 5, 30, 0.35, 0.75, 0.4, 8, 6),
                new AppProperties.RateLimit(10, 10, 10),
                new AppProperties.Ocr(false, "tesseract", "eng", 200, 25),
                new AppProperties.Admin("", "", ""));
    }

    private static final OcrEngine NO_OCR = new OcrEngine() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String recognize(java.awt.image.BufferedImage pageImage) {
            throw new AssertionError("OCR must not run");
        }
    };

    private Path write(byte[] content) throws Exception {
        return Files.write(temp.resolve("input-" + System.nanoTime() + ".pdf"), content);
    }

    // --- chunking -----------------------------------------------------------------------------

    @Test
    void shortPageBecomesOneChunkOnItsOwnPage() {
        var chunks = new TextChunker(900, 150).chunk(List.of(new PageText(7, "A definition of a compiler phase.", false)));
        assertThat(chunks).containsExactly(new TextChunk(7, 0, "A definition of a compiler phase."));
    }

    @Test
    void chunksNeverCrossPagesAndKeepTheirPageNumber() {
        String longText = "This sentence is about compilers and it repeats often. ".repeat(60);
        var chunks = new TextChunker(300, 50).chunk(List.of(new PageText(3, longText, false), new PageText(4, longText, false)));

        assertThat(chunks).hasSizeGreaterThan(4).allMatch(c -> c.content().length() <= 300);
        assertThat(chunks).extracting(TextChunk::pageNumber).contains(3, 4).isSorted();
        assertThat(chunks).extracting(TextChunk::chunkIndex).doesNotHaveDuplicates().isSorted();
    }

    @Test
    void consecutiveChunksOverlap() {
        List<String> sentences = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            sentences.add("Unique sentence number " + i + " explains one idea.");
        }
        var chunks = new TextChunker(300, 80).chunk(List.of(new PageText(1, String.join(" ", sentences), false)));

        assertThat(chunks.size()).isGreaterThan(1);
        String tail = chunks.get(0).content().substring(chunks.get(0).content().length() - 20);
        assertThat(chunks.get(1).content()).contains(tail);
    }

    @Test
    void textWithNoBoundariesIsStillSplit() {
        var chunks = new TextChunker(100, 10).chunk(List.of(new PageText(1, "x".repeat(450), false)));
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(4).allMatch(c -> c.content().length() <= 100);
    }

    @Test
    void blankAndTrivialPagesProduceNoChunks() {
        var chunks = new TextChunker(900, 150).chunk(List.of(new PageText(1, "   ", false), new PageText(2, "12", false)));
        assertThat(chunks).isEmpty();
    }

    @Test
    void overlapMustBeSmallerThanChunkSize() {
        assertThatThrownBy(() -> new TextChunker(100, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- header / footer removal --------------------------------------------------------------

    @Test
    void repeatedHeadersAndPageNumbersAreRemovedButBodyKept() {
        List<PageText> pages = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            pages.add(new PageText(i, "CSE 3101 Compiler Design\nBody content unique to page " + (char) ('a' + i)
                    + " explaining things\nmore body " + (char) ('a' + i) + "\nPage " + i, false));
        }
        var cleaned = new HeaderFooterCleaner().clean(pages);

        assertThat(cleaned.get(0).text()).doesNotContain("CSE 3101", "Page 1").contains("Body content");
        assertThat(cleaned.get(5).pageNumber()).isEqualTo(6);
    }

    @Test
    void shortDocumentsAreLeftAlone() {
        List<PageText> pages = List.of(new PageText(1, "Same\nbody", false), new PageText(2, "Same\nbody", false));
        assertThat(new HeaderFooterCleaner().clean(pages)).isEqualTo(pages);
    }

    // --- extraction ---------------------------------------------------------------------------

    @Test
    void extractsEachPageWithItsOriginalNumber() throws Exception {
        var extractor = new PdfTextExtractor(NO_OCR, properties(temp));
        var pages = extractor.extract(write(TestPdfs.withPages("alpha page content", "", "gamma page content")));

        assertThat(pages).extracting(PageText::pageNumber).containsExactly(1, 2, 3);
        assertThat(pages.get(0).text()).contains("alpha");
        assertThat(pages.get(1).text()).isEmpty();
        assertThat(pages.get(2).text()).contains("gamma");
    }

    @Test
    void ocrIsUsedOnlyForPagesWithoutTextAndKeepsPageNumbers() throws Exception {
        List<Integer> calls = new ArrayList<>();
        OcrEngine ocr = new OcrEngine() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String recognize(java.awt.image.BufferedImage image) {
                calls.add(1);
                return "Recognised scanned text for this page of the notes.";
            }
        };
        var extractor = new PdfTextExtractor(ocr, properties(temp));
        var pages = extractor.extract(write(TestPdfs.withPages("A real text page with plenty of characters.", "")));

        assertThat(calls).hasSize(1);
        assertThat(pages.get(0).ocr()).isFalse();
        assertThat(pages.get(1).ocr()).isTrue();
        assertThat(pages.get(1).pageNumber()).isEqualTo(2);
        assertThat(pages.get(1).text()).contains("Recognised");
    }

    @Test
    void pdfWithNoTextAndNoOcrFailsClearly() throws Exception {
        var extractor = new PdfTextExtractor(NO_OCR, properties(temp));
        Path blank = write(TestPdfs.withPages("", ""));
        assertThatThrownBy(() -> extractor.extract(blank))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("no extractable text");
    }

    @Test
    void cleanRemovesNulCharactersAndRejoinsHyphenatedWords() {
        assertThat(PdfTextExtractor.clean("com\u0000piler trans-\nlation")).isEqualTo("compiler translation");
    }

    // --- validation ---------------------------------------------------------------------------

    @Test
    void validatorAcceptsRealPdfAndRejectsImpostors() throws Exception {
        var validator = new PdfValidator(properties(temp));
        validator.validate(write(TestPdfs.withPages("hello")));

        Path notPdf = write("plain text pretending".getBytes());
        assertThatThrownBy(() -> validator.validate(notPdf)).isInstanceOf(ApiException.class);
        Path corrupt = write("%PDF-1.7 not really".getBytes());
        assertThatThrownBy(() -> validator.validate(corrupt)).isInstanceOf(ApiException.class);
    }

    @Test
    void validatorEnforcesPageLimit() throws Exception {
        var validator = new PdfValidator(properties(temp));
        Path tooLong = write(TestPdfs.withPages("a", "b", "c", "d"));
        assertThatThrownBy(() -> validator.validate(tooLong)).isInstanceOf(ApiException.class)
                .hasMessageContaining("maximum");
    }

    // --- storage ------------------------------------------------------------------------------

    @Test
    void storageGeneratesOpaqueKeysAndRoundTrips() throws Exception {
        var storage = new LocalFileStorage(properties(temp.resolve("store")));
        Path source = write("content".getBytes());

        String key = storage.store(source);

        assertThat(key).matches("[0-9a-f]{2}/[0-9a-f-]{36}\\.pdf");
        assertThat(storage.size(key)).isEqualTo(7);
        storage.delete(key);
        assertThatThrownBy(() -> storage.size(key)).isInstanceOf(java.nio.file.NoSuchFileException.class);
    }

    @Test
    void storageRejectsTraversalKeys() throws Exception {
        var storage = new LocalFileStorage(properties(temp.resolve("store2")));
        for (String key : List.of("../../etc/passwd", "ab/../../secret.pdf", "/etc/passwd", "")) {
            assertThatThrownBy(() -> storage.open(key)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
