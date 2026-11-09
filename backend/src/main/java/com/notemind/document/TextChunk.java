package com.notemind.document;

/** A retrievable passage. It never crosses a page boundary, so its citation is always exact. */
public record TextChunk(int pageNumber, int chunkIndex, String content) {
}
