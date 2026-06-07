package com.threeamigos.common.util.implementations.injection.events;

/**
 * Snapshot of contextual identifiers used for provider-assisted restoration.
 */
public class ContextSnapshot {
    public final String requestId; // unused placeholder
    public final String conversationId;
    public final String sessionId;
    public final byte[] sessionData;

    public ContextSnapshot(String requestId, String conversationId, String sessionId, byte[] sessionData) {
        this.requestId = requestId;
        this.conversationId = conversationId;
        this.sessionId = sessionId;
        this.sessionData = sessionData;
    }
}
