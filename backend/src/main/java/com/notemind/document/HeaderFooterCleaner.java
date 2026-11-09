package com.notemind.document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Removes running headers and footers such as a course title on every slide.
 * Left in, such repeated lines make unrelated pages look similar to the search and hurt retrieval.
 */
@Component
public class HeaderFooterCleaner {

    private static final int MIN_PAGES = 4;
    private static final int EDGE_LINES = 2;
    private static final int MAX_LINE_LENGTH = 120;
    private static final double REPEAT_RATIO = 0.6;

    public List<PageText> clean(List<PageText> pages) {
        if (pages.size() < MIN_PAGES) {
            return pages;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (PageText page : pages) {
            List<String> lines = lines(page.text());
            edgeIndexes(lines.size()).stream()
                    .map(lines::get)
                    .filter(line -> !line.isBlank() && line.length() <= MAX_LINE_LENGTH)
                    .map(HeaderFooterCleaner::signature)
                    .distinct()
                    .forEach(signature -> counts.merge(signature, 1, Integer::sum));
        }
        int required = Math.max(3, (int) Math.ceil(pages.size() * REPEAT_RATIO));

        List<PageText> cleaned = new ArrayList<>(pages.size());
        for (PageText page : pages) {
            List<String> lines = new ArrayList<>(lines(page.text()));
            List<Integer> edges = edgeIndexes(lines.size());
            for (int i = edges.size() - 1; i >= 0; i--) {
                int index = edges.get(i);
                if (counts.getOrDefault(signature(lines.get(index)), 0) >= required) {
                    lines.remove(index);
                }
            }
            String text = String.join("\n", lines).strip();
            // A page that is only a repeated line, like a title slide, keeps its original text.
            cleaned.add(text.isBlank() ? page : new PageText(page.pageNumber(), text, page.ocr()));
        }
        return cleaned;
    }

    private static List<String> lines(String text) {
        return Arrays.asList(text.split("\n", -1));
    }

    private static List<Integer> edgeIndexes(int size) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (i < EDGE_LINES || i >= size - EDGE_LINES) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    /** Digits are masked so "Page 3" and "Page 4" count as the same footer. */
    private static String signature(String line) {
        return line.strip().toLowerCase(Locale.ROOT).replaceAll("\\d+", "#");
    }
}
