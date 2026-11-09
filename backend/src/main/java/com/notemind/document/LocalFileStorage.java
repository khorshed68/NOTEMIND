package com.notemind.document;

import com.notemind.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private static final Pattern KEY_FORMAT = Pattern.compile("^[0-9a-f]{2}/[0-9a-f-]{36}\\.pdf$");

    private final Path root;

    public LocalFileStorage(AppProperties properties) throws IOException {
        this.root = Path.of(properties.storage().rootDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(Path source) throws IOException {
        String id = UUID.randomUUID().toString();
        String key = id.substring(0, 2) + "/" + id + ".pdf";
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        return key;
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public long size(String key) throws IOException {
        return Files.size(resolve(key));
    }

    @Override
    public void copyTo(String key, Path target) throws IOException {
        Files.copy(resolve(key), target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Could not delete stored file {}", key, ex);
        }
    }

    @Override
    public void deleteAfterCommit(Collection<String> keys) {
        List<String> snapshot = List.copyOf(keys);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            snapshot.forEach(this::delete);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                snapshot.forEach(LocalFileStorage.this::delete);
            }
        });
    }

    /** Keys are server-generated, but are still checked so a tampered value can never escape the root. */
    private Path resolve(String key) {
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
