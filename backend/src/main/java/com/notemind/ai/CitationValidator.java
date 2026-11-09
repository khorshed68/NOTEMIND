package com.notemind.ai;

import com.notemind.dto.ChatDtos.SourceRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The model only ever cites opaque labels, and each label is checked against what retrieval
 * returned. Document names and page numbers shown to students therefore come from the database,
 * so the model has no way to produce a citation to a page that was not retrieved.
 */
@Component
public class CitationValidator {

    private static final Pattern GROUP = Pattern.compile("\\[(\\s*S\\d+\\s*(?:[,;]\\s*S\\d+\\s*)+)\\]");
    private static final Pattern LABEL = Pattern.compile("\\[S(\\d+)\\]");
    /** Small models sometimes stop before the closing bracket, as in "...packet [S1". */
    private static final Pattern UNCLOSED_LABEL = Pattern.compile("\\[S(\\d+)(?=\\s*$|\\s+|[.,;:])");

    private static final String SENTENCE_END = "(?<=[.!?\\u0964])\\s+";
    private static final int MIN_ATTRIBUTION_WORDS = 3;
    private static final double MIN_ATTRIBUTION_OVERLAP = 0.6;
    private static final int WORD_PREFIX = 6;
    private static final Set<String> STOP_WORDS = Set.of("the", "and", "for", "are", "that", "this", "with", "from",
            "which", "can", "has", "have", "its", "was", "were", "been", "will", "not", "but", "into", "than", "then",
            "also", "such", "these", "those", "their", "when", "where", "what", "how", "you", "your", "according",
            "notes", "lecture", "context", "provided");

    public record Result(String text, List<SourceRef> sources) {
    }

    public Result validate(String answer, List<RetrievedChunk> chunks) {
        String normalized = UNCLOSED_LABEL.matcher(expandGroups(answer)).replaceAll("[S$1]");
        Set<Integer> cited = new LinkedHashSet<>();
        StringBuilder cleaned = new StringBuilder();
        Matcher matcher = LABEL.matcher(normalized);
        while (matcher.find()) {
            int number = parse(matcher.group(1));
            boolean valid = number >= 1 && number <= chunks.size();
            if (valid) {
                cited.add(number);
            }
            matcher.appendReplacement(cleaned, valid ? Matcher.quoteReplacement(matcher.group()) : "");
        }
        matcher.appendTail(cleaned);

        // Some models ignore the label format. Attach sentences to the passages they demonstrably came from.
        if (cited.isEmpty()) {
            cleaned = new StringBuilder(attribute(cleaned.toString(), chunks, cited));
        }

        // Still nothing: show what was retrieved, flagged cited=false so the UI can label it honestly.
        if (cited.isEmpty()) {
            List<SourceRef> related = new ArrayList<>(chunks.size());
            for (int i = 1; i <= chunks.size(); i++) {
                related.add(toSource(i, chunks.get(i - 1), false));
            }
            return new Result(cleaned.toString().strip(), related);
        }
        List<SourceRef> sources = new ArrayList<>(cited.size());
        for (int number : cited) {
            sources.add(toSource(number, chunks.get(number - 1), true));
        }
        return new Result(cleaned.toString().strip(), sources);
    }

    /** Adds a label after each sentence whose content words clearly come from one retrieved passage. */
    private String attribute(String answer, List<RetrievedChunk> chunks, Set<Integer> cited) {
        List<Set<String>> passages = chunks.stream().map(chunk -> contentWords(chunk.content())).toList();
        StringBuilder out = new StringBuilder();
        for (String line : answer.split("\n", -1)) {
            out.append(attributeLine(line, passages, cited)).append('\n');
        }
        return out.toString().strip();
    }

    private String attributeLine(String line, List<Set<String>> passages, Set<Integer> cited) {
        if (line.isBlank() || line.stripLeading().startsWith("#") || line.strip().matches("\\*\\*[^*]+\\*\\*:?")) {
            return line;
        }
        List<String> sentences = new ArrayList<>();
        int lastLabel = -1;
        for (String sentence : line.split(SENTENCE_END)) {
            int best = bestPassage(sentence, passages);
            if (best > 0 && best != lastLabel) {
                cited.add(best);
                lastLabel = best;
                sentences.add(sentence.stripTrailing() + " [S" + best + "]");
            } else {
                sentences.add(sentence);
            }
        }
        return String.join(" ", sentences);
    }

    /** @return 1-based passage number, or -1 when no passage contains enough of the sentence */
    private static int bestPassage(String sentence, List<Set<String>> passages) {
        Set<String> words = contentWords(sentence);
        if (words.size() < MIN_ATTRIBUTION_WORDS) {
            return -1;
        }
        int best = -1;
        double bestScore = MIN_ATTRIBUTION_OVERLAP;
        for (int i = 0; i < passages.size(); i++) {
            Set<String> common = new HashSet<>(words);
            common.retainAll(passages.get(i));
            double score = common.size() / (double) words.size();
            if (score >= bestScore && (best < 0 || score > bestScore)) {
                best = i + 1;
                bestScore = score;
            }
        }
        return best;
    }

    /** Lowercased words without stop words, cut to a prefix so "converts" and "converted" still match. */
    private static Set<String> contentWords(String text) {
        Set<String> words = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                words.add(token.length() > WORD_PREFIX ? token.substring(0, WORD_PREFIX) : token);
            }
        }
        return words;
    }

    public SourceRef toSource(int number, RetrievedChunk chunk, boolean cited) {
        return new SourceRef("S" + number, chunk.documentId(), chunk.documentName(), chunk.pageNumber(),
                chunk.chunkId(), chunk.content(), Math.round(chunk.similarity() * 1000) / 1000d, cited);
    }

    private static String expandGroups(String text) {
        Matcher matcher = GROUP.matcher(text);
        StringBuilder expanded = new StringBuilder();
        while (matcher.find()) {
            StringBuilder labels = new StringBuilder();
            for (String label : matcher.group(1).split("[,;]")) {
                labels.append('[').append(label.strip()).append(']');
            }
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(labels.toString()));
        }
        matcher.appendTail(expanded);
        return expanded.toString();
    }

    private static int parse(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
