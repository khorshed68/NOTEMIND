package com.notemind.document;

import com.notemind.config.AppProperties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Calls the tesseract binary, which the backend Docker image installs along with English and Bangla data. */
@Component
public class TesseractOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);
    private static final long PAGE_TIMEOUT_SECONDS = 120;

    private final AppProperties.Ocr config;
    private volatile Boolean available;

    public TesseractOcrEngine(AppProperties properties) {
        this.config = properties.ocr();
    }

    @Override
    public boolean isAvailable() {
        if (!config.enabled()) {
            return false;
        }
        if (available == null) {
            available = probe();
        }
        return available;
    }

    private boolean probe() {
        try {
            Process process = new ProcessBuilder(config.command(), "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException ex) {
            log.info("Tesseract not found; scanned PDFs cannot be read on this host");
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public String recognize(BufferedImage pageImage) {
        Path image = null;
        try {
            image = Files.createTempFile("notemind-ocr-", ".png");
            ImageIO.write(pageImage, "png", image.toFile());
            Process process = new ProcessBuilder(List.of(config.command(), image.toString(), "stdout",
                    "-l", config.languages(), "--dpi", String.valueOf(config.dpi())))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DocumentProcessingException("OCR timed out on a page of this PDF.");
            }
            if (process.exitValue() != 0) {
                throw new DocumentProcessingException("OCR failed on a page of this PDF.");
            }
            return text;
        } catch (IOException ex) {
            throw new DocumentProcessingException("OCR failed on a page of this PDF.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DocumentProcessingException("OCR was interrupted.", ex);
        } finally {
            if (image != null) {
                try {
                    Files.deleteIfExists(image);
                } catch (IOException ex) {
                    log.debug("Could not delete OCR temp file", ex);
                }
            }
        }
    }
}
