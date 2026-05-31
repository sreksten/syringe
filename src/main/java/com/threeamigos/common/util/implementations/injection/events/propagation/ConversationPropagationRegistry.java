package com.threeamigos.common.util.implementations.injection.events.propagation;

import java.util.Map;

/**
 * Lightweight, transport-agnostic registry for propagating conversation identifiers.
 * Uses ThreadLocal storage; callers bind/unbind around message handling or request handling.
 * <p>
 * Designed to avoid compile-time depends on specific transports; integrate by mapping headers/metadata
 * to a conversation id string and calling set/clear.
 */
public final class ConversationPropagationRegistry {

    private static final ThreadLocal<String> CONVERSATION_ID = new ThreadLocal<>();
    public static final String DEFAULT_HEADER = "X-Conversation-Id";

    private ConversationPropagationRegistry() {}

    public static void setConversationId(String id) {
        if (id == null) {
            clear();
        } else {
            CONVERSATION_ID.set(id);
        }
    }

    public static String getConversationId() {
        return CONVERSATION_ID.get();
    }

    public static void clear() {
        CONVERSATION_ID.remove();
    }

    /** Bind from a simple header map (HTTP/JMS/Kafka adapted to Map<String,String>). */
    public static Runnable bindFromHeaders(Map<String, String> headers) {
        return bindFromHeaders(headers, DEFAULT_HEADER);
    }

    public static Runnable bindFromHeaders(Map<String, String> headers, String headerName) {
        String id = headers != null ? headers.get(headerName) : null;
        String previous = CONVERSATION_ID.get();
        if (id != null) {
            CONVERSATION_ID.set(id);
        }
        return () -> {
            if (previous == null) {
                CONVERSATION_ID.remove();
            } else {
                CONVERSATION_ID.set(previous);
            }
        };
    }

    /** Inject into a header map (for outgoing messages). */
    public static void injectToHeaders(Map<String, String> headers) {
        injectToHeaders(headers, DEFAULT_HEADER);
    }

    public static void injectToHeaders(Map<String, String> headers, String headerName) {
        if (headers != null && CONVERSATION_ID.get() != null) {
            headers.put(headerName, CONVERSATION_ID.get());
        }
    }
}
