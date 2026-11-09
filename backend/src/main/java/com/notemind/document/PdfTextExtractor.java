package com.notemind.document;

import com.notemind.config.AppProperties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Extracts text one page at a time, using OCR for individual pages that have no text layer. */
@Component
public class PdfTextExtractor {

    private final OcrEngine ocr;
    private final AppProperties.Ocr config;

    public PdfTextExtractor(OcrEngine ocr, AppProperties properties) {
        this.ocr = ocr;
        this.config = properties.ocr();
    }

    public List<PageText> extract(Path pdf) {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            int total = document.getNumberOfPages();
            if (total == 0) {
                throw new DocumentProcessingException("This PDF has no pages.");
            }
            boolean ocrReady = ocr.isAvailable();
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            List<PageText> pages = new ArrayList<>(total);
            for (int number = 1; number <= total; number++) {
                String text = clean(extractPage(stripper, document, number));
                boolean usedOcr = false;
                if (text.length() < config.minPageChars() && ocrReady) {
                    BufferedImage image = renderer.renderImageWithDPI(number - 1, config.dpi(), ImageType.GRAY);
                    String recognised = clean(ocr.recognize(image));
                    if (recognised.length() > text.length()) {
                        text = recognised;
                        usedOcr = true;
                    }
                }
                pages.add(new PageText(number, text, usedOcr));
            }
            if (pages.stream().allMatch(page -> page.text().isBlank())) {
                throw new DocumentProcessingException(ocrReady
                        ? "No readable text was found in this PDF, even with OCR."
                        : "This PDF has no extractable text. It may be a scan, and OCR is not available on this server.");
            }
            return pages;
        } catch (InvalidPasswordException ex) {
            throw new DocumentProcessingException("Password-protected PDFs are not supported.", ex);
        } catch (IOException ex) {
            throw new DocumentProcessingException("This PDF appears to be corrupted and could not be read.", ex);
        }
    }

    public String extractSinglePage(Path pdf, int pageNumber) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return clean(extractPage(new PDFTextStripper(), document, pageNumber));
        }
    }

    public int countPages(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return document.getNumberOfPages();
        }
    }

    private String extractPage(PDFTextStripper stripper, PDDocument document, int number) throws IOException {
        stripper.setStartPage(number);
        stripper.setEndPage(number);
        return stripper.getText(document);
    }

    /** PostgreSQL text columns reject NUL, and line-end hyphens would otherwise split search terms. */
    static String clean(String raw) {
        return raw
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .replace('\t', ' ')
                .replaceAll("(?<=\\p{L})-\\n(?=\\p{Ll})", "")
                .replaceAll(" {2,}", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
