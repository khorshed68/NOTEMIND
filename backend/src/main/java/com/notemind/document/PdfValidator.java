package com.notemind.document;

import com.notemind.config.AppProperties;
import com.notemind.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

/** Validates by content: the magic bytes and an actual parse. Filename and client MIME type are not trusted. */
@Component
public class PdfValidator {

    private static final byte[] MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final int maxPages;

    public PdfValidator(AppProperties properties) {
        this.maxPages = properties.upload().maxPages();
    }

    public void validate(Path file) throws IOException {
        byte[] header;
        try (InputStream in = Files.newInputStream(file)) {
            header = in.readNBytes(MAGIC.length);
        }
        if (!Arrays.equals(header, MAGIC)) {
            throw ApiException.badRequest("Only PDF files are accepted. This file is not a valid PDF.");
        }
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            if (document.getNumberOfPages() == 0) {
                throw ApiException.badRequest("This PDF has no pages.");
            }
            if (document.getNumberOfPages() > maxPages) {
                throw ApiException.badRequest("This PDF has more than the maximum of " + maxPages + " pages.");
            }
        } catch (InvalidPasswordException ex) {
            throw ApiException.badRequest("Password-protected PDFs are not supported. Remove the password and retry.");
        } catch (IOException ex) {
            throw ApiException.badRequest("This PDF appears to be corrupted and could not be read.");
        }
    }
}
