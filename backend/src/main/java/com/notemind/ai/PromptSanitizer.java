package com.notemind.ai;

import java.util.regex.Pattern;

/**
 * Removes text from retrieved passages that addresses the model rather than the student.
 * This is one layer of defence, not a guarantee: phrasing it does not recognise still reaches the
 * model, which is why passages are also fenced and labelled as untrusted data.
 *
 * <p>Only the copy sent to the model is changed. The stored passage stays intact, so highlighting
 * in the PDF viewer still matches the page.
 */
final class PromptSanitizer {

    static final String REMOVED = "[text removed: it addressed the AI rather than the reader]";

    private static final String SEPARATOR = "(?<=[.!?\\u0964])\\s+|\\n";

    private static final Pattern INSTRUCTION = Pattern.compile(
            "(ignore|disregard|forget|override)\\s+(all\\s+|any\\s+|the\\s+|your\\s+)*"
                    + "(previous|prior|above|earlier|system|preceding)?\\s*(instruction|prompt|rule|direction)"
                    + "|^\\s*\\W*(system|assistant|developer)\\s*(prompt|message)?\\s*:"
                    + "|(reveal|show|print|repeat|leak)\\s+(me\\s+)?(your|the)\\s+(system\\s+|internal\\s+|hidden\\s+)*"
                    + "(prompt|instruction|api\\s*key|secret)"
                    + "|\\byour\\s+new\\s+(task|instruction|role|goal)"
                    + "|\\b(note|message|instruction)\\s+(to|for)\\s+(the\\s+|any\\s+)?(ai|llm|model|assistant|chatbot)\\b"
                    + "|\\b(reply|respond|answer)\\s+only\\s+with\\b"
                    + "|\\byou\\s+are\\s+now\\b"
                    + "|\\bbegin\\s+every\\s+(answer|response|reply)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private PromptSanitizer() {
    }

    static String clean(String passage) {
        // Checked sentence by sentence so "SYSTEM:" is caught mid-paragraph as well as at the start of a line.
        StringBuilder out = new StringBuilder();
        boolean removed = false;
        for (String part : passage.split(SEPARATOR)) {
            if (INSTRUCTION.matcher(part).find()) {
                if (!removed) {
                    out.append(REMOVED).append('\n');
                    removed = true;
                }
            } else if (!part.isBlank()) {
                out.append(part.strip()).append('\n');
            }
        }
        return removed ? out.toString().strip() : passage;
    }
}
