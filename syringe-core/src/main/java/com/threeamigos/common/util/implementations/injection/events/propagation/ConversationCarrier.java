package com.threeamigos.common.util.implementations.injection.events.propagation;

/**
 * Abstraction for reading/writing conversation identifiers on different transports.
 * Implementations can map the ID to HTTP params/headers, message metadata, etc.
 */
public interface ConversationCarrier {

    /**
     * Extracts an incoming conversation identifier from the transport.
     *
     * @return conversation id or null/empty if not present
     */
    String getConversationId();

    /**
     * Writes the current conversation identifier back to the transport so downstream hops
     * can continue the same conversation.
     *
     * @param conversationId the id to write; implementations should ignore null/empty values
     */
    void setConversationId(String conversationId);

    /**
     * Indicates whether the conversation should be ended when the transport interaction completes.
     * Default: false.
     *
     * @return true if the conversation should be ended
     */
    default boolean shouldEndConversation() {
        return false;
    }
}
