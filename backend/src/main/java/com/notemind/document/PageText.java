package com.notemind.document;

/** Text of one PDF page. {@code pageNumber} is 1-based, matching what a PDF viewer shows. */
public record PageText(int pageNumber, String text, boolean ocr) {
}
