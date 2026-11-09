package com.notemind.ai;

/**
 * Stops the internal not-found marker from flashing on screen during streaming: output is held
 * back only while it could still turn out to be that marker.
 */
final class SentinelGate {

    private final StringBuilder held = new StringBuilder();
    private boolean open;

    /** @return text that is safe to forward now, possibly empty */
    String accept(String token) {
        if (open) {
            return token;
        }
        held.append(token);
        String candidate = held.toString().stripLeading();
        if (RagPrompts.NOT_FOUND_SENTINEL.startsWith(candidate) || candidate.startsWith(RagPrompts.NOT_FOUND_SENTINEL)) {
            return "";
        }
        open = true;
        return held.toString();
    }
}
