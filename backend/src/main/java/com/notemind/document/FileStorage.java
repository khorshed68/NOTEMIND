package com.notemind.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Storage abstraction. Callers only ever see opaque keys, so swapping the local
 * filesystem for S3 or similar means adding one implementation of this interface.
 */
public interface FileStorage {

    /** Moves a validated temporary file into storage and returns its new opaque key. */
    String store(Path source) throws IOException;

    InputStream open(String key) throws IOException;

    long size(String key) throws IOException;

    /** Copies the object to a local path for libraries that need random access. */
    void copyTo(String key, Path target) throws IOException;

    void delete(String key);

    /** Deletes after the surrounding transaction commits, so a rollback can never orphan a database row. */
    void deleteAfterCommit(Collection<String> keys);
}
